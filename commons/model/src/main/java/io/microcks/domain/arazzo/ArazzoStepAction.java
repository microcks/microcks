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
package io.microcks.domain.arazzo;

import java.util.List;

/**
 * What a step of an Arazzo workflow does when it succeeds or fails, as the API developer reads it: retry the step, or
 * end the workflow.
 * @param name       The name of the action
 * @param type       retry or end
 * @param retryAfter How long a retry waits before calling the step again, in seconds; null for an end
 * @param retryLimit How many times a retry may call the step again; null for an end
 * @param criteria   The criteria that must hold for the action to apply, in the order of the document; empty when it
 *                   always applies
 * @author SebastienDegodez
 */
public record ArazzoStepAction(String name, String type, Double retryAfter, Integer retryLimit,
      List<ArazzoStepCriterion> criteria) {
}
