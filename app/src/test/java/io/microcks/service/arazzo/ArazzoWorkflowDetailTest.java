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
import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.service.arazzo.view.ArazzoSourceDescription;
import io.microcks.service.arazzo.view.ArazzoSourceLink;
import io.microcks.service.arazzo.view.ArazzoStepDetail;
import io.microcks.service.arazzo.view.ArazzoStepOperation;
import io.microcks.service.arazzo.view.ArazzoWorkflowDetail;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests of reading the steps of an Arazzo workflow, one per acceptance criterion of the story.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoWorkflowDetailTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";

   @Inject
   private ArazzoWorkflowService arazzoWorkflowService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ServiceRepository serviceRepository;

   @Test
   void eachStepShowsItsOperationParametersCriteriaAndOutputs() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), "readThenReprice")
            .orElseThrow();

      assertEquals(List.of(
            List.of("readPastry calls GET /pastry/{name} (GetPastryByName)", "parameter name in path = $inputs.pastry",
                  "parameter Accept in header = application/json",
                  "criterion $statusCode == 200 && $response.body#/status == 'available'",
                  "output name = $response.body#/name", "output price = $response.body#/price"),
            List.of("reprice calls PATCH /pastry/{name} (PatchPastry)",
                  "parameter name in path = $steps.readPastry.outputs.name",
                  "parameter Accept in header = application/json",
                  "body application/json {\"price\":0} with /price = $inputs.newPrice", "criterion $statusCode == 200",
                  "criterion $response.body#/price == $inputs.newPrice", "output price = $response.body#/price")),
            workflow.steps().stream().map(ArazzoWorkflowDetailTest::describe).toList());
   }

   @Test
   void dataPassedBetweenStepsIsMadeVisible() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), "readThenReprice")
            .orElseThrow();

      assertEquals(List.of("readPastry.outputs.name becomes the name path parameter of reprice"),
            workflow.dataPassings().stream()
                  .map(passing -> passing.fromStepId() + ".outputs." + passing.outputName() + " becomes the "
                        + passing.to().target() + " " + passing.to().place() + " parameter of " + passing.to().stepId())
                  .toList());
   }

   @Test
   void inputsAndWorkflowOutputsAreListedWithWhereTheyAreUsed() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), "readThenReprice")
            .orElseThrow();

      assertEquals(List.of("pastry: readPastry path name", "newPrice: reprice body /price, reprice criterion"),
            workflow.inputs().stream()
                  .map(input -> input.name() + ": " + String.join(", ", input.usedBy().stream()
                        .map(use -> use.stepId() + " " + use.place() + (use.target() == null ? "" : " " + use.target()))
                        .toList()))
                  .toList());
      assertEquals("{before=$steps.readPastry.outputs.price, after=$steps.reprice.outputs.price}",
            workflow.outputs().toString());
   }

   /** S5, run dialog: each input is asked for with the type the workflow declares, a number for newPrice. */
   @Test
   void eachInputGivesTheTypeTheRunDialogAsksFor() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), "readThenReprice")
            .orElseThrow();

      assertEquals(List.of("pastry: string", "newPrice: number"),
            workflow.inputs().stream().map(input -> input.name() + ": " + input.type()).toList());
   }

   /**
    * S8, run dialog: each input tells whether a run needs it, because the steps or the outputs use it or the inputs
    * schema requires it; an input the outputs use without the workflow declaring it is asked for too.
    */
   @Test
   void eachInputTellsWhetherARunNeedsIt() throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");
      arazzoDocumentService.importArazzoMetadata(fixture("pastry.arazzo-metadata.yaml"));
      ArazzoDocument document = arazzoDocumentService.importArazzoDocument(fixture("pastry-needed-inputs.arazzo.yaml"));

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), "readAndTell")
            .orElseThrow();

      assertEquals(
            List.of("pastry: required, used 1 time(s)", "note: required, used 0 time(s)",
                  "comment: optional, used 0 time(s)", "label: required, used 0 time(s)"),
            workflow.inputs().stream().map(input -> input.name() + ": " + (input.required() ? "required" : "optional")
                  + ", used " + input.usedBy().size() + " time(s)").toList());
   }

   /** S5: the Run button of the workflow page is enabled when the workflow can run, else gives the S2 reason. */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = { "pastry-scenarios.arazzo.yaml; readThenReprice: runnable []",
         "pastry-delete.arazzo.yaml; readThenReprice: not runnable [Operation DeletePastry not found in API Pastry - "
               + "2.0 2.0.0]" })
   void theWorkflowTellsWhetherItCanRun(String fixture, String status) throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");
      arazzoDocumentService.importArazzoMetadata(fixture("pastry.arazzo-metadata.yaml"));
      ArazzoDocument document = arazzoDocumentService.importArazzoDocument(fixture(fixture));

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), "readThenReprice")
            .orElseThrow();

      assertEquals(status, workflow.status().workflowId() + ": "
            + (workflow.status().runnable() ? "runnable " : "not runnable ") + workflow.status().reasons());
   }

   /** Maintainer decision: a step whose operation is not found shows its operationId alone, with the S2 reason. */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
         "pastry-scenarios.arazzo.yaml; false; false; readPastry calls GetPastryByName: no API name and version for "
               + "this source | reprice calls PatchPastry: no API name and version for this source",
         "pastry-scenarios.arazzo.yaml; false; true; readPastry calls GetPastryByName: API API Pastry - 2.0 2.0.0 not "
               + "found | reprice calls PatchPastry: API API Pastry - 2.0 2.0.0 not found",
         "pastry-delete.arazzo.yaml; true; true; readPastry calls GET /pastry/{name} (GetPastryByName) | reprice calls "
               + "PATCH /pastry/{name} (PatchPastry) | deletePastry calls DeletePastry: Operation DeletePastry not "
               + "found in API Pastry - 2.0 2.0.0" })
   void aStepWhoseOperationIsNotFoundShowsItsOperationIdAloneWithTheReason(String fixture, boolean apiImported,
         boolean overlayImported, String operations) throws Exception {
      if (apiImported) {
         apiIsImported("api-pastry-2.0.openapi.yaml");
      }
      if (overlayImported) {
         arazzoDocumentService.importArazzoMetadata(fixture("pastry.arazzo-metadata.yaml"));
      }
      ArazzoDocument document = arazzoDocumentService.importArazzoDocument(fixture(fixture));

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), "readThenReprice")
            .orElseThrow();

      assertEquals(operations, String.join(" | ",
            workflow.steps().stream().map(step -> step.stepId() + " calls " + describe(step.operation())).toList()));
   }

   /** Maintainer decision: one tab per workflow of the document, each reached by the id of the workflow. */
   @Test
   void eachWorkflowOfTheDocumentIsReachedFromItsTab() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), "listThenReadFirst")
            .orElseThrow();

      assertEquals(document.getId(), workflow.documentId());
      assertEquals("API Pastry scenarios 1.0.0 [readThenReprice, listThenReadFirst]",
            workflow.title() + " " + workflow.version() + " " + workflow.workflowIds());
      assertEquals("listThenReadFirst: Reads the first pastry of the list. (null)",
            workflow.workflowId() + ": " + workflow.summary() + " (" + workflow.description() + ")");
      assertEquals(
            List.of("list calls GET /pastry (GetPastries)", "readFirst calls GET /pastry/{name} (GetPastryByName)"),
            workflow.steps().stream().map(step -> step.stepId() + " calls " + describe(step.operation())).toList());
   }

   /** Mock-up, screen 3: the header gives the Arazzo version, the file and the date of the import. */
   @Test
   void theHeaderGivesTheArazzoVersionTheFileAndTheDateOfTheImport() throws Exception {
      Date before = new Date();
      ArazzoDocument document = arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"),
            "pastry.arazzo.yaml");
      Date after = new Date();

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), "readThenReprice")
            .orElseThrow();

      assertEquals("ARAZZO 1.0.1 from pastry.arazzo.yaml",
            "ARAZZO " + workflow.arazzoVersion() + " from " + workflow.fileName());
      assertTrue(!workflow.importDate().before(before) && !workflow.importDate().after(after),
            () -> workflow.importDate() + " is not between " + before + " and " + after);
   }

   /**
    * Mock-up, screen 3: each source description links to its API with how many of its operations the steps call, or
    * gives the S2 reason why it is not linked.
    */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
         "pastry-scenarios.arazzo.yaml; true; true; pastryApi -> API Pastry - 2.0 2.0.0 (linked): 3 operations, 3 "
               + "used by steps",
         "pastry-delete.arazzo.yaml; true; true; pastryApi -> API Pastry - 2.0 2.0.0 (linked): 3 operations, 2 used "
               + "by steps",
         "pastry-scenarios.arazzo.yaml; false; true; pastryApi -> API Pastry - 2.0 2.0.0: API API Pastry - 2.0 "
               + "2.0.0 not found",
         "pastry-scenarios.arazzo.yaml; false; false; pastryApi: no API name and version for this source" })
   void eachSourceDescriptionGivesItsApiAndTheOperationsStepsCallOrWhyItIsNotLinked(String fixture, boolean apiImported,
         boolean overlayImported, String source) throws Exception {
      if (apiImported) {
         apiIsImported("api-pastry-2.0.openapi.yaml");
      }
      if (overlayImported) {
         arazzoDocumentService.importArazzoMetadata(fixture("pastry.arazzo-metadata.yaml"));
      }
      ArazzoDocument document = arazzoDocumentService.importArazzoDocument(fixture(fixture));
      String serviceId = apiImported ? serviceRepository.findByNameAndVersion("API Pastry - 2.0", "2.0.0").getId()
            : null;

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), "readThenReprice")
            .orElseThrow();

      assertEquals(List.of(source), workflow.sources().stream().map(ArazzoWorkflowDetailTest::describe).toList());
      assertEquals(serviceId, workflow.sources().get(0).serviceId());
   }

   @ParameterizedTest
   @CsvSource({ "false, readThenReprice", "true, unknown" })
   void anUnknownDocumentOrWorkflowHasNoDetail(boolean knownDocument, String workflowId) throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      assertEquals(Optional.empty(),
            arazzoWorkflowService.getWorkflowDetail(knownDocument ? document.getId() : "unknown", workflowId));
   }

   @Test
   void aStoredDocumentThatCannotBeReadHasNoWorkflow() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();
      document.setContent("workflows: [ readThenReprice");
      arazzoDocumentRepository.persistOrUpdate(document);

      assertEquals(Optional.empty(), arazzoWorkflowService.getWorkflowDetail(document.getId(), "readThenReprice"));
   }

   private ArazzoDocument pastryScenariosAreImportedAndLinked() throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");
      arazzoDocumentService.importArazzoMetadata(fixture("pastry.arazzo-metadata.yaml"));
      return arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
   }

   private static List<String> describe(ArazzoStepDetail step) {
      List<String> lines = new ArrayList<>();
      lines.add(step.stepId() + " calls " + describe(step.operation()));
      step.parameters().forEach(parameter -> lines
            .add("parameter " + parameter.name() + " in " + parameter.in() + " = " + parameter.value()));
      if (step.requestBody() != null) {
         lines.add("body " + step.requestBody().contentType() + " " + step.requestBody().payload().replaceAll("\\s", "")
               + " with " + String.join(", ", step.requestBody().replacements().entrySet().stream()
                     .map(replacement -> replacement.getKey() + " = " + replacement.getValue()).toList()));
      }
      step.successCriteria().forEach(criterion -> lines.add("criterion " + criterion.condition()));
      step.outputs().forEach((name, expression) -> lines.add("output " + name + " = " + expression));
      return lines;
   }

   private static String describe(ArazzoSourceDescription source) {
      ArazzoSourceLink link = source.link();
      String api = link.apiName() == null ? "" : " -> " + link.apiName() + " " + link.apiVersion();
      if (source.reason() != null) {
         return link.sourceName() + api + ": " + source.reason();
      }
      return link.sourceName() + api + " (" + (link.linked() ? "linked" : "not linked") + "): " + source.operations()
            + " operations, " + source.usedOperations() + " used by steps";
   }

   private static String describe(ArazzoStepOperation operation) {
      if (operation.reason() != null) {
         return operation.operationId() + ": " + operation.reason();
      }
      return operation.method() + " " + operation.path() + " (" + operation.operationId() + ")";
   }

   private void apiIsImported(String fileName) throws Exception {
      File contract = new File(ArazzoWorkflowDetailTest.class.getResource(FIXTURES + fileName).toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo(fileName, true));
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoWorkflowDetailTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
