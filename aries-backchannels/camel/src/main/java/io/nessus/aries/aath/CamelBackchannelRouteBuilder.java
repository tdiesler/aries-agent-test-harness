package io.nessus.aries.aath;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
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
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialProposalRequest;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialStoreRequest;
import org.hyperledger.aries.api.out_of_band.InvitationMessage;
import org.hyperledger.aries.api.out_of_band.InvitationMessage.InvitationMessageService;
import org.hyperledger.aries.api.out_of_band.ReceiveInvitationFilter;
import org.hyperledger.aries.api.present_proof.PresentationExchangeRecord;
import org.hyperledger.aries.api.present_proof.PresentationExchangeState;
import org.hyperledger.aries.api.present_proof.PresentationRequest;
import org.hyperledger.aries.api.present_proof.PresentationRequest.IndyRequestedCredsRequestedAttr;
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
import io.nessus.aries.util.ThreadUtils;
import io.nessus.aries.websocket.WebSocketListener;
import io.nessus.aries.websocket.WebSocketListener.WebSocketState;
import okhttp3.MediaType;

public class CamelBackchannelRouteBuilder extends RouteBuilder {

    static final Logger log = LoggerFactory.getLogger(CamelBackchannelRouteBuilder.class);
    
    static final MediaType JSON_TYPE = MediaType.get("application/json;charset=utf-8");
    static final Gson gson = GsonConfig.defaultConfig();
    
    private final EventType[] recordedEventTypes = new EventType[] { 
            EventType.CONNECTIONS, EventType.ISSUE_CREDENTIAL, EventType.PRESENT_PROOF };

    private String uri;
    private AgentConfiguration agentConfig;
    private AgentController agentController;

    public CamelBackchannelRouteBuilder(CamelContext context, AgentController agentController, AgentOptions opts) throws Exception {
        super(context);
        this.agentController = agentController;
        this.uri = "http://0.0.0.0:" + opts.ctrlPort;

        // Debug env vars
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            log.debug("{}: {}", entry.getKey(), entry.getValue());
        }
        
        // Construct the AgentConfiguration from cmd line opts
        this.agentConfig = AgentConfiguration.builder()
            .adminUrl(opts.adminEndpoint)
            .userUrl(opts.userEndpoint)
            .build();    
        log.info("Agent config: {}", agentConfig);
        
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

    private boolean startWebSocket() {
        HyperledgerAriesComponent component = getHyperledgerAriesComponent();
        component.createAdminWebSocketClient().startRecording(recordedEventTypes);

        int count = 0;
        WebSocketListener wsevents = getWebSocketListener();
        WebSocketState status = wsevents.getWebSocketState();
        while (++count < 40 && status != WebSocketState.OPEN) {
            ThreadUtils.sleepWell(500);
            status = wsevents.getWebSocketState();
        }
        return status == WebSocketState.OPEN;
    }

    private void closeWebSocket() {
        HyperledgerAriesComponent component = getHyperledgerAriesComponent();
        component.closeAdminWebSocketClient();
    }

