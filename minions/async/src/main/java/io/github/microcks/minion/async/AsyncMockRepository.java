/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.minion.async;

import javax.enterprise.context.ApplicationScoped;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
/**
 * @author laurent
 */
public class AsyncMockRepository {

   private Set<AsyncMockDefinition> mockDefinitions = new HashSet<>();

   /**
    *
    * @return
    */
   public Set<AsyncMockDefinition> getMocksDefinitions() {
      return mockDefinitions;
   }

   /**
    *
    * @param mockDefinition
    */
   public void storeMockDefinition(AsyncMockDefinition mockDefinition) {
      if (mockDefinitions.contains(mockDefinition)) {
         mockDefinitions.remove(mockDefinition);
      }
      mockDefinitions.add(mockDefinition);
   }


   public Set<Long> getMockDefinitionsFrequencies() {
      return mockDefinitions.stream()
            .map(d -> d.getOperation().getDefaultDelay())
            .collect(Collectors.toSet());
   }

   /**
    *
    * @param frequency
    * @return
    */
   public Set<AsyncMockDefinition> getMockDefinitionsByFrequency(Long frequency) {
      return mockDefinitions.stream()
            .filter(d -> d.getOperation().getDefaultDelay().equals(frequency))
            .collect(Collectors.toSet());
   }
}
