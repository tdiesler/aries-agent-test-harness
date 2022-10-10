package io.nessus.aries.aath;

import org.kohsuke.args4j.Option;

public class AgentOptions {

	public AgentOptions() {}
	
	public AgentOptions(int ctrlPort, String agentName, String seed, String walletName, String walletKey,
			String walletType, String adminEndpoint, String userEndpoint, String wsEndpoint, String genesisUrl,
			String storageType) {
		this.seed = seed;
		this.ctrlPort = ctrlPort;
		this.agentName = agentName;
		this.walletName = walletName;
		this.walletKey = walletKey;
		this.walletType = walletType;
		this.adminEndpoint = adminEndpoint;
		this.userEndpoint = userEndpoint;
		this.wsEndpoint = wsEndpoint;
		this.genesisUrl = genesisUrl;
		this.storageType = storageType;
	}

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
    
    public static class Builder {
    	
        int ctrlPort 			= 9030;
        String agentName 		= "camel.Bob";
        String seed 			= null;
        String walletName 		= "admin";
        String walletKey 		= "adminkey";
        String walletType 		= "indy";
        String adminEndpoint	= "http://localhost:9032";
        String userEndpoint 	= "http://localhost:9031";
        String wsEndpoint 		= "ws://localhost:9032/ws";
        String genesisUrl 		= "http://host.docker.internal:9000/genesis";
        String storageType 		= "indy";
        
        public Builder ctrlPort(int ctrlPort) {
        	this.ctrlPort = ctrlPort;
        	return this;
        }
        
        public Builder agentName(String agentName) {
        	this.agentName = agentName;
        	return this;
        }
        
        public Builder seed(String seed) {
        	this.seed = seed;
        	return this;
        }
        
        public Builder walletName(String walletName) {
        	this.walletName = walletName;
        	return this;
        }
        
        public Builder walletKey(String walletKey) {
        	this.walletKey = walletKey;
        	return this;
        }
        
        public Builder walletType(String ctrlPort) {
        	this.walletType = ctrlPort;
        	return this;
        }
        
        public Builder adminEndpoint(String adminEndpoint) {
        	this.adminEndpoint = adminEndpoint;
        	return this;
        }
        
        public Builder userEndpoint(String userEndpoint) {
        	this.userEndpoint = userEndpoint;
        	return this;
        }
        
        public Builder wsEndpoint(String wsEndpoint) {
        	this.wsEndpoint = wsEndpoint;
        	return this;
        }
        
        public Builder genesisUrl(String genesisUrl) {
        	this.genesisUrl = genesisUrl;
        	return this;
        }
        
        public Builder storageType(String storageType) {
        	this.storageType = storageType;
        	return this;
        }
        
        public AgentOptions build() {
        	return new AgentOptions(ctrlPort, agentName, seed, walletName, walletKey, walletType, adminEndpoint, userEndpoint, wsEndpoint, genesisUrl, storageType);
        }
    }
}