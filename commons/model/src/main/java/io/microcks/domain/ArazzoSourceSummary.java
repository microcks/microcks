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
package io.microcks.domain;

/**
 * A source description of an Arazzo document, with the Microcks API it is linked to.
 * @param link    The link of the source description to a Microcks API
 * @param apiId   The id of the linked API; null when the source is not linked
 * @param apiType The type of the linked API; null when the source is not linked
 * @author SebastienDegodez
 */
public record ArazzoSourceSummary(ArazzoSourceLink link, String apiId, ServiceType apiType) {
}
