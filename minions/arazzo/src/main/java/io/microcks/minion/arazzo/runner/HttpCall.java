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

import java.net.URI;
import java.util.Map;

/**
 * A request a step sends.
 * @param method  The HTTP method
 * @param uri     The full URI, with path and query parameters
 * @param headers The headers, by name
 * @param body    The body; null when the step sends none
 * @author SebastienDegodez
 */
public record HttpCall(String method, URI uri, Map<String, String> headers, String body) {
}
