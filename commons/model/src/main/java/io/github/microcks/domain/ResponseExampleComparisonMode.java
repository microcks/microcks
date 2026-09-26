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
package io.github.microcks.domain;

/**
 * Comparison modes for checking an actual JSON response against its OpenAPI response example.
 */
public enum ResponseExampleComparisonMode {
   /** Require the same JSON structure and values while ignoring array ordering. */
   STRICT,
   /** Require the example structure and values while allowing additional object fields and array elements. */
   LENIENT
}
