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
package io.github.microcks.util;

/**
 * Identifies the JSON Schema draft dialect to use when validating a payload. Decouples Microcks utility APIs from the
 * underlying validator implementation so callers don't have to depend on a specific library type.
 */
public enum JsonSchemaDialect {
   /** JSON Schema draft-04 — required by OpenAPI 3.0.x. */
   DRAFT_4,
   /** JSON Schema draft 2020-12 — used by OpenAPI 3.1.x and the default elsewhere. */
   DRAFT_2020_12
}
