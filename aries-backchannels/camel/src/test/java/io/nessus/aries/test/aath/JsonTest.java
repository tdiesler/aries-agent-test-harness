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

import java.util.Map;

import org.hyperledger.aries.config.GsonConfig;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class JsonTest extends AbstractTest {
	
    static final Gson gson = GsonConfig.defaultConfig();
    
    @Test
	public void testStringToJson() {
    	log.info("'' => {}", gson.toJson(""));
    	log.info("'{}' => {}", gson.toJson("{}"));
    	log.info("'obj' => {}", gson.toJson(new JsonObject()));
    	log.info("'map' => {}", gson.toJson(Map.of("status", "active")));
	}
}
