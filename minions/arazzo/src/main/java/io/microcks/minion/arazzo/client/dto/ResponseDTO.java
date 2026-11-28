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
package io.microcks.minion.arazzo.client.dto;

import java.util.List;
import java.util.Map;

/**
 * The response a step received, as the minion reports it.
 * @param status  The status code
 * @param headers The headers, by name
 * @param body    The body, as text
 * @author SebastienDegodez
 */
public record ResponseDTO(int status, Map<String, List<String>> headers, String body) {
}
