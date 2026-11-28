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

/**
 * A criterion the response of a step of an Arazzo workflow must meet.
 * @param condition The condition to meet
 * @param context   The runtime expression the condition applies to, null for a simple condition
 * @param type      The type of the condition: simple or regex
 * @author SebastienDegodez
 */
public record ArazzoStepCriterion(String condition, String context, String type) {
}
