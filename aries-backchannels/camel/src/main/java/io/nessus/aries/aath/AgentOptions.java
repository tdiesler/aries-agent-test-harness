package io.nessus.aries.aath;

import org.kohsuke.args4j.Option;

public class AgentOptions {

	@Option(name = "--ctrl-port", usage = "Aries Test Harness Controller port")
    public int ctrlPort;

    @Option(name = "--agent-host", usage = "Aries Cloud Agent host")
    public String agentHost;

	@Option(name = "--agent-admin-port", usage = "Aries Cloud Agent Admin port")
    public int agentAdminPort;

	@Option(name = "--agent-http-port", usage = "Aries Cloud Agent HTTP port")
    public int agentHttpPort;

	@Option(name = "--agent-ws-port", usage = "Aries Cloud Agent WebSocket port")
    public int agentWsPort;

    @Option(name = "--agent-name", usage = "Test Harness Agent name")
    public String agentName;

    @Option(name = "--seed", usage = "Specifies the seed to use for the creation of a public DID for the agent")
    public String seed;

    @Option(name = "--wallet-name", usage = "Agent wallet name")
    public String walletName;

    @Option(name = "--wallet-key", usage = "Agent wallet key")
    public String walletKey;

    @Option(name = "--wallet-type", usage = "Agent wallet type")
    public String walletType;

    @Option(name = "--genesis-url", required = true, usage = "Ledger genesis transactions")
    public String genesisUrl;

    @Option(name = "--storage-type", usage = "Ledger genesis transactions")
    public String storageType;
}