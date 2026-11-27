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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The order the steps of an Arazzo workflow must be executed in. Arazzo 1.1 lets a step declare the steps it depends on
 * ({@code dependsOn}), and makes the outputs of a step that another step uses an implicit dependency of that step.
 * <p>
 * Microcks applies the rule its maintainer decided, "dependsOn relaxes":
 * <ul>
 * <li>a step that declares no dependsOn comes after every step listed before it, as when Arazzo had no dependsOn;</li>
 * <li>a step that declares dependsOn only comes after the steps it names and the steps whose outputs it uses.</li>
 * </ul>
 * A workflow without dependsOn is thus executed in the order of its list, as before. Executing the steps in the order
 * of the list, as the runner does, satisfies the rule for every workflow Microcks imports, since it refuses a step that
 * depends on a step listed after it.
 * @author SebastienDegodez
 */
public final class ArazzoStepOrder {

   private static final String INVALID = "Invalid Arazzo document: ";
   /** The runtime expressions naming a step of another workflow start with $workflows. or $sourceDescriptions. */
   private static final String EXPRESSION = "$";

   private ArazzoStepOrder() {
   }

   /**
    * Tell the steps a step must come after: its prerequisites. A consumer executing the workflow, or a judge checking
    * the order a workflow was executed in, may execute or accept the step once all its prerequisites are done.
    * @param workflow A workflow of an Arazzo document
    * @param stepId   The id of one of its steps
    * @return The ids of the prerequisites of the step, in the order of the steps of the workflow: when the step
    *         declares dependsOn, the steps it names and the steps whose outputs it uses; otherwise every step listed
    *         before it. Never the step itself
    * @throws IllegalArgumentException if the workflow has no step with that id
    */
   public static Set<String> prerequisitesOf(ArazzoWorkflow workflow, String stepId) {
      Set<String> prerequisites = prerequisites(workflow).get(stepId);
      if (prerequisites == null) {
         throw new IllegalArgumentException(workflow.workflowId() + " has no step " + stepId);
      }
      return prerequisites;
   }

   /**
    * Tell the steps that declare dependsOn although neither they nor any step they name send or receive messages.
    * Arazzo 1.1 (5.8.5.2) intends dependsOn to coordinate asynchronous steps and recommends the order of the list for
    * synchronous ones, without forbidding it: such a document is accepted, and these steps are worth a warning.
    * @param workflow A workflow of an Arazzo document
    * @return The ids of these steps, in the order of the workflow; empty when there is none
    */
   public static List<String> dependsOnBetweenSynchronousSteps(ArazzoWorkflow workflow) {
      Set<String> asynchronous = new HashSet<>();
      workflow.steps().stream().filter(step -> step.messaging() != null)
            .forEach(step -> asynchronous.add(step.stepId()));
      return workflow.steps().stream()
            .filter(step -> step.dependsOn() != null && !asynchronous.contains(step.stepId())
                  && step.dependsOn().stream().noneMatch(asynchronous::contains))
            .map(ArazzoStep::stepId).toList();
   }

   /**
    * @param workflow A workflow of an Arazzo document
    * @return The prerequisites of each step, by step id in the order of the workflow
    */
   private static Map<String, Set<String>> prerequisites(ArazzoWorkflow workflow) {
      Map<String, Set<String>> prerequisites = new LinkedHashMap<>();
      List<ArazzoStep> steps = workflow.steps();
      for (int index = 0; index < steps.size(); index++) {
         ArazzoStep step = steps.get(index);
         Set<String> named = new HashSet<>();
         if (step.dependsOn() == null) {
            steps.subList(0, index).forEach(previous -> named.add(previous.stepId()));
         } else {
            named.addAll(step.dependsOn());
            named.addAll(ArazzoDataFlow.usedStepOutputs(step));
         }
         Set<String> ordered = new LinkedHashSet<>();
         steps.stream().map(ArazzoStep::stepId)
               .filter(stepId -> named.contains(stepId) && !stepId.equals(step.stepId())).forEach(ordered::add);
         prerequisites.putIfAbsent(step.stepId(), Collections.unmodifiableSet(ordered));
      }
      return prerequisites;
   }

