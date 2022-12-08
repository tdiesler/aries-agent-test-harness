/*-
 * #%L
 * Nessus Aries :: Common
 * %%
 * Copyright (C) 2022 Nessus
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package io.nessus.aries.test.aath;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hyperledger.aries.config.GsonConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.CmdLineParser;

import com.google.gson.Gson;

import io.nessus.aries.aath.AgentController;
import io.nessus.aries.aath.AgentOptions;

@Disabled
public class AgentControllerTest extends AbstractTest {
	
    static final Gson gson = GsonConfig.defaultConfig();
    
    @Test
	public void testAgentRestart() throws Exception {
    	
        AgentOptions opts = new AgentOptions();
        CmdLineParser parser = new CmdLineParser(opts);
        parser.parseArgument(List.of(
			"--ctrl-port", "9030",
			"--agent-name", "camel.Bob",
			"--wallet-name", "admin",
			"--wallet-key", "adminkey",
			"--wallet-type", "indy",
			"--admin-endpoint", "http://localhost:9032",
			"--user-endpoint", "http://localhost:9031",
			"--ws-endpoint", "ws://localhost:9032/ws",
			"--genesis-url", "http://host.docker.internal:9000/genesis",
			"--storage-type", "indy"
		));
    	AgentController ctrl = new AgentController(opts);
    	
    	// Runtime.getRuntime().exec("/usr/local/bin/docker rm -f acapy");
    	
    	// Start the AcaPy
    	ctrl.startAgent();
		Assertions.assertTrue(ctrl.awaitLiveness(30, TimeUnit.SECONDS));
		Assertions.assertTrue(ctrl.awaitReadiness(30, TimeUnit.SECONDS));

		ctrl.stopAgent();
		Assertions.assertTrue(ctrl.awaitStop(10, TimeUnit.SECONDS));
	}
}
