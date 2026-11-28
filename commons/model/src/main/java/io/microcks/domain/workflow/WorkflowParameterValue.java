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
package io.microcks.domain.workflow;

/**
 * A parameter a step of a run sent, with the value it resolved to and where that value came from.
 * @param name       The name of the parameter
 * @param in         Where it was sent: path, query or header
 * @param value      The value it was sent with
 * @param expression The runtime expression the value came from, such as $steps.list.outputs.first; null for a constant
 * @author SebastienDegodez
 */
public record WorkflowParameterValue(String name, String in, Object value, String expression) {
}
