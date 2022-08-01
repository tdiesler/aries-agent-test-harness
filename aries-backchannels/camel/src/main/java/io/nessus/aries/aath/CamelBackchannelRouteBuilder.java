package io.nessus.aries.aath;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aries.HyperledgerAriesComponent;
import org.apache.http.HttpStatus;
import org.hyperledger.acy_py.generated.model.ConnectionInvitation;
import org.hyperledger.aries.AriesClient;
import org.hyperledger.aries.api.connection.ConnectionReceiveInvitationFilter;
import org.hyperledger.aries.api.connection.ConnectionRecord;
import org.hyperledger.aries.api.connection.ConnectionState;
import org.hyperledger.aries.api.credentials.Credential;
import org.hyperledger.aries.api.issue_credential_v1.CredentialExchangeState;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialExchange;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialStoreRequest;
import org.hyperledger.aries.api.trustping.PingRequest;
import org.hyperledger.aries.config.GsonConfig;
import org.hyperledger.aries.webhook.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.nessus.aries.AgentConfiguration;
import io.nessus.aries.util.AssertState;
import io.nessus.aries.websocket.WebSocketClient;
import io.nessus.aries.websocket.WebSocketListener;
import io.nessus.aries.websocket.WebSocketListener.WebSocketState;
import okhttp3.MediaType;

public class CamelBackchannelRouteBuilder extends RouteBuilder {

    static final Logger log = LoggerFactory.getLogger(CamelBackchannelRouteBuilder.class);
    
    static final MediaType JSON_TYPE = MediaType.get("application/json;charset=utf-8");
    static final Gson gson = GsonConfig.defaultConfig();
    
    private String uri;
    private WebSocketClient wsclient;
    private WebSocketListener wsevents;
    private AgentConfiguration agentConfig;

    public CamelBackchannelRouteBuilder(CamelContext context, AgentOptions opts) throws Exception {
        super(context);
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            log.debug("{}: {}", entry.getKey(), entry.getValue());
        }
        
        // [TODO] WebSocket endpoint uses admin port
        this.agentConfig = AgentConfiguration.builder()
            .adminUrl(opts.adminEndpoint)
            .userUrl(opts.userEndpoint)
            .build();    
        log.info("Agent config: {}", agentConfig);

        uri = "http://0.0.0.0:" + opts.port;
        log.info("Start listening on: {}", uri);
        
