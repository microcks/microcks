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
package io.microcks.minion.arazzo.runner;

import java.util.List;

/**
 * A success criterion of a step, evaluated on its response.
 * @param condition The condition of the criterion
 * @param values    The values of the runtime expressions of the condition, in the order of the condition
 * @param held      Whether the criterion held
 * @author SebastienDegodez
 */
public record CriterionResult(String condition, List<ExpressionValue> values, boolean held) {
}
