/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microcks.test;

import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;

import java.util.List;

/**
 * Read-only access to the {@code Resource} artifacts needed by test runners. Implemented by the persistence layer so
 * that runners do not depend on any store.
 * @author laurent
 */
public interface ResourceReader {

   /**
    * Get all the resources attached to a service.
    * @param serviceId The identifier of the service
    * @return The resources of the service
    */
   List<Resource> findByServiceId(String serviceId);

   /**
    * Get the resources of a given type attached to a service.
    * @param serviceId The identifier of the service
    * @param type      The type of resources
    * @return The matching resources
    */
   List<Resource> findByServiceIdAndType(String serviceId, ResourceType type);

   /**
    * Get the main resources attached to a service.
    * @param serviceId The identifier of the service
    * @return The main resources of the service
    */
   List<Resource> findMainByServiceId(String serviceId);
}
