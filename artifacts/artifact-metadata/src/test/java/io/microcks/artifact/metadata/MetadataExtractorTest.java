/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.microcks.util.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.TriggerInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test case for MetadataExtractor class.
 * @author laurent
 */
class MetadataExtractorTest {

   @Test
   void testDelayStrategyExtraction() throws Exception {
      // Prepare.
      String metadata = """
            {
               "delayStrategy": "random-20"
            }
            """;
      Operation operation = new Operation();
      ObjectMapper mapper = new ObjectMapper();

      // Action.
      MetadataExtractor.completeOperationProperties(operation, mapper.readTree(metadata));

      // Assert.
      assertNotNull(operation.getDefaultDelayStrategy());
      assertEquals("random-20", operation.getDefaultDelayStrategy());
   }

   @Test
   void testFrequencyStrategyExtraction() throws Exception {
      // Prepare.
      String metadata = """
            {
               "frequencyStrategy": "random-20"
            }
            """;
      Operation operation = new Operation();
      ObjectMapper mapper = new ObjectMapper();

      // Action.
      MetadataExtractor.completeOperationProperties(operation, mapper.readTree(metadata));

      // Assert.
      assertNotNull(operation.getDefaultDelayStrategy());
      assertEquals("random-20", operation.getDefaultDelayStrategy());
   }

   @Test
   void testTriggerInfosExtraction() throws Exception {
      // Prepare.
      String metadata = """
            {
               "triggers": [
                  "my-async-service:1.0:SUBSCRIBE /my-channel",
                  "malformed reference"
               ]
            }
            """;
      Operation operation = new Operation();
      ObjectMapper mapper = new ObjectMapper();

      // Action.
      MetadataExtractor.completeOperationProperties(operation, mapper.readTree(metadata));

      // Assert.
      assertNotNull(operation.getTriggerInfos());
      assertEquals(1, operation.getTriggerInfos().size());

      TriggerInfo triggerInfo = operation.getTriggerInfos().getFirst();
      assertEquals("my-async-service", triggerInfo.getServiceName());
      assertEquals("1.0", triggerInfo.getServiceVersion());
      assertEquals("SUBSCRIBE /my-channel", triggerInfo.getOperationName());
   }
}
