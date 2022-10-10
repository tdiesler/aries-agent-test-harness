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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.hyperledger.aries.api.out_of_band.InvitationMessage;
import org.hyperledger.aries.api.out_of_band.InvitationMessage.InvitationMessageService;
import org.hyperledger.aries.config.GsonConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class InvitationMessageTest extends AbstractTest {
	
    static final Gson gson = GsonConfig.defaultConfig();
    
    @Test
	public void testStringToJson() throws Exception {
    	InputStream instream = InvitationMessageTest.class.getResourceAsStream("/rfc0025/T001-RFC0025-receive-invitation.json");
    	try (Reader reader = new InputStreamReader(instream)) {
    		JsonObject jsonData = gson.fromJson(reader, JsonObject.class).getAsJsonObject("data");
            InvitationMessage<InvitationMessageService> imsg = gson.fromJson(jsonData, InvitationMessage.RFC0067_TYPE);
            log.info("{}", imsg);
            Assertions.assertEquals("did:sov:BzCbsNYhMrjHiqZDTUASHg;spec/out-of-band/1.0/invitation", imsg.getAtType());
    	}
	}
}
