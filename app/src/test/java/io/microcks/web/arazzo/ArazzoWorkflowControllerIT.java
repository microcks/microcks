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
package io.microcks.web;

import io.microcks.testsupport.http.ResponseEntity;
import io.microcks.repository.ArazzoDocumentRepository;
import io.microcks.repository.ArazzoSourcesRepository;
import io.microcks.repository.ServiceRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests of reading the steps of an Arazzo workflow, and the workflows using an API, through their REST
 * endpoints.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoWorkflowControllerIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoSourcesRepository arazzoSourcesRepository;

   @Inject
   private ServiceRepository serviceRepository;

   @AfterEach
   void deleteDocumentsAndOverlays() {
      arazzoDocumentRepository.deleteAll();
      arazzoSourcesRepository.deleteAll();
   }

   @Test
   void theStepsOfAWorkflowAreReadWithTheirOperationsDataFlowInputsAndOutputs() throws Exception {
      String documentId = pastryScenariosAreUploadedAndLinked();

      ResponseEntity<String> response = restTemplate
            .getForEntity("/api/arazzo/documents/" + documentId + "/workflows/readThenReprice", String.class);

      assertEquals(200, response.getStatusCode().value());
      JsonNode workflow = new ObjectMapper().readTree(response.getBody());
      assertEquals("readThenReprice [\"readThenReprice\",\"listThenReadFirst\"]",
            workflow.path("workflowId").asText() + " " + workflow.path("workflowIds"));
      assertEquals("ARAZZO 1.0.1 from pastry-scenarios.arazzo.yaml, imported",
            "ARAZZO " + workflow.path("arazzoVersion").asText() + " from " + workflow.path("fileName").asText()
                  + (workflow.path("importDate").isMissingNode() ? "" : ", imported"));
      assertEquals("API Pastry - 2.0 2.0.0: 3 operations, 3 used by steps",
            workflow.path("sources").path(0).path("link").path("apiName").asText() + " "
                  + workflow.path("sources").path(0).path("link").path("apiVersion").asText() + ": "
                  + workflow.path("sources").path(0).path("operations").asInt() + " operations, "
                  + workflow.path("sources").path(0).path("usedOperations").asInt() + " used by steps");
      assertEquals("GET /pastry/{name} GetPastryByName", operation(workflow.path("steps").path(0)));
      assertEquals("PATCH /pastry/{name} PatchPastry", operation(workflow.path("steps").path(1)));
      assertEquals("{\"fromStepId\":\"readPastry\",\"outputName\":\"name\",\"to\":{\"stepId\":\"reprice\","
            + "\"place\":\"path\",\"target\":\"name\"}}", workflow.path("dataPassings").path(0).toString());
      assertEquals(
            "newPrice [{\"stepId\":\"reprice\",\"place\":\"body\",\"target\":\"/price\"},"
                  + "{\"stepId\":\"reprice\",\"place\":\"criterion\"}]",
            workflow.path("inputs").path(1).path("name").asText() + " "
                  + workflow.path("inputs").path(1).path("usedBy"));
      assertEquals("{\"before\":\"$steps.readPastry.outputs.price\",\"after\":\"$steps.reprice.outputs.price\"}",
            workflow.path("outputs").toString());
   }

   @Test
   void aWorkflowTheDocumentDoesNotHaveIsNotFound() {
      String documentId = pastryScenariosAreUploadedAndLinked();

      ResponseEntity<String> response = restTemplate
            .getForEntity("/api/arazzo/documents/" + documentId + "/workflows/unknown", String.class);

      assertEquals(404, response.getStatusCode().value());
   }

   @Test
   void theWorkflowsUsingAnApiAreReadWithTheNumberOfWorkflowsCallingEachOperation() throws Exception {
      String documentId = pastryScenariosAreUploadedAndLinked();

      ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/arazzo/services/" + serviceId("API Pastry - 2.0", "2.0.0") + "/workflows", String.class);

      assertEquals(200, response.getStatusCode().value());
      JsonNode usage = new ObjectMapper().readTree(response.getBody());
      assertEquals(
            "[{\"id\":\"" + documentId + "\",\"title\":\"API Pastry scenarios\",\"version\":\"1.0.0\","
                  + "\"workflows\":[{\"workflowId\":\"readThenReprice\",\"lastResult\":\"NEVER_RUN\"},"
                  + "{\"workflowId\":\"listThenReadFirst\",\"lastResult\":\"NEVER_RUN\"}]}]",
            usage.path("documents").toString());
      Map<String, Integer> counts = new TreeMap<>();
      usage.path("operationWorkflowCounts").properties()
            .forEach(count -> counts.put(count.getKey(), count.getValue().asInt()));
      assertEquals("{GET /pastry=1, GET /pastry/{name}=2, PATCH /pastry/{name}=1}", counts.toString());
   }

   @Test
   void anApiUsedByNoWorkflowHasNoWorkflowAndNoOperationCount() throws Exception {
      pastryScenariosAreUploadedAndLinked();
      uploadArtifactFile("target/test-classes/io/microcks/artifact/soapui/HelloAPI-soapui-project.xml", true);

      ResponseEntity<String> response = restTemplate
            .getForEntity("/api/arazzo/services/" + serviceId("Hello API Mock", "0.8") + "/workflows", String.class);

      assertEquals(200, response.getStatusCode().value());
      assertEquals("{\"documents\":[],\"operationWorkflowCounts\":{}}", response.getBody());
   }

   private String serviceId(String apiName, String apiVersion) {
      return serviceRepository.findByNameAndVersion(apiName, apiVersion).getId();
   }

   private String pastryScenariosAreUploadedAndLinked() {
      uploadArtifactFile(FIXTURES + "api-pastry-2.0.openapi.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry-scenarios.arazzo.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry.arazzo-sources.yaml", false);
      return arazzoDocumentRepository.findByTitleAndVersion("API Pastry scenarios", "1.0.0").getId();
   }

   private static String operation(JsonNode step) {
      JsonNode operation = step.path("operation");
      return operation.path("method").asText() + " " + operation.path("path").asText() + " "
            + operation.path("operationId").asText();
   }
}
