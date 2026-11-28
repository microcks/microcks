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
package io.microcks.service.arazzo.view;

import io.microcks.domain.workflow.WorkflowExample;
import io.microcks.domain.workflow.WorkflowStepOperation;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * What the API developer reads of a workflow of an Arazzo document before running it: its steps, the operations they
 * call, and how data flows between them.
 * @param documentId    The id of the Arazzo document the workflow belongs to
 * @param title         The title of the Arazzo document
 * @param version       The version of the Arazzo document
 * @param arazzoVersion The version of Arazzo the document uses
 * @param fileName      The name of the file the document was imported from, null if unknown
 * @param importDate    When the document was last imported
 * @param workflowIds   The ids of all the workflows of the document, in the order of the document
 * @param sources       Each source description of the document, in the order of the document
 * @param workflowId    The id of the workflow
 * @param summary       The summary of the workflow, null if it has none
 * @param description   The description of the workflow, null if it has none
 * @param inputs        The inputs of the workflow, with where each one is used
 * @param steps         The steps of the workflow, in the order of the document
 * @param outputs       The outputs of the workflow, by name, with their runtime expressions
 * @param dataPassings  The step outputs that later steps use, in the order of the steps that use them
 * @param status        Whether the workflow can run, else why
 * @param examples      The recorded examples of the workflow, in the order of its ArazzoExamples
 * @author SebastienDegodez
 */
public record ArazzoWorkflowDetail(String documentId, String title, String version, String arazzoVersion,
      String fileName, Date importDate, List<String> workflowIds, List<Source> sources,
      String workflowId, String summary, String description, List<Input> inputs,
      List<Step> steps, Map<String, String> outputs, List<DataPassing> dataPassings,
      ArazzoWorkflowStatus status, List<WorkflowExample> examples) {

   /**
    * A step of an Arazzo workflow, as the API developer reads it.
    * @param stepId          The id of the step
    * @param description     The description of the step, null if it has none
    * @param operation       The operation the step calls
    * @param parameters      The parameters the step sends, in the order of the document
    * @param requestBody     The request body the step sends, null if it sends none
    * @param successCriteria The criteria the response must meet, in the order of the document
    * @param outputs         The outputs of the step, by name, with their runtime expressions
    * @param onSuccess       What the step does when it succeeds, in the order of the document; empty to go on
    * @param onFailure       What the step does when it fails, in the order of the document; empty to end the workflow
    * @param messaging       What the step tells of the messages it sends or receives, for a step of an AsyncAPI source;
    *                        null for a step calling an HTTP operation
    * @param dependsOn       The steps the step declares it depends on, in the order of the document; empty when it
    *                        declares none
    * @param prerequisites   The steps the step must come after, in the order of the workflow: those it depends on and
    *                        those whose outputs it uses, or all the steps listed before it when it declares no
    * dependsOn
    * @author SebastienDegodez
    */
   public record Step(String stepId, String description, WorkflowStepOperation operation,
         List<Parameter> parameters, RequestBody requestBody,
         List<Criterion> successCriteria, Map<String, String> outputs, List<Action> onSuccess,
         List<Action> onFailure, Messaging messaging, List<String> dependsOn,
         List<String> prerequisites) {
   }

   /**
    * What a step of an Arazzo workflow does when it succeeds or fails, as the API developer reads it: retry the step,
    * or end the workflow.
    * @param name       The name of the action
    * @param type       retry or end
    * @param retryAfter How long a retry waits before calling the step again, in seconds; null for an end
    * @param retryLimit How many times a retry may call the step again; null for an end
    * @param criteria   The criteria that must hold for the action to apply, in the order of the document; empty when it
    *                   always applies
    * @author SebastienDegodez
    */
   public record Action(String name, String type, Double retryAfter, Integer retryLimit,
         List<Criterion> criteria) {
   }

   /**
    * A criterion the response of a step of an Arazzo workflow must meet.
    * @param condition The condition to meet
    * @param context   The runtime expression the condition applies to, null for a simple condition
    * @param type      The type of the condition: simple or regex
    * @author SebastienDegodez
    */
   public record Criterion(String condition, String context, String type) {
   }

   /**
    * A parameter a step of an Arazzo workflow sends.
    * @param name  The name of the parameter
    * @param in    Where the parameter is sent: path, query or header
    * @param value The value of the parameter: a constant, or a runtime expression
    * @author SebastienDegodez
    */
   public record Parameter(String name, String in, String value) {
   }

   /**
    * The request body a step of an Arazzo workflow sends.
    * @param contentType  The content type of the body, null if the document gives none
    * @param payload      The payload of the body, as written in the document
    * @param replacements The values replacing parts of the payload, by target, in the order of the document
    * @author SebastienDegodez
    */
   public record RequestBody(String contentType, String payload, Map<String, String> replacements) {
   }

   /**
    * What a step of an AsyncAPI source of an Arazzo workflow tells of the messages it sends or receives.
    * @param action        send or receive; null when the step does not tell it, its AsyncAPI operation then telling it
    * @param correlationId The runtime expression or value identifying the message a receive step waits for; null if
    *                      none
    * @param timeout       How long the step waits, in milliseconds; null when the step does not tell it
    * @author SebastienDegodez
    */
   public record Messaging(String action, String correlationId, Long timeout) {
   }

   /**
    * An output of a step of an Arazzo workflow that a later step uses.
    * @param fromStepId The id of the step giving the output
    * @param outputName The name of the output
    * @param to         Where the later step uses it
    * @author SebastienDegodez
    */
   public record DataPassing(String fromStepId, String outputName, ExpressionUse to) {
   }

   /**
    * Where a step of an Arazzo workflow uses a runtime expression.
    * @param stepId The id of the step
    * @param place  Where the step uses it: path, query or header for a parameter, body for a request body replacement,
    *               criterion for a success criterion
    * @param target The name of the parameter, or the pointer replaced in the body; null for a criterion
    * @author SebastienDegodez
    */
   public record ExpressionUse(String stepId, String place, String target) {
   }

   /**
    * An input of an Arazzo workflow, with where it is used.
    * @param name     The name of the input
    * @param usedBy   Where the steps use the input, in the order of the steps
    * @param type     The JSON schema type the workflow declares for the input, such as number; null when it declares
    *                 none
    * @param required Whether a run of the workflow needs the input: its steps or outputs use it, or its inputs schema
    *                 requires it
    * @author SebastienDegodez
    */
   public record Input(String name, List<ExpressionUse> usedBy, String type, boolean required) {
   }

   /**
    * A source description of an Arazzo document, as the API developer reads it: the Microcks API it is linked to, and
    * how many of the operations of that API the steps of the document call.
    * @param link           The link of the source description to a Microcks API
    * @param serviceId      The id of the linked Microcks API, null when the source is not linked
    * @param reason         Why the source is not linked, null when it is linked
    * @param operations     The number of operations the linked API defines in its main OpenAPI contract
    * @param usedOperations The number of these operations that steps of the document call
    * @author SebastienDegodez
    */
   public record Source(ArazzoSourceLink link, String serviceId, String reason, int operations,
         int usedOperations) {
   }
}
