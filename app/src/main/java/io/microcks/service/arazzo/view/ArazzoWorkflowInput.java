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

import java.util.List;

/**
 * An input of an Arazzo workflow, with where it is used.
 * @param name     The name of the input
 * @param usedBy   Where the steps use the input, in the order of the steps
 * @param type     The JSON schema type the workflow declares for the input, such as number; null when it declares none
 * @param required Whether a run of the workflow needs the input: its steps or outputs use it, or its inputs schema
 *                 requires it
 * @author SebastienDegodez
 */
public record ArazzoWorkflowInput(String name, List<ArazzoExpressionUse> usedBy, String type, boolean required) {
}
