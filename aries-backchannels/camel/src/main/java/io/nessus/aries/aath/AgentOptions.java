package io.nessus.aries.aath;

import org.kohsuke.args4j.Option;

public class AgentOptions {

	@Option(name = "--ctrl-port", required = true, usage = "Aries Test Harness Controller port")
    public int ctrlPort;

    @Option(name = "--agent-name", required = true, usage = "Test Harness Agent name")
    public String agentName;

    @Option(name = "--seed", required = true, usage = "Test Harness Agent name")
    public String seed;

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

    @Option(name = "--ws-endpoint", usage = "Agent websocket endpoint")
    public String wsEndpoint;

    @Option(name = "--genesis-url", usage = "Ledger genesis transactions")
    public String genesisUrl;

    @Option(name = "--storage-type", usage = "Ledger genesis transactions")
    public String storageType;
}