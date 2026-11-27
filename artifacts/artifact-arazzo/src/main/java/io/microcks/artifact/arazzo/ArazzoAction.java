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

import java.util.List;

/**
 * What a step of an Arazzo workflow does when it succeeds or fails: retry the step, or end the workflow. The first
 * action of a step whose criteria hold applies.
 * @param name       The name of the action
 * @param type       {@value #RETRY} or {@value #END}
 * @param retryAfter How long to wait before calling the step again, in seconds: 0 when the document gives none; null
 *                   for an action that does not retry
 * @param retryLimit How many times the step may be called again: 1 when the document gives none, as Arazzo says; null
 *                   for an action that does not retry
 * @param criteria   The criteria that must hold for the action to apply, in the order of the document; empty when it
 *                   always applies
 * @author SebastienDegodez
 */
public record ArazzoAction(String name, String type, Double retryAfter, Integer retryLimit,
      List<ArazzoCriterion> criteria) {

   /** The type of an action calling the failing step again. */
   public static final String RETRY = "retry";
   /** The type of an action ending the workflow at the step. */
   public static final String END = "end";
}
