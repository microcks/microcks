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

/**
 * A source description of an Arazzo document, as the API developer reads it: the Microcks API it is linked to, and how
 * many of the operations of that API the steps of the document call.
 * @param link           The link of the source description to a Microcks API
 * @param serviceId      The id of the linked Microcks API, null when the source is not linked
 * @param reason         Why the source is not linked, null when it is linked
 * @param operations     The number of operations the linked API defines in its main OpenAPI contract
 * @param usedOperations The number of these operations that steps of the document call
 * @author SebastienDegodez
 */
public record ArazzoSourceDescription(ArazzoSourceLink link, String serviceId, String reason, int operations,
      int usedOperations) {
}
