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
import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;
import io.microcks.domain.arazzo.WorkflowCriterionResult;
import io.microcks.domain.arazzo.WorkflowExpressionValue;
import io.microcks.domain.arazzo.WorkflowResult;
import io.microcks.domain.arazzo.WorkflowRunStatus;
import io.microcks.domain.arazzo.WorkflowStepOperation;
import io.microcks.domain.arazzo.WorkflowStepResult;
import io.microcks.domain.arazzo.WorkflowStepStatus;
import io.microcks.repository.ResourceRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.repository.arazzo.WorkflowResultRepository;
import io.microcks.web.arazzo.ArazzoRunController;
import io.microcks.web.arazzo.dto.ArazzoRunLaunchDTO;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Acceptance tests of running an Arazzo workflow with its step responses validated against their OpenAPI schema,
 * entering through the run use case, with the Arazzo minion standing at its boundary.
 * @author SebastienDegodez
 */
@QuarkusTest
@TestProfile(ArazzoRunSchemaValidationTest.ResourcesAt.class)
class ArazzoRunSchemaValidationTest {

   static final String RESOURCES = "http://microcks.example:8080/api/resources/";
   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final Map<String, Object> INPUTS = Map.of("pastry", "Eclair Cafe", "newPrice", 2.6);

   /** Where the relative references of the stored contracts resolve against, as configured. */
   public static class ResourcesAt implements QuarkusTestProfile {

      @Override
      public Map<String, String> getConfigOverrides() {
         return Map.of("validation.resourceUrl", RESOURCES);
      }
   }

   @Inject
   private ArazzoRunService arazzoRunService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ServiceRepository serviceRepository;

   @Inject
   private ResourceRepository resourceRepository;

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   private final RecordingMinionClient minion = new RecordingMinionClient();

   /** The minion stays at the boundary: runs are recorded, not executed. */
   @BeforeEach
   void recordTheRunsHandedToTheMinion() {
      QuarkusMock.installMockForType(minion, ArazzoMinionClient.class);
   }

   @ParameterizedTest
   @CsvSource({ "true, api-pastry-2.0.openapi.yaml", "false, " })
   void theMinionGetsTheOpenApiContractOfEachLinkedApiOnlyWhenTheResponsesAreValidated(boolean validateSchema,
         String contract) throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      WorkflowResult run = arazzoRunService
            .launchRun(document.getId(), "readThenReprice", INPUTS, 10000L, validateSchema).orElseThrow();

      ArazzoRunRequest request = minion.requests().get(0);
      assertEquals(validateSchema, request.validateSchema());
      assertEquals(contract == null ? null : fixture(contract), request.sources().get("pastryApi").openApiContract());
      assertEquals(contract == null ? null : RESOURCES, request.sources().get("pastryApi").openApiContractBaseUrl());
      assertEquals(validateSchema,
            workflowResultRepository.findByIdOptional(run.getId()).orElseThrow().isValidateSchema());
   }

   @ParameterizedTest
   @CsvSource({ "true", "false" })
   void theMinionGetsTheMediaTypesTheContractDeclaresForTheRequestBodyOfEachOperation(boolean validateSchema)
         throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      arazzoRunService.launchRun(document.getId(), "readThenReprice", INPUTS, 10000L, validateSchema).orElseThrow();

      Map<String, ArazzoRunRequest.Operation> operations = minion.requests().get(0).sources().get("pastryApi")
            .operations();
      assertEquals(List.of("application/json", "text/xml"), operations.get("PatchPastry").requestMediaTypes());
      assertEquals(List.of(), operations.get("GetPastryByName").requestMediaTypes());
   }

   @Test
   void aRunLaunchedWithoutTheOptionDoesNotValidateTheResponses() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      WorkflowResult run = arazzoRunService.launchRun(document.getId(), "readThenReprice", INPUTS, 10000L)
            .orElseThrow();

      assertFalse(minion.requests().get(0).validateSchema());
      assertFalse(run.isValidateSchema());
   }

   @Test
   void theOptionOfTheRunDialogReachesTheMinion() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      new ArazzoRunController(arazzoRunService).launchRun(document.getId(), "readThenReprice",
            new ArazzoRunLaunchDTO(INPUTS, 10000L, true));
      new ArazzoRunController(arazzoRunService).launchRun(document.getId(), "readThenReprice",
            new ArazzoRunLaunchDTO(INPUTS, 10000L, null));

      assertEquals(List.of(true, false), minion.requests().stream().map(ArazzoRunRequest::validateSchema).toList());
   }

   @ParameterizedTest
   @CsvSource({ "JSON_SCHEMA, ", "SWAGGER, order-api.openapi.yaml" })
   void aLinkedApiIsHandedItsContractOnlyWhenItIsAnOpenApiOrSwaggerOne(ResourceType type, String contract)
         throws Exception {
      apiIsImported("customer-api.openapi.yaml");
      apiIsImported("order-api.openapi.yaml");
      String orderApi = serviceRepository.findByNameAndVersion("Order API", "1.0.0").getId();
      for (Resource resource : resourceRepository.findMainByServiceId(orderApi)) {
         resource.setType(type);
         resourceRepository.save(resource);
      }
      arazzoDocumentService.importArazzoSources(fixture("order-fulfilment.arazzo-sources.yaml"));
      ArazzoDocument document = arazzoDocumentService.importArazzoDocument(fixture("order-fulfilment.arazzo.yaml"));

      arazzoRunService.launchRun(document.getId(), "searchThenRead", Map.of("name", "Jane"), 10000L, true)
            .orElseThrow();

      Map<String, ArazzoRunRequest.Source> sources = minion.requests().get(0).sources();
      assertEquals(fixture("customer-api.openapi.yaml"), sources.get("customerApi").openApiContract());
      assertEquals(contract == null ? null : fixture(contract), sources.get("orderApi").openApiContract());
      assertEquals(contract == null ? null : RESOURCES, sources.get("orderApi").openApiContractBaseUrl());
   }

   @Test
   void aNonConformantResponseFailsTheStepEvenWhenItsCriteriaHold() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();
      WorkflowResult run = arazzoRunService.launchRun(document.getId(), "readThenReprice", INPUTS, 10000L, true)
            .orElseThrow();
      String reason = "/price does not match the Pastry schema: string found, number expected";

      arazzoRunService.recordStep(run.getId(),
            new WorkflowStepResult(1, "reprice",
                  new WorkflowStepOperation("pastryApi", "PatchPastry", "PATCH", "/pastry/{name}"),
                  WorkflowStepStatus.FAILED, 200, 6,
                  List.of(new WorkflowCriterionResult("$statusCode == 200",
                        List.of(new WorkflowExpressionValue("$statusCode", 200)), true)),
                  Map.of(), null, reason, null, List.of(), Map.of()),
            null, null);
      arazzoRunService.recordOutcome(run.getId(), WorkflowRunStatus.FAILURE, Map.of(), 12, null);

      WorkflowResult failed = arazzoRunService.getRun(run.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.FAILURE, failed.getStatus());
      assertEquals("reprice FAILED " + reason, failed.getSteps().get(0).stepId() + " "
            + failed.getSteps().get(0).status() + " " + failed.getSteps().get(0).message());
   }

   private ArazzoDocument pastryScenariosAreImportedAndLinked() throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");
      arazzoDocumentService.importArazzoSources(fixture("pastry.arazzo-sources.yaml"));
      return arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
   }

   private void apiIsImported(String fileName) throws Exception {
      File contract = new File(ArazzoRunSchemaValidationTest.class.getResource(FIXTURES + fileName).toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo(fileName, true));
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoRunSchemaValidationTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
