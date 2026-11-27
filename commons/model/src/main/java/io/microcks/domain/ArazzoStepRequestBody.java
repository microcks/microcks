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

import java.util.Map;

/**
 * The request body a step of an Arazzo workflow sends.
 * @param contentType  The content type of the body, null if the document gives none
 * @param payload      The payload of the body, as written in the document
 * @param replacements The values replacing parts of the payload, by target, in the order of the document
 * @author SebastienDegodez
 */
public record ArazzoStepRequestBody(String contentType, String payload, Map<String, String> replacements) {
}
