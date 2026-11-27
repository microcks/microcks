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
package io.microcks.web.arazzo;

import io.microcks.service.arazzo.ArazzoMockUrlAnswer;
import io.microcks.service.arazzo.ArazzoMockUrlService;

import org.junit.jupiter.api.Test;
import jakarta.ws.rs.core.Response;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests of how the Mock URL of a workflow turns what the play use case answers into HTTP.
 * @author SebastienDegodez
 */
class ArazzoMockUrlControllerTest {

   private static final String INPUTS = "{\"pastry\": \"Eclair Cafe\", \"newPrice\": 2.6}";

   private final ArazzoMockUrlService arazzoMockUrlService = mock(ArazzoMockUrlService.class);
   private final ArazzoMockUrlController controller = new ArazzoMockUrlController(arazzoMockUrlService);

   /** A passed run answers exactly the outputs, and tells its run in a header; a + in the title is a space. */
   @Test
   void aPassedRunAnswersExactlyTheOutputsAndTellsItsRun() {
      when(arazzoMockUrlService.play("API Pastry scenarios", "1.0.0", "readThenReprice", INPUTS))
            .thenReturn(new ArazzoMockUrlAnswer(200, Map.of("after", 2.6), "run-1", null, null));

      Response response = controller.play("API+Pastry+scenarios", "1.0.0",
            "readThenReprice", INPUTS);

      assertEquals("200 {after=2.6} run-1", response.getStatus() + " " + response.getEntity() + " "
            + response.getHeaderString(ArazzoMockUrlController.RUN_ID_HEADER));
   }

   /** A failed run answers its status with the run, the failed step and the reason. */
   @Test
   void aFailedRunAnswersItsRunFailedStepAndReason() {
      when(arazzoMockUrlService.play("API Pastry scenarios", "1.0.0", "readThenReprice", INPUTS))
            .thenReturn(new ArazzoMockUrlAnswer(404, null, "run-1", "reprice", "Step reprice failed: …"));

      Response response = controller.play("API Pastry scenarios", "1.0.0",
            "readThenReprice", INPUTS);

      assertEquals("404 {runId=run-1, failedStep=reprice, reason=Step reprice failed: …} run-1",
            response.getStatus() + " " + response.getEntity() + " "
                  + response.getHeaderString(ArazzoMockUrlController.RUN_ID_HEADER));
   }

   /** A refused call, that created no run, answers its reason only. */
   @Test
   void aRefusedCallAnswersItsReasonOnly() {
      when(arazzoMockUrlService.play("API Pastry scenarios", "1.0.0", "deleteAll", INPUTS))
            .thenReturn(new ArazzoMockUrlAnswer(404, null, null, null, "Unknown workflow: deleteAll"));

      Response response = controller.play("API+Pastry+scenarios", "1.0.0", "deleteAll",
            INPUTS);

      assertEquals("404 {reason=Unknown workflow: deleteAll}",
            response.getStatus() + " " + response.getEntity());
      assertNull(response.getHeaderString(ArazzoMockUrlController.RUN_ID_HEADER));
   }
}