        getHyperledgerAriesComponent()
            .setAgentConfiguration(agentConfig);
    }

    public HyperledgerAriesComponent getHyperledgerAriesComponent() {
        CamelContext context = getCamelContext();
        return context.getComponent("hyperledger-aries", HyperledgerAriesComponent.class);
    }
    
    public AriesClient adminClient() {
        return getHyperledgerAriesComponent().adminClient();
    }
    
    @Override
    public void configure() {

        from("undertow:" + uri + "?matchOnUriPrefix=true")
            .log("Request: ${headers.CamelHttpMethod} ${headers.CamelHttpPath} ${body}")
            .choice()

                // Agent Status -----------------------------------------------------------------------------------------------
            
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/status"))
                    .process(commandStatus)
                    .process(sendResponse)
                    
                // Connection -------------------------------------------------------------------------------------------------
                    
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/connection/accept-invitation"))
                    .process(commandConnectionAcceptInvitation)
                    .toD("hyperledger-aries:admin?service=/connections/${header.conn_id}/accept-invitation")
                    .process(sendResponse)
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/connection/receive-invitation"))
                    .process(commandConnectionReceiveInvitation)
                    .to("hyperledger-aries:admin?service=/connections/receive-invitation")
                    .process(sendResponse)
                    
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/connection/send-ping"))
                    .process(commandConnectionSendPing)
                    .toD("hyperledger-aries:admin?service=/connections/${header.conn_id}/send-ping")
                    .process(sendResponse)
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/connection/"))
                    .process(commandConnection)
                    .toD("hyperledger-aries:admin?service=/connections/${header.conn_id}")
                    .process(connectionAfter)
                    .process(sendResponse)
            
                // Issue-Credential -------------------------------------------------------------------------------------------
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/issue-credential/send-request"))
                    .process(commandIssueCredentialSendRequest)
                    .toD("hyperledger-aries:admin?service=/issue-credential/records/${header.cred_ex_id}/send-request")
                    .process(sendResponse)
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/issue-credential/store"))
                    .process(commandIssueCredentialStore)
                    .toD("hyperledger-aries:admin?service=/issue-credential/records/${header.cred_ex_id}/store")
                    .process(credentialExchangeAfter)
                    .process(sendResponse)
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/issue-credential/"))
                    .process(commandIssueCredential)
                    .process(credentialExchangeAfter)
                    .process(sendResponse)
                
                // Credential -------------------------------------------------------------------------------------------------
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/credential/"))
                    .to("hyperledger-aries:admin?service=/credentials")
                    .process(credentialSelect)
                    .process(sendResponse)
                
                // Otherwise --------------------------------------------------------------------------------------------------
                
                .otherwise()
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, () -> HttpStatus.SC_NOT_IMPLEMENTED)
                    .setHeader(Exchange.CONTENT_TYPE, () -> JSON_TYPE.toString())
                    .setBody(notImplementedResponse)
            .end()
            .log("Response: ${headers.CamelHttpResponseCode} ${body}");
    }

    // Utility functions ------------------------------------------------------------------------------------------------------
    
    private void assertHttpMethod(String expected, Exchange ex) {
        AssertState.isEqual(expected, messageHeader.apply(ex, Exchange.HTTP_METHOD));
    }
    
    private Function<Exchange, JsonObject> bodyToJson = ex -> {
        String body = ex.getMessage().getBody(String.class);
        return gson.fromJson(body, JsonObject.class);
    };
    
    private BiFunction<Exchange, String, String> messageHeader = (ex, name) -> {
        return ex.getMessage().getHeader(name, String.class);
    };
    
    private Processor sendResponse = ex -> {
        Integer code = ex.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        if (code == null) {
            ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, code = HttpStatus.SC_OK);
            ex.getMessage().setHeader(Exchange.CONTENT_TYPE, JSON_TYPE.toString());
            Object obj = ex.getMessage().getBody(Object.class);
            ex.getMessage().setBody(gson.toJson(obj));
        }
    };

    // Status -----------------------------------------------------------------------------------------------------------------
    
    // agent/command/status
    //
    private Processor commandStatus = ex -> {
        assertHttpMethod("GET", ex);
        if (wsclient == null || wsevents.getWebSocketState() == WebSocketState.NEW) {
            HyperledgerAriesComponent component = getHyperledgerAriesComponent();
            try {
                wsevents = new WebSocketListener("admin", null, null);
				wsclient = component.adminWebSocketClient(wsevents)
						.startRecording(EventType.CONNECTIONS, EventType.ISSUE_CREDENTIAL);
            } catch (Exception e) {
                log.error("{}", e);
            }
        }
        if (wsevents.getWebSocketState() != WebSocketState.OPEN) {
            ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SC_SERVICE_UNAVAILABLE);
            ex.getMessage().setBody("");
        } else {
            ex.getMessage().setBody(Map.of("status", "active"));
        }
    };
    
    // Connection -------------------------------------------------------------------------------------------------------------
    
    // agent/command/connection/accept-invitation
    //
    private Processor commandConnectionAcceptInvitation = ex -> {
        assertHttpMethod("POST", ex);
        String connectionId = bodyToJson.apply(ex).get("id").getAsString();
        ex.getMessage().setHeader("conn_id", connectionId);
    };
    
    // agent/command/connection/receive-invitation
    //
    private Processor commandConnectionReceiveInvitation = ex -> {
        assertHttpMethod("POST", ex);
        JsonObject jsonData = bodyToJson.apply(ex).getAsJsonObject("data");
        ConnectionInvitation invitation = gson.fromJson(jsonData, ConnectionInvitation.class);
        ConnectionReceiveInvitationFilter receiveInvitationFilter = ConnectionReceiveInvitationFilter.builder()
                .autoAccept(false)
                .build();
        ex.getMessage().setHeader(ConnectionReceiveInvitationFilter.class.getName(), receiveInvitationFilter);
        ex.getMessage().setBody(invitation);
    };
    
    // agent/command/connection/send-ping
    //
    private Processor commandConnectionSendPing = ex -> {
        assertHttpMethod("POST", ex);
        JsonObject jsonBody = bodyToJson.apply(ex);
        String connectionId = jsonBody.get("id").getAsString();
        String comment = jsonBody.getAsJsonObject("data").get("comment").getAsString();
        PingRequest pingRequest = PingRequest.builder().comment(comment).build();
        ex.getMessage().setHeader("conn_id", connectionId);
        ex.getMessage().setBody(pingRequest);
    };
    
    // agent/command/connection/{id}
    //
    private Processor commandConnection = ex -> {
        assertHttpMethod("GET", ex);
        String httpPath = messageHeader.apply(ex, Exchange.HTTP_PATH);
        String connectionId = httpPath.substring("agent/command/connection/".length());
        ex.getMessage().setHeader("conn_id", connectionId);
    };
    
    private Processor connectionAfter = ex -> {
        ConnectionRecord connectionRecord = ex.getMessage().getBody(ConnectionRecord.class);
        if (connectionRecord == null) {
            ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SC_NOT_FOUND);
            ex.getMessage().setBody("");
            return;
        }
        ConnectionState state = connectionRecord.getState();
        Map<String, String> resmap = Map.of (
                "connection", gson.toJson(connectionRecord),
                "connection_id", connectionRecord.getConnectionId(),
                "state", mapState(state));
        ex.getMessage().setBody(resmap);
        log.info("commandConnectionAfter: {}", resmap);
    };
    
    // Issue Credential -------------------------------------------------------------------------------------------------------
    
    // agent/command/issue-credential
    //
    private Processor commandIssueCredential = ex -> {
        assertHttpMethod("GET", ex);
        String httpPath = messageHeader.apply(ex, Exchange.HTTP_PATH);
        String threadId = httpPath.substring("agent/command/issue-credential/".length());
        V1CredentialExchange credex = wsevents.awaitIssueCredentialV1(ce -> 
        		ce.getState() == CredentialExchangeState.OFFER_RECEIVED && 
        		ce.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
            .findAny().get();
        ex.getMessage().setBody(credex);
    };
    
    // agent/command/issue-credential/send-request
    //
    private Processor commandIssueCredentialSendRequest = ex -> {
        assertHttpMethod("POST", ex);
        JsonObject jsonBody = bodyToJson.apply(ex);
        String threadId = jsonBody.get("id").getAsString();
        V1CredentialExchange credex = wsevents.awaitIssueCredentialV1(ce -> 
				ce.getState() == CredentialExchangeState.OFFER_RECEIVED && 
				ce.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
		    .findAny().get();
        ex.getMessage().setHeader("cred_ex_id", credex.getCredentialExchangeId());
    };
    
    // agent/command/issue-credential/store
    //
    // The harness sends: {'data': "{'credential_id': '8b5df2fc-fd2a-48bb-b07e-dcb65d99b821'}", 'id': '8b5df2fc-fd2a-48bb-b07e-dcb65d99b821'}  which is the threadId 
    private Processor commandIssueCredentialStore = ex -> {
        assertHttpMethod("POST", ex);
        JsonObject jsonBody = bodyToJson.apply(ex);
        String threadId = jsonBody.get("id").getAsString();
        V1CredentialExchange credex = wsevents.awaitIssueCredentialV1(ce -> 
				ce.getState() == CredentialExchangeState.CREDENTIAL_RECEIVED && 
				ce.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
		    .findAny().get();
        // [TODO] Is it correct that Bob uses the treadId as the credentialId for the store request? It becomes both, the credentialId and the referent
        ex.getMessage().setHeader("cred_ex_id", credex.getCredentialExchangeId());
        ex.getMessage().setBody(V1CredentialStoreRequest.builder().credentialId(threadId).build());
    };
    
    // agent/command/credential
    //
    private Processor credentialSelect = ex -> {
        assertHttpMethod("GET", ex);
        String httpPath = messageHeader.apply(ex, Exchange.HTTP_PATH);
        String referent = httpPath.substring("agent/command/credential/".length());
        Credential cred = Arrays.asList(ex.getMessage().getBody(Credential[].class)).stream()
            .filter(cr -> cr.getReferent().equals(referent))
            .findAny().orElse(null);
        ex.getMessage().setBody(cred);
    };
    
    @SuppressWarnings("unchecked")
    private Processor credentialExchangeAfter = ex -> {
        V1CredentialExchange credex = ex.getMessage().getBody(V1CredentialExchange.class);
        if (credex == null) {
            ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SC_NOT_FOUND);
            ex.getMessage().setBody("");
            return;
        }
        JsonElement jtree = gson.toJsonTree(credex, V1CredentialExchange.class);
        Map<String, String> resmap = gson.fromJson(jtree, Map.class);
        resmap.put("state", mapState(credex.getState()));
        ex.getMessage().setBody(resmap);
    };
    
    // State mappings ---------------------------------------------------------------------------------------------------------
    
    // These method are used to translate the agent states passes back in the responses of operations into the states the
    // test harness expects. The test harness expects states to be as they are written in the Protocol's RFC.
    //
    // The following is what the tests/rfc expect vs what aca-py communicates
    // https://github.com/hyperledger/aries-agent-test-harness/blob/main/aries-backchannels/acapy/acapy_backchannel.py#L2060

    // Connection Protocol:
    // Tests/RFC         |   Aca-py
    // invited           |   invitation
    // requested         |   request
    // responded         |   response
    // complete          |   active
    private String mapState(ConnectionState state) {
        Map<ConnectionState, String> mapping = Map.of(
            ConnectionState.INVITATION, "invited",
            ConnectionState.REQUEST,    "requested",
            ConnectionState.RESPONSE,   "responded",
            ConnectionState.ACTIVE,     "complete");
        String result = mapping.get(state);
        if (result == null)
            result = state.toString().toLowerCase();
        return result;
    }
    
    // Issue Credential Protocol:
    // Tests/RFC         |   Aca-py
    // proposal-sent     |   proposal_sent
    // proposal-received |   proposal_received
    // offer-sent        |   offer_sent
    // offer_received    |   offer_received
    // request-sent      |   request_sent
    // request-received  |   request_received
    // credential-issued |   issued
    // credential-received | credential_received
    // done              |   credential_acked
    private String mapState(CredentialExchangeState state) {
        Map<CredentialExchangeState, String> mapping = Map.of(
            CredentialExchangeState.CREDENTIAL_ISSUED, "issued",
            CredentialExchangeState.CREDENTIAL_ACKED,  "done");
        String result = mapping.get(state);
        if (result == null)
            result = state.toString().toLowerCase().replace('_', '-');
        return result;
    }
    
    // Not Implemented --------------------------------------------------------------------------------------------------------
    
    private Function<Exchange, String> notImplementedResponse = ex -> {
        String httpPath = ex.getIn().getHeader(Exchange.HTTP_PATH, String.class);
        return gson.toJson(Map.of("error", "NotImplemented: " + httpPath));
    };

}
