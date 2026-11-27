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
package io.microcks.artifact.arazzo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of the subset of Arazzo that Microcks supports. The webapp import tests cover one example of each acceptance
 * criterion; this reader is shared with the components that run workflows, so every refused feature is covered here.
 * <p>
 * All the features below are valid Arazzo 1.0.1 or 1.1.0: they are refused ONLY TEMPORARILY, until the workflow runner
 * executes them. A document using one of them is refused rather than imported, because a run that silently skipped them
 * would report a result the journey does not have: an ignored retry fails a run that should pass, an ignored dependsOn
 * runs workflows in the wrong order. When a story makes the runner handle one of them, it removes its example from
 * these tests and from ArazzoSubset.
 * @author SebastienDegodez
 */
class ArazzoDocumentReaderTest {

   @Test
   void aSupportedDocumentIsReadWithItsTitleVersionAndWorkflows() throws Exception {
      assertEquals(
            new ArazzoDescription("API Pastry scenarios", "1.0.0", List.of("readThenReprice", "listThenReadFirst")),
            ArazzoDocumentReader.read(document("pastry.arazzo.yaml")));
   }

   @Test
   void theArazzoVersionOfADocumentIsRead() throws IOException {
      assertEquals("1.0.1", ArazzoDocumentReader.arazzoVersion(document("pastry.arazzo.yaml")));
   }

   @Test
   void theSourceDescriptionsOfADocumentAreReadByName() throws IOException {
      assertEquals(List.of("pastryApi"), ArazzoDocumentReader.sourceDescriptionNames(document("pastry.arazzo.yaml")));
   }

   @Test
   void theUrlEachSourceDescriptionDeclaresIsReadInTheOrderOfTheDocument() throws IOException {
      assertEquals("{customerApi=./customer-api.openapi.yaml, orderApi=./order-api.openapi.yaml}", ArazzoDocumentReader
            .sourceDescriptionUrls(exampleOf("called-sources.arazzo.yaml", "named-source")).toString());
   }

   @ParameterizedTest
   @CsvSource({ "summary, Reads a pastry.", "description, Reads a pastry by its name.",
         "summary-and-description, Reads a pastry.", "none," })
   void aWorkflowIsDescribedByItsSummaryOrElseItsDescription(String example, String description) throws IOException {
      assertEquals(Collections.singletonMap("readOne", description),
            ArazzoDocumentReader.workflowDescriptions(exampleOf("workflow-descriptions.arazzo.yaml", example)));
   }

   @ParameterizedTest
   @CsvSource({ "operation-id-alone, pastryApi, GetPastryByName", "named-source, orderApi, CreateOrder",
         "dotted-operation-id, orderApi, orders.create" })
   void theOperationAStepCallsIsReadFromItsOperationId(String example, String sourceName, String operationId)
         throws IOException {
      assertEquals(Map.of("callOne", List.of(new ArazzoOperationCall(sourceName, operationId))),
            ArazzoDocumentReader.workflowCalls(exampleOf("called-sources.arazzo.yaml", example)));
   }

   @Test
   void anUnsupportedVersionIsRefusedWithItsReason() throws IOException {
      assertRefused(document("unsupported-version.arazzo.yaml"), "Unsupported Arazzo version: 2.0.0");
   }

   @ParameterizedTest
   @CsvSource({ "pastry.arazzo.yaml, true", "pastry.openapi.yaml, false" })
   void onlyADocumentDeclaringAnArazzoVersionIsAnArazzoDocument(String fileName, boolean arazzoDocument)
         throws IOException {
      assertEquals(arazzoDocument, ArazzoDocumentReader.isArazzoDocument(document(fileName)));
   }

