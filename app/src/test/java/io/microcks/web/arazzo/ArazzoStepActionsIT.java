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

import io.microcks.web.AbstractBaseIT;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoSourcesRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.microcks.testsupport.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests of the demo of S17 uploaded as the API developer does: the stateful mock of the Order status API,
 * and the actions of the steps of the Order tracking document, read through the REST endpoint of a workflow.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoStepActionsIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";
   private static final String ORDER_STATUS_MOCKS = "/rest/Order+status/1.0.0/order/";

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoSourcesRepository arazzoSourcesRepository;

   @AfterEach
   void deleteDocumentsAndOverlays() {
      arazzoDocumentRepository.deleteAll();
      arazzoSourcesRepository.deleteAll();
   }

   /** AC-05: the workflow endpoint gives what each step does on success and on failure. */
   @Test
   void eachStepGivesWhatItDoesOnSuccessAndOnFailure() throws Exception {
      String documentId = orderTrackingIsUploadedAndLinked();

      JsonNode pollStatus = workflow(documentId, "waitUntilReady").path("steps").path(0);
      JsonNode checkStatus = workflow(documentId, "readUnlessPreparing").path("steps").path(0);

      assertEquals("[] [{\"name\":\"notReadyYet\",\"type\":\"retry\",\"retryAfter\":1.0,\"retryLimit\":5,"
            + "\"criteria\":[]}]", pollStatus.path("onSuccess") + " " + pollStatus.path("onFailure"));
      assertEquals(
            "[{\"name\":\"stillPreparing\",\"type\":\"end\",\"criteria\":[{\"condition\":"
                  + "\"$response.body#/status == 'preparing'\",\"type\":\"simple\"}]}] "
                  + "[{\"name\":\"unknownOrder\",\"type\":\"end\",\"criteria\":[]}]",
            checkStatus.path("onSuccess") + " " + checkStatus.path("onFailure"));
   }

   /**
    * AC-06: the SCRIPT dispatcher of the status mock keeps its state in the store: order 42 is preparing, preparing,
    * then ready, and starts over; order 7 is always preparing, any other order is ready.
    */
   @Test
   void theStatusMockAnswersPreparingTwiceThenReadyForOrder42() throws Exception {
      orderTrackingIsUploadedAndLinked();

      List<String> statuses = new ArrayList<>();
      for (String orderId : List.of("42", "42", "42", "42", "7", "7", "7", "1")) {
         ResponseEntity<String> response = restTemplate.getForEntity(ORDER_STATUS_MOCKS + orderId + "/status",
               String.class);
         statuses.add(orderId + " " + new ObjectMapper().readTree(response.getBody()).path("status").asText());
      }

      assertEquals(List.of("42 preparing", "42 preparing", "42 ready", "42 preparing", "7 preparing", "7 preparing",
            "7 preparing", "1 ready"), statuses);
   }

   private JsonNode workflow(String documentId, String workflowId) throws Exception {
      ResponseEntity<String> response = restTemplate
            .getForEntity("/api/arazzo/documents/" + documentId + "/workflows/" + workflowId, String.class);
      assertEquals(200, response.getStatusCode().value());
      return new ObjectMapper().readTree(response.getBody());
   }

   private String orderTrackingIsUploadedAndLinked() {
      uploadArtifactFile(FIXTURES + "order-status.openapi.yaml", true);
      uploadArtifactFile(FIXTURES + "order-tracking.arazzo.yaml", true);
      uploadArtifactFile(FIXTURES + "order-tracking.arazzo-sources.yaml", false);
      return arazzoDocumentRepository.findByTitleAndVersion("Order tracking", "1.0.0").getId();
   }
}
