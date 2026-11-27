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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.domain.arazzo.ArazzoSources;
import io.microcks.domain.arazzo.ArazzoStepExpectation;
import io.microcks.domain.arazzo.ArazzoWorkflowExample;
import io.microcks.domain.ServiceRef;
import io.microcks.repository.arazzo.ArazzoSourcesRepository;
import io.microcks.artifact.MockRepositoryImportException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Acceptance tests of the recorded examples of the workflows of an Arazzo document, given by its ArazzoSources overlay:
 * how they are imported, offered to run, and kept when the sources are linked again.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoWorkflowExamplesTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String EXAMPLES = "examples.arazzo-sources.yaml";

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ArazzoWorkflowService arazzoWorkflowService;

   @Inject
   private ArazzoImportService arazzoImportService;

   @Inject
   private ArazzoSourcesRepository arazzoSourcesRepository;

   @Test
   void theExamplesAreImportedWithTheOverlay() throws Exception {
      arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));

      ArazzoSources imported = arazzoDocumentService.importArazzoSources(exampleOf(EXAMPLES, "recorded"));

      assertEquals(List.of(
            "readThenReprice/eclair-repriced inputs {newPrice=2.6, pastry=Eclair Cafe} outputs {after=2.6, before=2.5}",
            "readThenReprice/millefeuille-repriced inputs {newPrice=4.5, pastry=Millefeuille}",
            "listThenReadFirst/unknown-first-pastry steps [list 200 {first=Baba Rhum}, readFirst 404] stops at readFirst"),
            imported.getExamples().stream().map(ArazzoWorkflowExamplesTest::describe).toList());
      assertEquals(Map.of("pastryApi", "API Pastry - 2.0 2.0.0"), sourcesOf(imported));
   }

   @Test
   void eachWorkflowOffersItsOwnExamplesToRun() throws Exception {
      ArazzoDocument document = arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
      arazzoDocumentService.importArazzoSources(exampleOf(EXAMPLES, "recorded"));

      assertEquals(List.of("eclair-repriced", "millefeuille-repriced"), exampleNames(document, "readThenReprice"));
      assertEquals(List.of("unknown-first-pastry"), exampleNames(document, "listThenReadFirst"));
   }

   @Test
   void aWorkflowWithoutRecordedExampleOffersNone() throws Exception {
      ArazzoDocument document = arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
      arazzoDocumentService.importArazzoSources(exampleOf(EXAMPLES, "no-example"));

      assertEquals(List.of(), exampleNames(document, "readThenReprice"));
   }

   @Test
   void reImportingTheOverlayReplacesItsExamples() throws Exception {
      ArazzoDocument document = arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
      arazzoDocumentService.importArazzoSources(exampleOf(EXAMPLES, "recorded"));

      arazzoDocumentService.importArazzoSources(exampleOf(EXAMPLES, "no-example"));

      assertEquals(List.of(), exampleNames(document, "listThenReadFirst"));
   }

   @Test
   void anExpectedStatusIsAnyExactHttpStatusCode() throws Exception {
      arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));

      ArazzoSources imported = arazzoDocumentService.importArazzoSources(exampleOf(EXAMPLES, "status-bounds"));

      assertEquals(List.of("listThenReadFirst/unknown-first-pastry steps [list 100, readFirst 599]"),
            imported.getExamples().stream().map(ArazzoWorkflowExamplesTest::describe).toList());
   }

   /** An overlay with an example that does not fit its workflow is refused whole: nor its links, nor its examples. */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "unknown-workflow | Invalid ArazzoSources overlay: example eclair-read-twice names the unknown workflow readTwice",
         "unknown-input | Invalid ArazzoSources overlay: example eclair-repriced of readThenReprice gives the unknown input price",
         "unknown-output | Invalid ArazzoSources overlay: example eclair-repriced of readThenReprice expects the unknown output total",
         "unknown-step | Invalid ArazzoSources overlay: example unknown-first-pastry of listThenReadFirst expects the unknown step readSecond",
         "unknown-step-output | Invalid ArazzoSources overlay: example unknown-first-pastry of listThenReadFirst expects the unknown output last of step list",
         "unknown-stop | Invalid ArazzoSources overlay: example unknown-first-pastry of listThenReadFirst stops at the unknown step readSecond",
         "status-range | Invalid ArazzoSources overlay: example unknown-first-pastry of listThenReadFirst expects status 4XX at step readFirst, not an exact HTTP status code",
         "status-default | Invalid ArazzoSources overlay: example unknown-first-pastry of listThenReadFirst expects status default at step readFirst, not an exact HTTP status code",
         "status-below-range | Invalid ArazzoSources overlay: example unknown-first-pastry of listThenReadFirst expects status 99 at step readFirst, not an exact HTTP status code",
         "status-above-range | Invalid ArazzoSources overlay: example unknown-first-pastry of listThenReadFirst expects status 600 at step readFirst, not an exact HTTP status code",
         "no-document | Invalid ArazzoSources overlay: its examples need the Arazzo document Unknown scenarios 1.0.0, which is not imported" })
   void anExampleThatDoesNotFitItsWorkflowRefusesTheWholeOverlay(String example, String reason) throws Exception {
      arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
      String content = exampleOf(EXAMPLES, example);

      MockRepositoryImportException refusal = assertThrows(MockRepositoryImportException.class,
            () -> arazzoDocumentService.importArazzoSources(content));

      assertEquals(reason, refusal.getMessage());
      assertEquals(0, arazzoSourcesRepository.count());
   }

   @Test
   void linkingTheSourcesAgainFromTheImportDialogKeepsTheExamples() throws Exception {
      ArazzoDocument document = arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
      arazzoDocumentService.importArazzoSources(exampleOf(EXAMPLES, "recorded"));

      arazzoImportService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"), "pastry-scenarios.arazzo.yaml",
            Map.of("pastryApi", new ServiceRef(null, "API Pastry - 2.0", "2.0.0")));

      assertEquals(List.of("eclair-repriced", "millefeuille-repriced"), exampleNames(document, "readThenReprice"));
      assertEquals(List.of("unknown-first-pastry"), exampleNames(document, "listThenReadFirst"));
   }

   @Test
   void changingTheLinkOfTheSourcesKeepsTheExamplesAndTheOverlay() throws Exception {
      ArazzoDocument document = arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
      arazzoDocumentService.importArazzoSources(exampleOf(EXAMPLES, "recorded"));
      String overlayId = arazzoSourcesRepository.listAll().get(0).getId();

      arazzoImportService.relinkSources(document.getId(),
            Map.of("pastryApi", new ServiceRef(null, "API Pastry - 2.0", "2.0.0")));

      assertEquals(List.of("eclair-repriced", "millefeuille-repriced"), exampleNames(document, "readThenReprice"));
      assertEquals(List.of("unknown-first-pastry"), exampleNames(document, "listThenReadFirst"));
      assertEquals(List.of(overlayId), arazzoSourcesRepository.listAll().stream().map(ArazzoSources::getId).toList());
   }

   private List<String> exampleNames(ArazzoDocument document, String workflowId) {
      return arazzoWorkflowService.getWorkflowDetail(document.getId(), workflowId).orElseThrow().examples().stream()
            .map(ArazzoWorkflowExample::name).toList();
   }

   private static Map<String, String> sourcesOf(ArazzoSources overlay) {
      return overlay.getSources().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
            source -> source.getValue().getName() + " " + source.getValue().getVersion()));
   }

   /** An example as its workflow, name, then what it gives and expects, each part only when the overlay gives it. */
   static String describe(ArazzoWorkflowExample example) {
      StringBuilder description = new StringBuilder(example.workflowId() + "/" + example.name());
      if (!example.inputs().isEmpty()) {
         description.append(" inputs ").append(sorted(example.inputs()));
      }
      if (!example.outputs().isEmpty()) {
         description.append(" outputs ").append(sorted(example.outputs()));
      }
      if (!example.steps().isEmpty()) {
         description.append(" steps ")
               .append(example.steps().stream().map(ArazzoWorkflowExamplesTest::describe).toList());
      }
      if (example.stopsAt() != null) {
         description.append(" stops at ").append(example.stopsAt());
      }
      return description.toString();
   }

   private static String describe(ArazzoStepExpectation step) {
      return step.stepId() + (step.status() == null ? "" : " " + step.status())
            + (step.outputs().isEmpty() ? "" : " " + sorted(step.outputs()));
   }

   private static Map<String, Object> sorted(Map<String, Object> values) {
      return new TreeMap<>(values);
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoWorkflowExamplesTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   /**
    * @param fileName The fixture of a category, holding one YAML document per example, each announced by a
    *                 {@code # example: <name> - <what>} comment
    * @param example  The name of the example
    * @return The YAML document of that example
    */
   static String exampleOf(String fileName, String example) throws IOException {
      String marker = "# example: " + example + " ";
      return Arrays.stream(fixture(fileName).split("\n---\n")).filter(document -> document.contains(marker)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException(example + " is not in " + fileName));
   }
}
