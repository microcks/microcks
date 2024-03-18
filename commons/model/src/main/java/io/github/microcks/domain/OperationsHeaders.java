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

import java.util.HashMap;
import java.util.Set;

/**
 * Specification of additional headers for a Service/API operations. Keys are operation name or "globals" (if header
 * applies to all), values are Header objects.
 * @author laurent
 */
public class OperationsHeaders extends HashMap<String, Set<Header>> {

   public static final String GLOBALS = "globals";

   public Set<Header> getGlobals() {
      return this.get(GLOBALS);
   }
}
