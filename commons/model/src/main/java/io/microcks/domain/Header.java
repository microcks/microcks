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

import java.util.HashSet;
import java.util.Set;

/**
 * Companion objects for all messages implementations instances. Represent some transport headers for both requests and
 * responses.
 * @author laurent
 */
public class Header {

   private String name;

   private Set<String> values = new HashSet<>();

   /**
    * Default empty constructor.
    */
   public Header() {
   }

   /**
    * Build a new Header with a name and a set of values.
    * @param name   The header name
    * @param values The set of values for this header
    */
   public Header(String name, Set<String> values) {
      this.name = name;
      this.values = values;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public Set<String> getValues() {
      return values;
   }

   public void setValues(Set<String> values) {
      this.values = values;
   }
}