   /**
    * S28: an Arazzo 1.1 document may describe AsyncAPI sources, whose steps send or receive messages through an
    * operation, or on a channel its channelPath points to. A step of an AsyncAPI source calls the operation it names,
    * or listens on the channel, read from the JSON pointer of its channelPath, ~1 standing for a slash and ~0 for a
    * tilde.
    */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
         "mixed-sources; placeThenConfirm=[orderApi CreateOrder, orderEvents sendShippingRequest, orderEvents "
               + "receiveOrderConfirmed]",
         "channel-path; listen=[orderEvents channel orderConfirmed]",
         "escaped-channel-path; listen=[orderEvents channel orders/confirmed~v1]",
         "implicit-action; listen=[orderEvents receiveOrderConfirmed]",
         "zero-timeout; listen=[orderEvents receiveOrderConfirmed]" })
   void aDocumentWithAsyncApiSourcesIsReadWithWhatItsStepsCall(String example, String calls) throws Exception {
      JsonNode document = exampleOf("async-steps.arazzo.yaml", example);

      ArazzoDocumentReader.read(document);

      assertEquals(calls,
            ArazzoDocumentReader.workflowCalls(document).entrySet().stream()
                  .map(workflow -> workflow.getKey() + "=" + workflow.getValue().stream()
                        .map(call -> call.sourceName() + " "
                              + (call.channel() == null ? call.operationId() : "channel " + call.channel()))
                        .toList())
                  .findFirst().orElseThrow());
   }

   /**
    * S28: Arazzo 1.1 lets a step send or receive messages only on an AsyncAPI source, and only receive a message by its
    * correlationId; a timeout is a number of milliseconds; a step names an operation or a channel, not both, and a
    * channelPath points to a channel of a source description of the document. These documents are invalid Arazzo,
    * refused permanently. A step on a channel must also tell its action: Arazzo leaves it optional, but a channel,
    * unlike an operation, does not tell whether the step sends or receives, so Microcks refuses such a step rather than
    * guess. The fields of an AsyncAPI step are never ignored silently.
    */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
         "action-on-http-step; Invalid Arazzo document: action only applies to a step of an AsyncAPI source: "
               + "createOrder",
         "correlation-id-on-http-step; Invalid Arazzo document: correlationId only applies to a step of an AsyncAPI "
               + "source: createOrder",
         "channel-path-to-openapi; Invalid Arazzo document: channelPath only applies to a step of an AsyncAPI source: "
               + "createOrder",
         "unknown-action; Invalid Arazzo document: a step action is send or receive, not subscribe: confirmOrder",
         "correlation-id-on-send; Invalid Arazzo document: only a receive step has a correlationId: requestShipping",
         "negative-timeout; Invalid Arazzo document: a step timeout is a number of milliseconds, not -1: "
               + "confirmOrder",
         "text-timeout; Invalid Arazzo document: a step timeout is a number of milliseconds, not 6s: confirmOrder",
         "decimal-timeout; Invalid Arazzo document: a step timeout is a number of milliseconds, not 1.5: "
               + "confirmOrder",
         "operation-and-channel; Invalid Arazzo document: a step names an operationId or a channelPath, not both: "
               + "confirmOrder",
         "not-a-channel; Invalid Arazzo document: channelPath does not point to a channel of a source description: "
               + "confirmOrder",
         "nested-channel-pointer; Invalid Arazzo document: channelPath does not point to a channel of a source "
               + "description: confirmOrder",
         "channel-of-unknown-source; Unknown source description: unknown",
         "channel-without-action; Invalid Arazzo document: a step on a channel tells its action, send or receive: "
               + "confirmOrder" })
   void aStepSendingOrReceivingMessagesOutsideTheRulesOfArazzoIsRefusedWithItsReason(String example, String reason)
         throws IOException {
      assertRefused(example("async-steps", example), reason);
   }

   /**
    * Temporary refusal: nested workflows and operationPath are listed under "Later" in the Arazzo epic. The runner only
    * calls one operation per step, by operationId. Success and failure actions are no longer refused here since S17:
    * see {@link #aStepActionOutsideTheSupportedSubsetIsRefusedWithItsReason(String, String)}.
    * <p>
    * Temporary refusal since S28: the timeout of an HTTP step, as the runner gives every HTTP step the timeout of the
    * run. The timeout of an AsyncAPI step is read: see
    * {@link #aDocumentWithAsyncApiSourcesIsReadWithWhatItsStepsCall(String, String)}. The dependsOn of a step is read
    * since S30: see {@link ArazzoStepOrderTest}.
    */
   @ParameterizedTest
   @CsvSource({ "operation-path, operationPath", "nested-workflow, workflowId", "http-timeout, timeout" })
   void aStepFieldOutsideTheSupportedSubsetIsRefusedWithItsReason(String example, String field) throws IOException {
      assertRefused(example("step-fields", example), "Unsupported step field: " + field);
   }

   /**
    * Temporary refusal: dependsOn and the default success and failure actions of a workflow are listed under "Later" in
    * the Arazzo epic. The runner plays one workflow at a time, and only applies the actions each step declares (S17).
    * The dependsOn of a step only names steps of its own workflow (S30).
    */
   @ParameterizedTest
   @CsvSource({ "depends-on, dependsOn", "success-actions, successActions", "failure-actions, failureActions" })
   void aWorkflowFieldOutsideTheSupportedSubsetIsRefusedWithItsReason(String example, String field) throws IOException {
      assertRefused(example("workflow-fields", example), "Unsupported workflow field: " + field);
   }

   /**
    * A step may retry when it fails and end the workflow when it succeeds or fails (S17): a document doing so is read.
    */
   @ParameterizedTest
   @CsvSource({ "retry-until-ready, waitUntilReady", "retry-defaults, retryOnce",
         "end-with-criteria, stopWhilePreparing", "several-actions, retryUnlessUnknown" })
   void aStepThatRetriesOrEndsTheWorkflowIsRead(String example, String workflowId) throws Exception {
      assertEquals(List.of(workflowId),
            ArazzoDocumentReader.read(exampleOf("step-actions.arazzo.yaml", example)).workflowIds());
   }

   /**
    * Temporary refusal: goto actions, to another step or another workflow, are listed under "Later" in the Arazzo epic,
    * as are reusable actions of the components. The runner plays the steps in order, retrying a step or ending the
    * workflow (S17). A JSONPath criterion of an action is refused as the one of a step is.
    */
   @ParameterizedTest
   @CsvSource({ "goto-on-success, Unsupported action type: goto", "goto-on-failure, Unsupported action type: goto",
         "reusable-action, Unsupported reusable action: $components.failureActions.retryLater",
         "jsonpath-criterion, Unsupported criterion type: jsonpath" })
   void aStepActionOutsideTheSupportedSubsetIsRefusedWithItsReason(String example, String reason) throws IOException {
      assertRefused(example("actions", example), reason);
   }

   /**
    * Permanent refusal, unlike the others: these documents are invalid Arazzo, not features the runner does not handle
    * yet. Arazzo requires an operationId to name its source description when there are several, and a named source
    * description to be declared by the document; otherwise the step cannot be linked to an API. Arazzo also requires an
    * info with a title and a version, which identify the document in Microcks. Arazzo only retries a failing step, and
    * only defines the end, goto and retry action types.
    */
   @ParameterizedTest
   @CsvSource({ "lone-operation-id, Ambiguous operationId CreateOrder: the document has several source descriptions",
         "unknown-source, Unknown source description: unknown",
         "no-info, Invalid Arazzo document: info.title is required",
         "no-info-title, Invalid Arazzo document: info.title is required",
         "no-info-version, Invalid Arazzo document: info.version is required",
         "success-retry, Invalid Arazzo document: a success action cannot retry",
         "unknown-action-type, Unsupported action type: abort" })
   void aDocumentArazzoForbidsIsRefusedWithItsReason(String example, String reason) throws IOException {
      assertRefused(example("invalid", example), reason);
   }

   /**
    * Temporary refusal: Arazzo source descriptions, whose workflows a step would run, are listed under "Later" in the
    * Arazzo epic, as nested workflows are. AsyncAPI source descriptions are read since S28: see
    * {@link #aDocumentWithAsyncApiSourcesIsReadWithWhatItsStepsCall(String, String)}.
    */
   @Test
   void aSourceDescriptionTypeOutsideTheSupportedSubsetIsRefusedWithItsReason() throws IOException {
      assertRefused(example("source-descriptions", "arazzo"), "Unsupported source description type: arazzo");
   }

   /**
    * Temporary refusal: the runner only sends parameters in the path, the query string and the headers. Cookies are not
    * planned by a story yet.
    */
   @ParameterizedTest
   @CsvSource({ "step-cookie", "workflow-cookie" })
   void aParameterLocationOutsideTheSupportedSubsetIsRefusedWithItsReason(String example) throws IOException {
      assertRefused(example("parameters", example), "Unsupported parameter location: cookie");
   }

   /**
    * Temporary refusal: the runner only evaluates simple and regex criteria. JSONPath and XPath criteria are not
    * planned by a story yet.
    */
   @ParameterizedTest
   @CsvSource({ "jsonpath", "xpath" })
   void aCriterionTypeOutsideTheSupportedSubsetIsRefusedWithItsReason(String type) throws IOException {
      assertRefused(example("criteria", type), "Unsupported criterion type: " + type);
   }

   private static void assertRefused(JsonNode document, String reason) {
      UnsupportedArazzoDocumentException refusal = assertThrows(UnsupportedArazzoDocumentException.class,
            () -> ArazzoDocumentReader.read(document));

      assertEquals(reason, refusal.getMessage());
   }

   private static JsonNode document(String fileName) throws IOException {
      try (InputStream stream = ArazzoDocumentReaderTest.class
            .getResourceAsStream("/io/microcks/artifact/arazzo/" + fileName)) {
         return new ObjectMapper(new YAMLFactory()).readTree(stream);
      }
   }

   /**
    * @param category The category of unsupported features, see {@link #exampleOf(String, String)}
    * @param example  The name of the example
    * @return The parsed document of that example
    */
   private static JsonNode example(String category, String example) throws IOException {
      return exampleOf("unsupported/" + category + ".arazzo.yaml", example);
   }

   /**
    * @param fixture The fixture of a category, holding one YAML document per example, each announced by a
    *                {@code # example: <name>} comment
    * @param example The name of the example
    * @return The parsed document of that example
    */
   private static JsonNode exampleOf(String fixture, String example) throws IOException {
      try (InputStream stream = ArazzoDocumentReaderTest.class
            .getResourceAsStream("/io/microcks/artifact/arazzo/" + fixture)) {
         String marker = "# example: " + example + " ";
         String document = Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n---\n"))
               .filter(d -> d.contains(marker)).findFirst()
               .orElseThrow(() -> new IllegalArgumentException(example + " is not in " + fixture));
         return new ObjectMapper(new YAMLFactory()).readTree(document);
      }
   }
}
