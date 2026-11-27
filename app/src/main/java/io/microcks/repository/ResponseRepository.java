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
package io.microcks.repository;

import io.microcks.domain.Response;
import io.microcks.test.ResponseReader;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for {@code Response} domain objects.
 * @author laurent
 */
@ApplicationScoped
public class ResponseRepository implements ObjectIdRepository<Response>, ResponseReader {

   @Override
   public Response findById(String id) {
      return ObjectIdRepository.super.findById(id);
   }

   public List<Response> findByOperationId(String operationId) {
      return list("operationId", operationId);
   }

   public List<Response> findByTestCaseId(String testCaseId) {
      return list("testCaseId", testCaseId);
   }

   public List<Response> findByOperationIdAndName(String operationId, String name) {
      return list("operationId = ?1 and name = ?2", operationId, name);
   }

   public List<Response> findNonCallbackByOperationIdAndName(String operationId, String name) {
      return list("{'operationId': ?1, 'name': ?2, 'callbackName': {'$exists': false}}", operationId, name);
   }

   public List<Response> findByOperationIdAndDispatchCriteria(String operationId, String dispatchCriteria) {
      return list("operationId = ?1 and dispatchCriteria = ?2", operationId, dispatchCriteria);
   }

   public List<Response> findNonCallbackByOperationIdAndDispatchCriteria(String operationId, String dispatchCriteria) {
      return list("{'operationId': ?1, 'dispatchCriteria': ?2, 'callbackName': {'$exists': false}}", operationId,
            dispatchCriteria);
   }

   public List<Response> findByOperationIdAndSourceArtifact(String operationId, String sourceArtifact) {
      return list("operationId = ?1 and sourceArtifact = ?2", operationId, sourceArtifact);
   }

   public List<Response> findByOperationIdIn(List<String> operationIds) {
      return list("operationId in ?1", operationIds);
   }
}
