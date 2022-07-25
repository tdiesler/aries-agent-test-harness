package io.nessus.aries.aath;

import java.util.Map;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aries.HyperledgerAriesComponent;
import org.apache.http.HttpStatus;
import org.hyperledger.acy_py.generated.model.ConnectionInvitation;
import org.hyperledger.aries.api.connection.ConnectionReceiveInvitationFilter;
import org.hyperledger.aries.api.connection.ConnectionRecord;
import org.hyperledger.aries.api.connection.ConnectionState;
import org.hyperledger.aries.config.GsonConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.nessus.aries.AgentConfiguration;
import okhttp3.MediaType;

public class CamelBackchannelRouteBuilder extends RouteBuilder {

    static final Logger log = LoggerFactory.getLogger(CamelBackchannelRouteBuilder.class);
    
    static final MediaType JSON_TYPE = MediaType.get("application/json;charset=utf-8");
    static final Gson gson = GsonConfig.defaultConfig();
    
    private String uri;
    private AgentConfiguration agentConfig;
    
    public CamelBackchannelRouteBuilder(CamelContext context, AgentOptions opts) {
        super(context);
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            log.debug("{}: {}", entry.getKey(), entry.getValue());
        }
        this.uri = "http://0.0.0.0:" + opts.port;
        this.agentConfig = AgentConfiguration.builder()
            .adminUrl(opts.adminEndpoint)
            .userUrl(opts.userEndpoint)
            .build();    
        log.info("Agent config: {}", agentConfig);
        log.info("Start listening on: {}", uri);
        
        context.getComponent("hyperledger-aries", HyperledgerAriesComponent.class)
            .setAgentConfiguration(agentConfig);
    }

    @Override
    public void configure() {

        from("undertow:" + uri + "?matchOnUriPrefix=true")
            .log("Request: ${headers.CamelHttpMethod} ${headers.CamelHttpPath} ${body}")
            .choice()
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/status"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, () -> HttpStatus.SC_OK)
                    .setBody(statusActiveResponse)
                    
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/connection/receive-invitation"))
                    .process(connectionReceiveInvitationBeforeProcessor)
                    .to("direct:connections/receive-invitation")
                    .process(jsonResponseProcessor)
                    
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/connection/accept-invitation"))
                    .process(connectionAcceptInvitationBeforeProcessor)
                    .to("direct:connections/accept-invitation")
                    .process(jsonResponseProcessor)
                
                .when(header(Exchange.HTTP_PATH).startsWith("agent/command/connection/"))
                    .process(connectionBeforeProcessor)
                    .to("direct:connections/get-single")
                    .process(connectionAfterProcessor)
            
                .otherwise()
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, () -> HttpStatus.SC_NOT_IMPLEMENTED)
                    .setHeader(Exchange.CONTENT_TYPE, () -> JSON_TYPE.toString())
                    .setBody(notImplementedResponse)
            .end()
            .log("Response: ${headers.CamelHttpResponseCode} ${body}");
        
        from("direct:connections/receive-invitation")
            .to("hyperledger-aries:admin?service=/connections/receive-invitation");
        
        from("direct:connections/accept-invitation")
            .to("hyperledger-aries:admin?service=/connections/accept-invitation");
        
        from("direct:connections/get-single")
            .to("hyperledger-aries:admin?service=/connections/get-single");
    }

    // Utility functions --------------------------------------------------------
    
    private Function<Exchange, JsonObject> bodyToJson = ex -> {
        String body = ex.getMessage().getBody(String.class);
        return gson.fromJson(body, JsonObject.class);
    };
    
    private Function<Exchange, JsonObject> requestData = ex -> {
        JsonObject json = bodyToJson.apply(ex);
        return json.getAsJsonObject("data");
    };
    
    private Processor jsonResponseProcessor = ex -> {
        JsonObject response = ex.getMessage().getBody(JsonObject.class);
        ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SC_OK);
        ex.getMessage().setHeader(Exchange.CONTENT_TYPE, JSON_TYPE.toString());
        ex.getMessage().setBody(gson.toJson(response));
    };
    
    // Connection -------------------------------------------------------------
    
    // agent/command/connection/receive-invitation
    //
    private Processor connectionReceiveInvitationBeforeProcessor = ex -> {
        ConnectionInvitation invitation = gson.fromJson(requestData.apply(ex), ConnectionInvitation.class);
        ConnectionReceiveInvitationFilter receiveInvitationFilter = ConnectionReceiveInvitationFilter.builder()
                .autoAccept(false)
                .build();
        ex.getMessage().setHeader(ConnectionReceiveInvitationFilter.class.getName(), receiveInvitationFilter);
        ex.getMessage().setBody(invitation);
    };
    
    // agent/command/connection/accept-invitation
    //
    private Processor connectionAcceptInvitationBeforeProcessor = ex -> {
        String connectionId = bodyToJson.apply(ex).get("id").getAsString();
        ex.getMessage().setBody(connectionId);
    };
    
    // agent/command/connection/{id}
    //
    private Processor connectionBeforeProcessor = ex -> {
        String httpPath = ex.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
        String connectionId = httpPath.substring("agent/command/connection/".length());
        ex.getMessage().setBody(connectionId);
    };
    
    // agent/command/connection/{id}
    //
    private Processor connectionAfterProcessor = ex -> {
        ConnectionRecord connectionRecord = ex.getMessage().getBody(ConnectionRecord.class);
        if (connectionRecord == null) {
            ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SC_NOT_FOUND);
            ex.getMessage().setBody("");
            return;
        }
        ConnectionState state = connectionRecord.getState();
        Map<String, String> response = Map.of (
                "connection", gson.toJson(connectionRecord),
                "connection_id", connectionRecord.getConnectionId(),
                "state", mapConnectionState(state));
        ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SC_OK);
        ex.getMessage().setHeader(Exchange.CONTENT_TYPE, JSON_TYPE.toString());
        ex.getMessage().setBody(gson.toJson(response));
    };
    
    // This method is used to translate the agent states passes back in the responses of operations into the states the
    // test harness expects. The test harness expects states to be as they are written in the Protocol's RFC.
    //
    // The following is what the tests/rfc expect vs what aca-py communicates
    //
    // Connection Protocol:
    // Tests/RFC         |   Aca-py
    // invited           |   invitation
    // requested         |   request
    // responded         |   response
    // complete          |   active
    private String mapConnectionState(ConnectionState state) {
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
    
    // Command status ---------------------------------------------------------
    
    private Function<Exchange, String> statusActiveResponse = ex -> {
        return gson.toJson(Map.of("status", "active"));
    };
    
    // Not Implemented --------------------------------------------------------
    
    private Function<Exchange, String> notImplementedResponse = ex -> {
        String httpPath = ex.getIn().getHeader(Exchange.HTTP_PATH, String.class);
        return gson.toJson(Map.of("error", "NotImplemented: " + httpPath));
    };

}
