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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The data flow of an Arazzo workflow: where its steps use its inputs and the outputs of earlier steps, read from the
 * runtime expressions of their parameters, request body replacements and success criteria.
 * @author SebastienDegodez
 */
public final class ArazzoDataFlow {

   /** Arazzo ids and output names are made of letters, digits, underscores and hyphens. */
   private static final String NAME = "([A-Za-z0-9_\\-]+)";
   private static final Pattern INPUT = Pattern.compile("\\$inputs\\." + NAME);
   private static final Pattern STEP_OUTPUT = Pattern.compile("\\$steps\\." + NAME + "\\.outputs\\." + NAME);

   private ArazzoDataFlow() {
   }

   /**
    * @param workflow A workflow of an Arazzo document
    * @return The step outputs that steps use, in the order of the steps using them, then of their places; each once
    */
   public static List<ArazzoStepOutputPassing> stepOutputPassings(ArazzoWorkflow workflow) {
      Set<ArazzoStepOutputPassing> passings = new LinkedHashSet<>();
      for (ExpressionPlace place : places(workflow)) {
         Matcher matcher = STEP_OUTPUT.matcher(place.expression());
         while (matcher.find()) {
            passings.add(new ArazzoStepOutputPassing(matcher.group(1), matcher.group(2), place.use()));
         }
      }
      return List.copyOf(passings);
   }

   /**
    * @param workflow A workflow of an Arazzo document
    * @return For each input, where the steps use it, in the order of the steps, then of their places; each once. The
    *         inputs the workflow declares come first, in the order of the document, then the inputs steps use without
    *         the workflow declaring them, in the order of use
    */
   public static Map<String, List<ArazzoUse>> inputUses(ArazzoWorkflow workflow) {
      Map<String, Set<ArazzoUse>> uses = new LinkedHashMap<>();
      workflow.inputNames().forEach(inputName -> uses.put(inputName, new LinkedHashSet<>()));
      for (ExpressionPlace place : places(workflow)) {
         Matcher matcher = INPUT.matcher(place.expression());
         while (matcher.find()) {
            uses.computeIfAbsent(matcher.group(1), inputName -> new LinkedHashSet<>()).add(place.use());
         }
      }
      Map<String, List<ArazzoUse>> inputUses = new LinkedHashMap<>();
      uses.forEach((inputName, inputPlaces) -> inputUses.put(inputName, List.copyOf(inputPlaces)));
      return inputUses;
   }

   /**
    * @param workflow A workflow of an Arazzo document
    * @return The inputs a run of the workflow needs: those its steps and outputs use, and those its inputs schema
    *         requires; each once. The inputs the workflow declares come first, in the order of the document, then the
    *         inputs used without being declared, in the order of use, then those required without being declared
    */
   public static List<String> neededInputs(ArazzoWorkflow workflow) {
      Set<String> used = new LinkedHashSet<>();
      for (String expression : expressions(workflow)) {
         Matcher matcher = INPUT.matcher(expression);
         while (matcher.find()) {
            used.add(matcher.group(1));
         }
      }
      Set<String> needed = new LinkedHashSet<>();
      workflow.inputNames().stream()
            .filter(inputName -> used.contains(inputName) || workflow.requiredInputNames().contains(inputName))
            .forEach(needed::add);
      needed.addAll(used);
      needed.addAll(workflow.requiredInputNames());
      return List.copyOf(needed);
   }

   /**
    * All the values of a workflow that may hold runtime expressions, in the order of the document: for each step, its
    * parameters, request body payload and replacements, criteria (its success criteria and those of its actions) and
    * outputs; then the outputs of the workflow.
    */
   private static List<String> expressions(ArazzoWorkflow workflow) {
      List<String> expressions = new ArrayList<>();
      for (ArazzoStep step : workflow.steps()) {
         step.parameters().forEach(parameter -> expressions.add(parameter.value()));
         if (step.requestBody() != null) {
            expressions.add(step.requestBody().payload());
            expressions.addAll(step.requestBody().replacements().values());
         }
         for (ArazzoCriterion criterion : criteriaOf(step)) {
            expressions.add(criterion.condition());
            if (criterion.context() != null) {
               expressions.add(criterion.context());
            }
         }
         expressions.addAll(step.outputs().values());
      }
      expressions.addAll(workflow.outputs().values());
      return expressions;
   }

   /** The places where the steps of a workflow may use runtime expressions, in the order of the steps. */
   private static List<ExpressionPlace> places(ArazzoWorkflow workflow) {
      List<ExpressionPlace> places = new ArrayList<>();
      for (ArazzoStep step : workflow.steps()) {
         for (ArazzoParameter parameter : step.parameters()) {
            places.add(new ExpressionPlace(parameter.value(),
                  new ArazzoUse(step.stepId(), parameter.in(), parameter.name())));
         }
         if (step.requestBody() != null) {
            step.requestBody().replacements().forEach((target, value) -> places
                  .add(new ExpressionPlace(value, new ArazzoUse(step.stepId(), ArazzoUse.BODY, target))));
         }
         ArazzoUse use = new ArazzoUse(step.stepId(), ArazzoUse.CRITERION, null);
         for (ArazzoCriterion criterion : criteriaOf(step)) {
            places.add(new ExpressionPlace(criterion.condition(), use));
            if (criterion.context() != null) {
               places.add(new ExpressionPlace(criterion.context(), use));
            }
         }
      }
      return places;
   }

   /** The success criteria of a step, then the criteria of its success actions and of its failure actions. */
   private static List<ArazzoCriterion> criteriaOf(ArazzoStep step) {
      List<ArazzoCriterion> criteria = new ArrayList<>(step.successCriteria());
      step.onSuccess().forEach(action -> criteria.addAll(action.criteria()));
      step.onFailure().forEach(action -> criteria.addAll(action.criteria()));
      return criteria;
   }

   /** A runtime expression, or a value embedding some, with where a step uses it. */
   private record ExpressionPlace(String expression, ArazzoUse use) {
   }
}
