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

import io.github.microcks.domain.ImportJob;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * Repository interface for ImportJob domain objects.
 * @author laurent
 */
public interface ImportJobRepository extends PagingAndSortingRepository<ImportJob, String>, CustomImportJobRepository {

   @Query("{ 'name' : {'$regex':?0, '$options':'i'}}")
   List<ImportJob> findByNameLike(String name);

   @Query("{ 'serviceRefs' : {'$elemMatch': {'serviceId':?0}}}")
   List<ImportJob> findByServiceRefId(String serviceRefId);
}