    private WebSocketListener getWebSocketListener() {
        HyperledgerAriesComponent component = getHyperledgerAriesComponent();
        return component.getAdminWebSocketListener();
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
                    
                // Agent Start -----------------------------------------------------------------------------------------------
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/agent/start"))
                    .process(commandAgentStart)
                    .process(sendResponse)
                    
                // Connection -------------------------------------------------------------------------------------------------
                    
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/connection/accept-invitation"))
                    .process(commandConnectionAcceptInvitation)
                    .toD("hyperledger-aries:admin?service=/connections/${header.conn_id}/accept-invitation")
                    .process(sendResponse)
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/connection/receive-invitation"))
                    .process(commandConnectionReceiveInvitation)
                    .toD("hyperledger-aries:admin?service=/connections/receive-invitation")
                    .process(sendResponse)
                    
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/connection/send-ping"))
                    .process(commandConnectionSendPing)
                    .toD("hyperledger-aries:admin?service=/connections/${header.conn_id}/send-ping")
                    .process(sendResponse)
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/connection/"))
                    .process(commandConnection)
                    .toD("hyperledger-aries:admin?service=/connections/${header.conn_id}")
                    .process(connectionRecordAfter)
                    .process(sendResponse)
            
                // Credential -------------------------------------------------------------------------------------------------
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/credential/"))
                    .toD("hyperledger-aries:admin?service=/credentials")
                    .process(credentialSelect)
                    .process(sendResponse)
                
                // DID Exchange -----------------------------------------------------------------------------------------------
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/did-exchange/send-request"))
                    .process(commandDidExchangeSendRequest)
                    .toD("hyperledger-aries:admin?service=/didexchange/${header.conn_id}/accept-invitation")
                    .process(didExchangeAfter)
                    .process(sendResponse)
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/did-exchange/"))
                    .process(commandDidExchange)
                    .toD("hyperledger-aries:admin?service=/didexchange/${header.conn_id}")
                    .process(didExchangeAfter)
                    .process(sendResponse)
                
                // Issue-Credential -------------------------------------------------------------------------------------------
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/issue-credential/send-proposal"))
                    .process(commandIssueCredentialSendProposal)
                    .toD("hyperledger-aries:admin?service=/issue-credential/send-proposal")
                    .process(credentialExchangeAfter)
                    .process(sendResponse)
                
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
                
                // Out-of-Band ------------------------------------------------------------------------------------------------------
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/out-of-band/receive-invitation"))
                    .process(commandOutOfBandReceiveInvitation)
                    .toD("hyperledger-aries:admin?service=/out-of-band/receive-invitation")
                    .process(didExchangeAfter)
                    .process(sendResponse)
                
                /*
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/out-of-band/send-invitation"))
                    .process(commandOutOfBandSendInvitation)
                    //.toD("hyperledger-aries:admin?service=/out-of-band/send-invitation-message")
                    .process(sendResponse)
                */
                
                // Proof ------------------------------------------------------------------------------------------------------
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/proof/send-presentation"))
                    .process(commandProofSendPresentation)
                    .toD("hyperledger-aries:admin?service=/present-proof/records/${header.pres_ex_id}/send-presentation")
                    .process(presentationExchangeAfter)
                    .process(sendResponse)
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/proof/"))
                    .toD("hyperledger-aries:admin?service=/present-proof/records")
                    .process(presentProofRecords)
                    .process(presentationExchangeAfter)
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
    
    private Function<Exchange, String> notImplementedResponse = ex -> {
        String httpPath = ex.getIn().getHeader(Exchange.HTTP_PATH, String.class);
        ex.getMessage().setHeader(Exchange.CONTENT_TYPE, JSON_TYPE.toString());
        return gson.toJson(Map.of("error", "NotImplemented: " + httpPath));
    };

    private Processor sendResponse = ex -> {
        Integer code = ex.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        if (code == null) {
            ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, code = HttpStatus.SC_OK);
        }
        ex.getMessage().setHeader(Exchange.CONTENT_TYPE, JSON_TYPE.toString());
        Object obj = ex.getMessage().getBody(Object.class);
        ex.getMessage().setBody(gson.toJson(obj));
    };

    // Status -----------------------------------------------------------------------------------------------------------------
    
    // agent/command/status
    //
    private Processor commandStatus = ex -> {
        assertHttpMethod("GET", ex);
        WebSocketListener wsevents = getWebSocketListener();
        if (wsevents == null) {
            startWebSocket();
            wsevents = getWebSocketListener();
        }
        WebSocketState status = wsevents.getWebSocketState();
        if (status == WebSocketState.OPEN) {
            ex.getMessage().setBody(Map.of("status", "active"));
        } else {
            ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            ex.getMessage().setBody(Map.of("status", status.toString().toLowerCase()));
        }
    };

    // Agent Start ------------------------------------------------------------------------------------------------------------
    
