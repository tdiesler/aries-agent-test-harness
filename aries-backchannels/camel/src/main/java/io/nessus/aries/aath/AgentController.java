package io.nessus.aries.aath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.hyperledger.aries.config.GsonConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.nessus.aries.util.AssertState;
import io.nessus.aries.util.ThreadUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AgentController {

    static final Logger log = LoggerFactory.getLogger(AgentController.class);
    
    static final Gson gson = GsonConfig.defaultConfig();
    
    private final AgentOptions opts;
    private CountDownLatch shutdownLatch;
    private List<String> endpoints;
    private boolean restarting;
    private Process process;
	private URL adminURL;
	private URL userURL;
	
	public AgentController(AgentOptions opts) throws IOException {
		this.opts = opts;
        this.adminURL = new URL(opts.adminEndpoint);
        this.userURL = new URL(opts.userEndpoint);
	}

	public List<String> getEndpoints() {
		return Collections.unmodifiableList(endpoints);
	}

	public String getPreferredEndpoint() {
		String result = null;
		if (endpoints != null && endpoints.size() > 0) 
			result = endpoints.get(0);
		log.info("Preferred endpoint: {}", result);
		return result;
	}

	public URL getAdminURL() {
		return adminURL;
	}

	public URL getUserURL() {
		return userURL;
	}

	public void startAgent() throws IOException {
		startAgent(new JsonObject());
	}
	
	public void startAgent(JsonObject params) throws IOException {
		AssertState.isNull(process, "Proccess already running");
		
        String logLevel = getenv("LOG_LEVEL", "info");
        
        BiConsumer<String, List<String>> transportsAppender = (direction, command) -> {
            List<String> transports = new ArrayList<>(List.of("http"));
            String paramName = direction + "_transports";
			if (params.get(paramName) != null) {
            	transports.clear();
            	params.get(paramName).getAsJsonArray()
            		.forEach(el -> transports.add(el.getAsString()));
            }
			if (direction.equals("inbound")) {
				for (String protocol : transports) {
					String endpoint = String.format("%s://%s:%d", protocol, userURL.getHost(), userURL.getPort());
					command.addAll(List.of("--endpoint", endpoint));
					endpoints.add(endpoint);
				}
				for (String protocol : transports) {
					command.addAll(List.of("--inbound-transport", protocol, "0.0.0.0", "" + userURL.getPort()));
				}
			}
			if (direction.equals("outbound")) {
				for (String protocol : transports) {
					command.addAll(List.of("--outbound-transport", protocol));
				}
			}
        };
        
        List<String> command = new ArrayList<>(List.of("/usr/local/bin/python3", "-m", "aries_cloudagent", "start", 
    			"--label", opts.agentName));
        
        endpoints = new ArrayList<>();
        transportsAppender.accept("inbound", command);
        transportsAppender.accept("outbound", command);
        
		command.addAll(List.of(
				"--seed", opts.seed,
        		"--admin", "0.0.0.0", "" + adminURL.getPort(), 
        		"--admin-insecure-mode",
        		"--genesis-url", opts.genesisUrl,
        		"--storage-type", opts.storageType,
        		"--wallet-name", opts.walletName,
        		"--wallet-key", opts.walletKey,
        		"--wallet-type", opts.walletType,
        		"--auto-accept-requests",
        		"--auto-provision",
        		"--enable-undelivered-queue",
        		"--monitor-revocation-notification",
        		"--public-invites",
        		"--recreate-wallet",
        		"--log-level", logLevel));
        
		log.info("Starting agent with: {}", String.join(" ", command));
		
		shutdownLatch = new CountDownLatch(1);
		CountDownLatch startupLatch = new CountDownLatch(1);
    	new Thread(() -> {
            try {
				process = new ProcessBuilder(command).redirectErrorStream(true).start();
				log.info("Agent PID: {}", process.pid());
				try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
					String line = br.readLine();
					startupLatch.countDown();
					while (line != null) {
						log.info("[acapy] {}", line);
						line = br.readLine();
					}
				}
            	log.info("Agent stopped normally");
			} catch (IOException ex) {
				// ignore
			} catch (Exception ex) {
				log.error("Error running agent", ex);
			} finally {
            	if (restarting) {
                	log.info("Agent process stopped for restart");
            	} else {
                	log.info("Agent process stopped");
            		shutdownLatch.countDown();
            	}
			}
    	}).start();
    	try {
			startupLatch.await();
		} catch (InterruptedException ex) {
			// ignore
		}
	}
	
	public void restartAgent(JsonObject params) throws IOException {
		restarting = true;
		try {
			stopAgent();
			startAgent(params);
	        awaitLiveness(20, TimeUnit.SECONDS);
	        awaitReadiness(20, TimeUnit.SECONDS);
		} finally {
			restarting = false;
		}
	}
	
	public boolean stopAgent() {
		AssertState.notNull(process, "No proccess");
		log.info("Shutting down agent process ...");
		process.descendants().forEach(p -> log.info("Agent Descendant: {}", p.info()));
		process.destroy();
		try {
			int exitValue = process.waitFor();
			log.info("Agent process exit({})", exitValue);
		} catch (InterruptedException ex) {
			log.error("Could not shut down agent process: {}", ex);
		}
		// The above does not necessarily free the open ports ... we wait
		int waitCount = 0;
		while (isAlive()) {
			if (waitCount++ % 5 == 0) {
				log.info("Agent process still alive, we wait ...");
				process.descendants().forEach(p -> log.info("Agent Descendant: {}", p.info()));
			}
			ThreadUtils.sleepWell(500);
		}
		log.info("Agent destroyed");
		process = null;
		return true;
	}
	
	public void awaitStop() {
        try {
        	shutdownLatch.await();
		} catch (InterruptedException e) {
			// ignore
		}
	}
	
	public boolean awaitStop(long timeout, TimeUnit unit) {
        try {
        	return shutdownLatch.await(timeout, unit);
		} catch (InterruptedException e) {
			return false;
		}
	}
	
	public boolean awaitLiveness(long timeout, TimeUnit unit) {
		log.info("Awaiting agent liveness ...");
		long startTime = System.currentTimeMillis();
		long endTime = startTime + unit.toMillis(timeout);
		long now = startTime;
		boolean success = false;
		while (now < endTime && !success) {
			if (!(success = isAlive()))
				ThreadUtils.sleepWell(500);
			now = System.currentTimeMillis();
		}
		if (success) {
			log.info(String.format("Agent alive after %dms", now - startTime));
			return true;
		} else {
	    	log.warn(String.format("Agent not alive after %d %s", timeout, unit));
		}
		return success;
	}
	
	public boolean awaitReadiness(long timeout, TimeUnit unit) {
		log.info("Awaiting agent readiness ...");
		long startTime = System.currentTimeMillis();
		long endTime = startTime + unit.toMillis(timeout);
		long now = startTime;
		boolean success = false;
		while (now < endTime && !success) {
			if (!(success = isReady()))
				ThreadUtils.sleepWell(500);
			now = System.currentTimeMillis();
		}
		if (success) {
			log.info(String.format("Agent ready after %dms", now - startTime));
			return true;
		} else {
	    	log.warn(String.format("Agent not ready after %d %s", timeout, unit));
		}
		return success;
	}
	
	public boolean isAlive() {
		OkHttpClient client = new OkHttpClient();
		Request request = new Request.Builder()
	      .url(opts.adminEndpoint + "/status/live")
	      .build();		
		try {
			Response response = client.newCall(request).execute();
			return response.code() == 200;
		} catch (IOException ex) {
			return false;
		}
	}
	
	public boolean isReady() {
		OkHttpClient client = new OkHttpClient();
		Request request = new Request.Builder()
	      .url(opts.adminEndpoint + "/status/ready")
	      .build();		
		try {
			Response response = client.newCall(request).execute();
			return response.code() == 200;
		} catch (IOException ex) {
			return false;
		}
	}
	
    private String getenv(String key, String altval) {
    	String val = System.getenv(key);
    	return val != null ? val : altval;
    }
}