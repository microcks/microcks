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

package io.github.microcks.service;

import io.github.microcks.domain.ServiceState;
import io.github.microcks.repository.ServiceStateRepository;

import javax.annotation.Nullable;
import java.util.Date;

/**
 * An implementation of {@code StateStore} scoped to Service, that uses a MongoDB repository with a collection having
 * some Time To Live indices.
 * @author laurent
 */
public class ServiceStateStore implements StateStore {

   private static final int DEFAULT_SECONDS_TTL = 10;

   private final ServiceStateRepository repository;
   private final String serviceId;

   /**
    * Build a ServiceStateStore for required elements.
    * @param repository The MongoDB repository to use for persistence
    * @param serviceId  The ID of Service this state store will be scoped to
    */
   public ServiceStateStore(ServiceStateRepository repository, String serviceId) {
      this.repository = repository;
      this.serviceId = serviceId;
   }

   public void put(String key, String value) {
      put(key, value, DEFAULT_SECONDS_TTL);
   }

   public void put(String key, String value, int secondsTTL) {
      ServiceState state = repository.findByServiceIdAndKey(serviceId, key);
      if (state == null) {
         state = new ServiceState(serviceId, key);
      }
      state.setValue(value);
      state.setExpireAt(new Date(System.currentTimeMillis() + (secondsTTL * 1000L)));
      repository.save(state);
   }

   @Nullable
   public String get(String key) {
      ServiceState state = repository.findByServiceIdAndKey(serviceId, key);
      if (state != null) {
         return state.getValue();
      }
      return null;
   }

   public void delete(String key) {
      ServiceState state = repository.findByServiceIdAndKey(serviceId, key);
      if (state != null) {
         repository.delete(state);
      }
   }
}
