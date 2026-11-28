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
package io.microcks.artifact.jsonschema;

/**
 * A violation of a JSON schema by a JSON value, and where in the value it is.
 * @param pointer The JSON pointer of the part of the value that violates the schema, such as /price, or the empty
 *                pointer for the whole value; null when the violation is not about a part of the value but about the
 *                schema it is validated against
 * @param message What is violated, such as "string found, number expected"
 * @author SebastienDegodez
 */
public record JsonSchemaViolation(String pointer, String message) {
}
