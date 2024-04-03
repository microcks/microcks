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
package io.github.microcks.minion.async;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
/**
 * Repository for AsyncMockDefinitions. Used as a backend storage for jobs that have to publish event messages at
 * specified frequencies. Has to be initialized at application startup and regularly keep-in-sync with Microcks server.
 * @author laurent
 */
public class AsyncMockRepository {

   private Set<AsyncMockDefinition> mockDefinitions = new HashSet<>();

   /**
    * Retrieve the AsyncMockDefinitions present in store.
    * @return A set of AsyncMockDefinitions
    */
   public Set<AsyncMockDefinition> getMocksDefinitions() {
      return mockDefinitions;
   }

   /**
    * Store a new or update an existing AsyncMockDefinition in store.
    * @param mockDefinition Definition to store or update in store
    */
   public void storeMockDefinition(AsyncMockDefinition mockDefinition) {
      if (mockDefinitions.contains(mockDefinition)) {
         mockDefinitions.remove(mockDefinition);
      }
      mockDefinitions.add(mockDefinition);
   }

   /**
    * Remove the AsyncMockDefinitions corresponding to owner Service id.
    * @param serviceId The identifier of owner Service.
    */
   public void removeMockDefinitions(String serviceId) {
      Set<AsyncMockDefinition> serviceDefs = mockDefinitions.stream()
            .filter(d -> d.getOwnerService().getId().equals(serviceId)).collect(Collectors.toSet());
      mockDefinitions.removeAll(serviceDefs);
   }

   /**
    * Retrieve the set of frequencies of Operation found within definitions in store.
    * @return A set of frequencies for definitions operations
    */
   public Set<Long> getMockDefinitionsFrequencies() {
      return mockDefinitions.stream().map(d -> d.getOperation().getDefaultDelay()).collect(Collectors.toSet());
   }

   /**
    * Retrieve all the AsyncMockDefinition corresponding to a specified operation frequency.
    * @param frequency The operation frequency to get definitions for
    * @return A set of AsyncMockDefinition having specified operation frequency
    */
   public Set<AsyncMockDefinition> getMockDefinitionsByFrequency(Long frequency) {
      return mockDefinitions.stream().filter(d -> d.getOperation().getDefaultDelay().equals(frequency))
            .collect(Collectors.toSet());
   }

   /**
    * Retrieve the AsyncMockDefinition corresponding to a specified service and version.
    * @param serviceName The service name to get definition for
    * @param version     The service version to get definition for
    * @return Should return an empty of 1 element set only.
    */
   public Set<AsyncMockDefinition> getMockDefinitionsByServiceAndVersion(String serviceName, String version) {
      return mockDefinitions.stream().filter(
            d -> d.getOwnerService().getName().equals(serviceName) && d.getOwnerService().getVersion().equals(version))
            .collect(Collectors.toSet());
   }
}
