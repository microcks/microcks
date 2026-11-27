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
 * Where the steps calling a source description send their requests.
 * @param baseUrl    The base URL the paths of the operations are appended to
 * @param operations The method and path of each operation of the source, by operationId
 * @author SebastienDegodez
 */
public record SourceTarget(URI baseUrl, Map<String, OperationTarget> operations) {
}
