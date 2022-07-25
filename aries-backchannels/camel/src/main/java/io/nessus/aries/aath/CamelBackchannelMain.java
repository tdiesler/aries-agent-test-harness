package io.nessus.aries.aath;

import java.util.concurrent.CountDownLatch;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aries.HyperledgerAriesComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelBackchannelMain {

    static final Logger log = LoggerFactory.getLogger(CamelBackchannelMain.class);

    public void run(String[] args) throws Exception {

        AgentOptions opts = new AgentOptions();
        CmdLineParser parser = new CmdLineParser(opts);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException ex) {
            parser.printUsage(System.err);
            return;
        }

        try (CamelContext camelctx = new DefaultCamelContext()) {

            RouteBuilder routeBuilder = new CamelBackchannelRouteBuilder(camelctx, opts);
            camelctx.addRoutes(routeBuilder);

            // Remove the wallets created by this component on shutdown
            HyperledgerAriesComponent component = getHyperledgerAriesComponent(camelctx);
            component.setRemoveWalletsOnShutdown(true);

            CountDownLatch latch = new CountDownLatch(1);
            camelctx.start();
            latch.await();
        }
    }

    public static void main(String[] args) throws Exception {
        new CamelBackchannelMain().run(args);
    }

    private HyperledgerAriesComponent getHyperledgerAriesComponent(CamelContext camelctx) {
        return camelctx.getComponent("hyperledger-aries", HyperledgerAriesComponent.class);
    }
}
