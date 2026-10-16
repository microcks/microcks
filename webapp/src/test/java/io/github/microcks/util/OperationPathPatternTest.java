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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for OperationPathPattern class.
 */
class OperationPathPatternTest {

   @Test
   void unresolvedVariableSegmentMatchesItselfLiterally() {
      // Issue #2366: '{{petId}}' must be kept as literal text in the pattern, not dropped. This unit-level
      // specification is not observable through HTTP: Tomcat rejects raw braces in a request path and the encoded
      // form '%7B%7BpetId%7D%7D' is never decoded before matching.
      OperationPathPattern pattern = OperationPathPattern.compile("/pets/{{petId}}/toys/:toyId");

      assertTrue(pattern.matches("/pets/{{petId}}/toys/3"));
   }
}
