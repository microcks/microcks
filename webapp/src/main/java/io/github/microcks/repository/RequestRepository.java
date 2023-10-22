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

import io.github.microcks.domain.Request;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Repository interface for Request domain objects.
 * 
 * @author laurent
 */
public interface RequestRepository extends MongoRepository<Request, String> {

   List<Request> findByOperationId(String operationId);

   List<Request> findByTestCaseId(String testCaseId);

   List<Request> findByOperationIdAndSourceArtifact(String operationId, String sourceArtifact);

   @Query("{ 'operationId' : {'$in' : ?0}}")
   List<Request> findByOperationIdIn(List<String> operationIds);
}
