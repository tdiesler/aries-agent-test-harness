package io.nessus.aries.aath;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.component.aries.HyperledgerAriesComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nessus.aries.AgentConfiguration;

public class CamelBackchannelMain {

    static final Logger log = LoggerFactory.getLogger(CamelBackchannelMain.class);
    
    public void run(String[] args) throws Exception {

    	log.info("Java process started with: {}", Arrays.asList(args));
    	
        AgentOptions opts = new AgentOptions();
        CmdLineParser parser = new CmdLineParser(opts);
        parser.parseArgument(args);

        AgentController controller = new AgentController(opts);
        controller.startAgent();
        controller.awaitLiveness(20, TimeUnit.SECONDS);
        controller.awaitReadiness(20, TimeUnit.SECONDS);
        
        // Debug env vars
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            log.debug("{}: {}", entry.getKey(), entry.getValue());
        }
        
        try (CamelContext camelctx = new DefaultCamelContext()) {

            String agentAdminEndpoint = String.format("http://%s:%d", opts.agentHost, opts.agentAdminPort);
            String agentHttpEndpoint = String.format("http://%s:%d", opts.agentHost, opts.agentHttpPort);
            
            // Construct the AgentConfiguration from cmd line opts
            AgentConfiguration agentConfig = AgentConfiguration.builder()
                .adminUrl(agentAdminEndpoint)
                .userUrl(agentHttpEndpoint)
                .build();    
            log.info("Agent config: {}", agentConfig);
            
            HyperledgerAriesComponent component = getHyperledgerAriesComponent(camelctx);
            component.setAgentConfiguration(agentConfig);
            component.setRemoveWalletsOnShutdown(true);

            camelctx.addRoutes(new CamelBackchannelRouteBuilder(camelctx, controller, opts));

            // Start the CamelContext
            camelctx.start();
            
            // Await ACA-Py process stop
            controller.awaitStop();
        }
    }

    HyperledgerAriesComponent getHyperledgerAriesComponent(CamelContext camelctx) {
        return camelctx.getComponent("hyperledger-aries", HyperledgerAriesComponent.class);
    }

    public static void main(String[] args) throws Exception {
        new CamelBackchannelMain().run(args);
    }
}
