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
 * The link between a source description of an Arazzo document and the Microcks API it describes.
 * @param sourceName The name of the source description in the Arazzo document
 * @param apiName    The name of the API the source is mapped to by the ArazzoMetadata
 * @param apiVersion The version of the API the source is mapped to by the ArazzoMetadata
 * @param linked     Whether an API with that name and version exists in Microcks
 * @author SebastienDegodez
 */
public record ArazzoSourceLink(String sourceName, String apiName, String apiVersion, boolean linked) {
}
