package io.nessus.aries.aath;

import org.kohsuke.args4j.Option;

class AgentOptions {

    @Option(name = "--port", required = true, usage = "Agent backchannel port")
    public int port;

    @Option(name = "--wallet-name", required = true, usage = "Agent wallet name")
    public String walletName;

    @Option(name = "--wallet-key", required = true, usage = "Agent wallet key")
    public String walletKey;

    @Option(name = "--wallet-type", required = true, usage = "Agent wallet type")
    public String walletType;

    @Option(name = "--admin-endpoint", required = true, usage = "Agent admin endpoint")
    public String adminEndpoint;

    @Option(name = "--user-endpoint", required = true, usage = "Agent user endpoint")
    public String userEndpoint;
}