   /**
    * Check that the steps of a workflow can be executed in an order satisfying their dependsOn, and that the runner,
    * which executes them in the order of the list, does so.
    * @param workflow A workflow of an Arazzo document, some step of which declares dependsOn
    * @throws UnsupportedArazzoDocumentException if a step depends on a step of another workflow, or listed after it
    *                                            (temporarily: the runner plays one workflow, in the order of its list),
    *                                            or on an unknown step, on itself, or on steps depending on it (Arazzo
    *                                            forbids it)
    */
   static void check(ArazzoWorkflow workflow) throws UnsupportedArazzoDocumentException {
      List<String> stepIds = workflow.steps().stream().map(ArazzoStep::stepId).toList();
      for (ArazzoStep step : workflow.steps()) {
         checkNamedSteps(step, stepIds);
      }
      Map<String, Set<String>> prerequisites = prerequisites(workflow);
      for (String stepId : stepIds) {
         Set<String> cycle = cycleThrough(stepId, prerequisites);
         if (!cycle.isEmpty()) {
            throw new UnsupportedArazzoDocumentException(
                  INVALID + "steps depend on each other in a cycle: " + String.join(", ", cycle));
         }
      }
      for (ArazzoStep step : workflow.steps()) {
         if (step.dependsOn() != null) {
            checkListedBefore(step, prerequisites.get(step.stepId()), stepIds);
         }
      }
   }

   private static void checkNamedSteps(ArazzoStep step, List<String> stepIds)
         throws UnsupportedArazzoDocumentException {
      if (step.dependsOn() == null) {
         return;
      }
      for (String dependency : step.dependsOn()) {
         if (dependency.startsWith(EXPRESSION)) {
            throw new UnsupportedArazzoDocumentException(
                  "Unsupported step dependency on another workflow: " + dependency);
         }
         if (!stepIds.contains(dependency)) {
            throw new UnsupportedArazzoDocumentException(
                  INVALID + step.stepId() + " depends on an unknown step: " + dependency);
         }
         if (dependency.equals(step.stepId())) {
            throw new UnsupportedArazzoDocumentException(INVALID + "a step cannot depend on itself: " + step.stepId());
         }
      }
   }

   /**
    * @return The steps of the cycle the step is part of, in the order of the workflow; empty when it is in none
    */
   private static Set<String> cycleThrough(String stepId, Map<String, Set<String>> prerequisites) {
      Set<String> cycle = new LinkedHashSet<>();
      Set<String> reached = reachedFrom(stepId, prerequisites);
      if (reached.contains(stepId)) {
         prerequisites.keySet().stream()
               .filter(other -> reached.contains(other) && reachedFrom(other, prerequisites).contains(stepId))
               .forEach(cycle::add);
      }
      return cycle;
   }

   /** The steps reached from a step by following prerequisites, the step itself only when it is in a cycle. */
   private static Set<String> reachedFrom(String stepId, Map<String, Set<String>> prerequisites) {
      Set<String> reached = new HashSet<>();
      Deque<String> toFollow = new ArrayDeque<>(prerequisites.get(stepId));
      while (!toFollow.isEmpty()) {
         String next = toFollow.pop();
         if (reached.add(next)) {
            toFollow.addAll(prerequisites.get(next));
         }
      }
      return reached;
   }

   private static void checkListedBefore(ArazzoStep step, Set<String> prerequisites, List<String> stepIds)
         throws UnsupportedArazzoDocumentException {
      List<String> listedBefore = stepIds.subList(0, stepIds.indexOf(step.stepId()));
      for (String prerequisite : prerequisites) {
         if (!listedBefore.contains(prerequisite)) {
            String relation = step.dependsOn().contains(prerequisite) ? " depends on " : " uses the outputs of ";
            throw new UnsupportedArazzoDocumentException(
                  "Unsupported step order: " + step.stepId() + relation + prerequisite + ", listed after it");
         }
      }
   }
}
