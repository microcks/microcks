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

import java.util.Map;

/**
 * The request a step sent, as the minion reports it.
 * @param method  The HTTP method
 * @param url     The full URL
 * @param headers The headers, by name
 * @param body    The body; null when none
 * @author SebastienDegodez
 */
public record RequestDTO(String method, String url, Map<String, String> headers, String body) {
}