    // agent/command/agent/start
    //
    private Processor commandAgentStart = ex -> {
        assertHttpMethod("POST", ex);
        closeWebSocket();
        JsonObject jsonData = bodyToJson.apply(ex).getAsJsonObject("data");
        JsonObject jsonParams = jsonData.getAsJsonObject("parameters");
        agentController.restartAgent(jsonParams);
        if (startWebSocket()) {
            ex.getMessage().setBody(jsonParams);
        } else {
            ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            ex.getMessage().setBody(Map.of("error", "Cannot start web socket"));
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
        ex.getMessage().setHeader("ConnectionReceiveInvitationFilter", receiveInvitationFilter);
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
    
    // Credential -------------------------------------------------------------------------------------------------------------
    
    // agent/command/credential
    //
    private Processor credentialSelect = ex -> {
        assertHttpMethod("GET", ex);
        String httpPath = messageHeader.apply(ex, Exchange.HTTP_PATH);
        String referent = httpPath.substring("agent/command/credential/".length());
        Credential cred = Arrays.asList(ex.getMessage().getBody(Credential[].class)).stream()
            .filter(cr -> cr.getReferent().equals(referent))
            .findFirst().orElse(null);
        ex.getMessage().setBody(cred);
    };
    
    // DID Exchange -----------------------------------------------------------------------------------------------------------
    
    // agent/command/did-exchange/send-request
    //
    private Processor commandDidExchangeSendRequest = ex -> {
        assertHttpMethod("POST", ex);
        String connectionId = bodyToJson.apply(ex).get("id").getAsString();
        AssertState.notNull(connectionId, "No connectionId");
        ex.getMessage().setHeader("conn_id", connectionId);
    };
    
    // agent/command/did-exchange/{conn_id}
    //
    private Processor commandDidExchange = ex -> {
        assertHttpMethod("GET", ex);
        String httpPath = messageHeader.apply(ex, Exchange.HTTP_PATH);
        String connectionId = httpPath.substring("agent/command/did-exchange/".length());
        AssertState.notNull(connectionId, "No connectionId");
        ex.getMessage().setHeader("conn_id", connectionId);
    };
    
    private Processor didExchangeAfter = ex -> {
        ConnectionRecord conrec = ex.getMessage().getBody(ConnectionRecord.class);
        @SuppressWarnings("unchecked")
        Map<String, String> resmap = new LinkedHashMap<>(gson.fromJson(gson.toJson(conrec), Map.class));
        resmap.put("state", mapDidExchangeState(conrec.getState()));
        ex.getMessage().setBody(resmap);
    };
    
    // Issue Credential -------------------------------------------------------------------------------------------------------
    
    // agent/command/issue-credential
    //
    private Processor commandIssueCredential = ex -> {
        assertHttpMethod("GET", ex);
        String httpPath = messageHeader.apply(ex, Exchange.HTTP_PATH);
        String threadId = httpPath.substring("agent/command/issue-credential/".length());
        WebSocketListener wsevents = getWebSocketListener();
        V1CredentialExchange credex = wsevents.awaitIssueCredentialV1(ce -> 
                ce.getState() == CredentialExchangeState.OFFER_RECEIVED && 
                ce.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
            .findFirst().get();
        ex.getMessage().setBody(credex);
    };
    
    // agent/command/issue-credential/send-proposal
    //
    private Processor commandIssueCredentialSendProposal = ex -> {
        assertHttpMethod("POST", ex);
        JsonObject jsonData = bodyToJson.apply(ex).getAsJsonObject("data");
        V1CredentialProposalRequest proposalRequest = gson.fromJson(jsonData, V1CredentialProposalRequest.class);
        ex.getMessage().setBody(proposalRequest);
    };
    
    // agent/command/issue-credential/send-request
    //
    private Processor commandIssueCredentialSendRequest = ex -> {
        assertHttpMethod("POST", ex);
        JsonObject jsonBody = bodyToJson.apply(ex);
        String threadId = jsonBody.get("id").getAsString();
        WebSocketListener wsevents = getWebSocketListener();
        V1CredentialExchange credex = wsevents.awaitIssueCredentialV1(ce -> 
                ce.getState() == CredentialExchangeState.OFFER_RECEIVED && 
                ce.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
            .findFirst().get();
        ex.getMessage().setHeader("cred_ex_id", credex.getCredentialExchangeId());
    };
    
    // agent/command/issue-credential/store
    //
    // The harness sends: {'data': "{'credential_id': '8b5df2fc-fd2a-48bb-b07e-dcb65d99b821'}", 'id': '8b5df2fc-fd2a-48bb-b07e-dcb65d99b821'}  which is the threadId 
    private Processor commandIssueCredentialStore = ex -> {
        assertHttpMethod("POST", ex);
        JsonObject jsonBody = bodyToJson.apply(ex);
        String threadId = jsonBody.get("id").getAsString();
        WebSocketListener wsevents = getWebSocketListener();
        V1CredentialExchange credex = wsevents.awaitIssueCredentialV1(ce -> 
                ce.getState() == CredentialExchangeState.CREDENTIAL_RECEIVED && 
                ce.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
            .findFirst().get();
        ex.getMessage().setHeader("cred_ex_id", credex.getCredentialExchangeId());
        ex.getMessage().setBody(V1CredentialStoreRequest.builder().credentialId(threadId).build());
    };
    
    // Out-of-Band ------------------------------------------------------------------------------------------------------
    
    // agent/command/out-of-band/receive-invitation
    //
    private Processor commandOutOfBandReceiveInvitation = ex -> {
        assertHttpMethod("POST", ex);
        JsonObject jsonData = bodyToJson.apply(ex).getAsJsonObject("data");
        InvitationMessage<InvitationMessageService> invitation = gson.fromJson(jsonData, InvitationMessage.RFC0067_TYPE);
        ReceiveInvitationFilter filter = ReceiveInvitationFilter.builder()
                .useExistingConnection(false)
                .autoAccept(false)
                .build();
        ex.getMessage().setHeader("ReceiveInvitationFilter", filter);
        ex.getMessage().setBody(invitation);
    };
    
    // agent/command/out-of-band/send-invitation-message
    /*
    private Processor commandOutOfBandSendInvitation = ex -> {
        assertHttpMethod("POST", ex);
        JsonObject jsonBody = bodyToJson.apply(ex);
        ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        ex.getMessage().setBody(Map.of("error", "send-invitation-message"));
    };
    */
    
    // Proof ------------------------------------------------------------------------------------------------------------------
    
    // agent/command/proof/send-presentation
    //
    private Processor commandProofSendPresentation = ex -> {
        assertHttpMethod("POST", ex);
        JsonObject jsonBody = bodyToJson.apply(ex);
        String threadId = jsonBody.get("id").getAsString();
        WebSocketListener wsevents = getWebSocketListener();
        PresentationExchangeRecord pex = wsevents.awaitPresentProofV1(pe -> 
                pe.getThreadId().equals(threadId), 10, TimeUnit.SECONDS)
            .peek(pe -> log.info("{}", pe))
            .sorted(Collections.reverseOrder((a,b) -> a.getState().ordinal() - b.getState().ordinal()))
            .findFirst().get();
        JsonObject jsonAttrs = jsonBody.getAsJsonObject("data").getAsJsonObject("requested_attributes");
        Map<String, IndyRequestedCredsRequestedAttr> requestedAttributes = new HashMap<>();
        for (Entry<String, JsonElement> entry : jsonAttrs.entrySet()) {
            IndyRequestedCredsRequestedAttr value = gson.fromJson(entry.getValue(), IndyRequestedCredsRequestedAttr.class);
            requestedAttributes.put(entry.getKey(), value);
        }
        PresentationRequest presentationRequest = PresentationRequest.builder()
                .requestedAttributes(requestedAttributes)
                .build();
        String pres_ex_id = pex.getPresentationExchangeId();
        ex.getMessage().setHeader("pres_ex_id", pres_ex_id);
        ex.getMessage().setBody(presentationRequest);
    };
    
    // agent/command/proof
    //
    private Processor presentProofRecords = ex -> {
        assertHttpMethod("GET", ex);
        String httpPath = messageHeader.apply(ex, Exchange.HTTP_PATH);
        String threadId = httpPath.substring("agent/command/proof/".length());
        PresentationExchangeRecord pex = Arrays.asList(ex.getMessage().getBody(PresentationExchangeRecord[].class)).stream()
            .filter(pe -> pe.getThreadId().equals(threadId))
            .findFirst().orElse(null);
        ex.getMessage().setBody(pex);
    };
    
    // State mappings and Other Hacks -----------------------------------------------------------------------------------------
    
    // These method are used to translate the agent states passes back in the responses of operations into the states the
    // test harness expects. The test harness expects states to be as they are written in the Protocol's RFC.
    //
    // The following is what the tests/rfc expect vs what aca-py communicates
    // https://github.com/hyperledger/aries-agent-test-harness/blob/main/aries-backchannels/acapy/acapy_backchannel.py#L2060

    private Processor connectionRecordAfter = ex -> {
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
                "state", mapConnectionState(state));
        ex.getMessage().setBody(resmap);
    };
    
    // Connection Protocol:
    // Aca-py         | Tests/RFC   
    // invitation     | invited
    // request        | requested
    // response       | responded
    // active         | complete
    // https://github.com/hyperledger/aries-agent-test-harness/blob/main/aries-backchannels/acapy/acapy_backchannel.py#L105
    private String mapConnectionState(ConnectionState state) {
        Map<ConnectionState, String> mapping = Map.of(
            ConnectionState.INVITATION, "invited",
            ConnectionState.REQUEST,    "requested",
            ConnectionState.RESPONSE,   "responded",
            ConnectionState.ACTIVE,     "complete");
        String result = mapping.get(state);
        if (result == null)
            result = state.toString().toLowerCase();
        log.warn("ConnectionState HACK: {} => {}", state, result);
        return result;
    }
    
    // DID Exchange Requester:
    // Aca-py       | Tests/RFC
    // initial      | invitation-sent
    // invitation   | invitation-received
    // request      | request-sent
    // response     | response-received
    // active       | completed
    // https://github.com/hyperledger/aries-agent-test-harness/blob/main/aries-backchannels/acapy/acapy_backchannel.py#L183
    private String mapDidExchangeState(ConnectionState state) {
        Map<ConnectionState, String> mapping = Map.of(
            ConnectionState.INIT,         "invitation-sent",
            ConnectionState.INVITATION, "invitation-received",
            ConnectionState.REQUEST,    "request-sent",
            ConnectionState.RESPONSE,   "response-received",
            ConnectionState.ACTIVE,     "completed");
        String result = mapping.get(state);
        if (result == null)
            result = state.toString().toLowerCase();
        log.warn("ConnectionState HACK: {} => {}", state, result);
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private Processor presentationExchangeAfter = ex -> {
        PresentationExchangeRecord pex = ex.getMessage().getBody(PresentationExchangeRecord.class);
        if (pex == null) {
            ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SC_NOT_FOUND);
            ex.getMessage().setBody("");
            return;
        }
        JsonElement jtree = gson.toJsonTree(pex, PresentationExchangeRecord.class);
        Map<String, String> resmap = gson.fromJson(jtree, Map.class);
        resmap.put("state", mapPresentationExchangeState(pex.getState()));
        ex.getMessage().setBody(resmap);
    };
    
    // Present Proof Protocol:
    // Aca-py                 | Tests/RFC
    // request_sent           | request-sent 
    // request_received       | request-received
    // proposal_sent          | proposal-sent
    // proposal_received      | proposal-received
    // presentation_sent      | presentation-sent
    // presentation_received  | presentation-received
    // reject_sent            | reject-sent
    // verified               | done
    // presentation_acked     | done
    // https://github.com/hyperledger/aries-agent-test-harness/blob/main/aries-backchannels/acapy/acapy_backchannel.py#L159
    private String mapPresentationExchangeState(PresentationExchangeState state) {
        Map<PresentationExchangeState, String> mapping = Map.of(
            PresentationExchangeState.VERIFIED, "done",
            PresentationExchangeState.PRESENTATION_ACKED,  "done");
        String result = mapping.get(state);
        if (result == null)
            result = state.toString().toLowerCase().replace('_', '-');
        log.warn("PresentationExchangeState HACK: {} => {}", state, result);
        return result;
    }
    
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
        resmap.put("state", mapCredentialExchangeState(credex.getState()));
        ex.getMessage().setBody(resmap);
    };
    
    // Issue Credential Protocol:
    // Aca-py               | Tests/RFC
    // proposal_sent        | proposal-sent
    // proposal_received    | proposal-received
    // offer_sent           | offer-sent
    // offer_received       | offer_received
    // request_sent         | request-sent
    // request_received     | request-received
    // issued               | credential-issued
    // credential_received  | credential-received
    // credential_acked     | done
    // https://github.com/hyperledger/aries-agent-test-harness/blob/main/aries-backchannels/acapy/acapy_backchannel.py#L113
    private String mapCredentialExchangeState(CredentialExchangeState state) {
        Map<CredentialExchangeState, String> mapping = Map.of(
            CredentialExchangeState.CREDENTIAL_ISSUED, "issued",
            CredentialExchangeState.CREDENTIAL_ACKED,  "done");
        String result = mapping.get(state);
        if (result == null)
            result = state.toString().toLowerCase().replace('_', '-');
        log.warn("CredentialExchangeState HACK: {} => {}", state, result);
        return result;
    }
}
