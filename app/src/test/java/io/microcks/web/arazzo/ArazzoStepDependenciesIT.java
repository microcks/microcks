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

import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoMetadataRepository;
import io.microcks.testsupport.http.ResponseEntity;
import io.microcks.web.AbstractBaseIT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests of the demo of S30 uploaded as the API developer does: the Pastry tasting document, whose steps
 * declare the steps they depend on, read through the REST endpoint of a workflow.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoStepDependenciesIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoMetadataRepository arazzoMetadataRepository;

   @AfterEach
   void deleteDocumentsAndOverlays() {
      arazzoDocumentRepository.deleteAll();
      arazzoMetadataRepository.deleteAll();
   }

   /**
    * AC-01, AC-03: the uploaded document is imported, and the workflow endpoint gives what each step declares it
    * depends on and the steps it must come after.
    */
   @Test
   void eachStepGivesWhatItDependsOnAndTheStepsItMustComeAfter() throws Exception {
      uploadArtifactFile(FIXTURES + "api-pastry-2.0.openapi.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry-tasting.arazzo.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry-tasting.arazzo-metadata.yaml", false);
      String documentId = arazzoDocumentRepository.findByTitleAndVersion("Pastry tasting", "1.0.0").getId();

      ResponseEntity<String> response = restTemplate
            .getForEntity("/api/arazzo/documents/" + documentId + "/workflows/tasteThenReprice", String.class);
      JsonNode workflow = new ObjectMapper().readTree(response.getBody());

      assertEquals(200, response.getStatusCode().value());
      assertEquals("true", workflow.path("status").path("runnable").asText());
      List<String> steps = new ArrayList<>();
      workflow.path("steps").forEach(step -> steps
            .add(step.path("stepId").asText() + " " + step.path("dependsOn") + " " + step.path("prerequisites")));
      assertEquals(List.of("readMenu [] []", "tasteEclair [\"readMenu\"] [\"readMenu\"]",
            "tasteMillefeuille [\"readMenu\"] [\"readMenu\"]", "tasteEclairXml [\"readMenu\"] [\"readMenu\"]",
            "reprice [] [\"readMenu\",\"tasteEclair\",\"tasteMillefeuille\",\"tasteEclairXml\"]"), steps);
   }
}
