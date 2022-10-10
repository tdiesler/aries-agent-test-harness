package io.nessus.aries.aath;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aries.HyperledgerAriesComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        
        try (CamelContext camelctx = new DefaultCamelContext()) {

            RouteBuilder routeBuilder = new CamelBackchannelRouteBuilder(camelctx, controller, opts);
            camelctx.addRoutes(routeBuilder);

            // Remove the wallets created by this component on shutdown
            HyperledgerAriesComponent component = getHyperledgerAriesComponent(camelctx);
            component.setRemoveWalletsOnShutdown(true);
            camelctx.start();
            
            // Await ACA-Py process stop
            controller.awaitStop();
        }
    }

    public static void main(String[] args) throws Exception {
        new CamelBackchannelMain().run(args);
    }

    private HyperledgerAriesComponent getHyperledgerAriesComponent(CamelContext camelctx) {
        return camelctx.getComponent("hyperledger-aries", HyperledgerAriesComponent.class);
    }
}
