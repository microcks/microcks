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
package io.github.microcks.repository;

import io.github.microcks.domain.GenericResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * This is a Mongo repository interface for GenericResource domain objects.
 * 
 * @author laurent
 */
public interface GenericResourceRepository
      extends MongoRepository<GenericResource, String>, CustomGenericResourceRepository {

   List<GenericResource> findByServiceId(String serviceId, Pageable pageable);

   @Query(value = "{ 'reference' : true, 'serviceId' : ?0 }")
   List<GenericResource> findReferencesByServiceId(String serviceId);

   @Query(value = "{ 'serviceId' : ?0 }", count = true)
   long countByServiceId(String serviceId);
}
