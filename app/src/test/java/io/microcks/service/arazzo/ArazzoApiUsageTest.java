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
package io.microcks.service.arazzo;

import io.microcks.service.ArtifactInfo;
import io.microcks.service.ServiceService;
import io.microcks.domain.arazzo.ArazzoApiUsage;
import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.domain.arazzo.ArazzoUsingDocument;
import io.microcks.repository.ServiceRepository;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Acceptance tests of seeing the workflows that use an API on its page, one per acceptance criterion of the story.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoApiUsageTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String API_USAGE = "api-usage.arazzo.yaml";

   @Inject
   private ArazzoWorkflowService arazzoWorkflowService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ServiceRepository serviceRepository;

   /** AC-01: The API page lists the workflows using it with their last result. */
   @Test
   void theApiPageListsTheWorkflowsUsingItWithTheirLastResult() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();
      orderFulfilmentIsImportedAndLinked();

      ArazzoApiUsage usage = arazzoWorkflowService.getApiUsage(serviceId("API Pastry - 2.0", "2.0.0"));

      assertEquals(List.of("API Pastry scenarios 1.0.0: readThenReprice NEVER_RUN, listThenReadFirst NEVER_RUN"),
            usage.documents().stream().map(ArazzoApiUsageTest::describe).toList());
      assertEquals(document.getId(), usage.documents().get(0).id());
   }

   /** AC-02: Each operation shows how many workflows call it. */
   @Test
   void eachOperationShowsHowManyWorkflowsCallIt() throws Exception {
      pastryScenariosAreImportedAndLinked();
      orderFulfilmentIsImportedAndLinked();

      ArazzoApiUsage usage = arazzoWorkflowService.getApiUsage(serviceId("API Pastry - 2.0", "2.0.0"));

      assertEquals("{GET /pastry=1, GET /pastry/{name}=2, PATCH /pastry/{name}=1}",
            new TreeMap<>(usage.operationWorkflowCounts()).toString());
   }

   /** AC-03: No workflow, no addition. */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = { "Hello API Mock; 0.8; true", "API Pastry - 2.0; 2.0.0; false" })
   void anApiUsedByNoWorkflowGetsNoAddition(String apiName, String apiVersion, boolean overlayImported)
         throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");
      helloApiIsImported();
      if (overlayImported) {
         arazzoDocumentService.importArazzoSources(fixture("pastry.arazzo-sources.yaml"));
      }
      arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));

      ArazzoApiUsage usage = arazzoWorkflowService.getApiUsage(serviceId(apiName, apiVersion));

      assertEquals(List.of(), usage.documents());
      assertEquals(new TreeMap<>(), new TreeMap<>(usage.operationWorkflowCounts()));
   }

   /**
    * Maintainer decisions: a workflow uses an API when one of its steps calls an operation of a source description
    * linked to it; each operation counts the distinct workflows calling it.
    */
   @Test
   void aWorkflowCallingAnOperationTwiceCountsOnceAndOneCallingAnUndefinedOperationStillUsesTheApi() throws Exception {
      pastryScenariosAreImportedAndLinked();
      arazzoDocumentService.importArazzoSources(example(API_USAGE, "pastry-checks-sources"));
      arazzoDocumentService.importArazzoDocument(example(API_USAGE, "pastry-checks"));

      ArazzoApiUsage usage = arazzoWorkflowService.getApiUsage(serviceId("API Pastry - 2.0", "2.0.0"));

      assertEquals(
            List.of("API Pastry scenarios 1.0.0: readThenReprice NEVER_RUN, listThenReadFirst NEVER_RUN",
                  "Pastry checks 1.0.0: readTwice NEVER_RUN, deleteOnly NEVER_RUN"),
            usage.documents().stream().map(ArazzoApiUsageTest::describe).toList());
      assertEquals("{GET /pastry=1, GET /pastry/{name}=3, PATCH /pastry/{name}=1}",
            new TreeMap<>(usage.operationWorkflowCounts()).toString());
   }

   private ArazzoDocument pastryScenariosAreImportedAndLinked() throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");
      arazzoDocumentService.importArazzoSources(fixture("pastry.arazzo-sources.yaml"));
      return arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
   }

   private void orderFulfilmentIsImportedAndLinked() throws Exception {
      apiIsImported("customer-api.openapi.yaml");
      apiIsImported("order-api.openapi.yaml");
      arazzoDocumentService.importArazzoSources(fixture("order-fulfilment.arazzo-sources.yaml"));
      arazzoDocumentService.importArazzoDocument(fixture("order-fulfilment.arazzo.yaml"));
   }

   private String serviceId(String apiName, String apiVersion) {
      return serviceRepository.findByNameAndVersion(apiName, apiVersion).getId();
   }

   private static String describe(ArazzoUsingDocument document) {
      return document.title() + " " + document.version() + ": " + String.join(", ", document.workflows().stream()
            .map(workflow -> workflow.workflowId() + " " + workflow.lastResult()).toList());
   }

   private void apiIsImported(String fileName) throws Exception {
      File contract = new File(ArazzoApiUsageTest.class.getResource(FIXTURES + fileName).toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo(fileName, true));
   }

   private void helloApiIsImported() throws Exception {
      File project = new File(ArazzoApiUsageTest.class
            .getResource("/io/microcks/artifact/soapui/HelloAPI-soapui-project.xml").toURI());
      serviceService.importServiceDefinition(project, null, new ArtifactInfo("HelloAPI-soapui-project.xml", true));
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoApiUsageTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   /** One YAML document per example of a fixture, each announced by a {@code # example: <name> - <what>} comment. */
   private static String example(String fixture, String example) throws IOException {
      String marker = "# example: " + example + " ";
      return Arrays.stream(fixture(fixture).split("\n---\n")).filter(document -> document.startsWith(marker))
            .findFirst().orElseThrow(() -> new IllegalArgumentException(example + " is not in " + fixture));
   }
}
