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

import io.microcks.domain.EventMessage;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for {@code EventMessage} domain objects.
 * @author laurent
 */
@ApplicationScoped
public class EventMessageRepository implements ObjectIdRepository<EventMessage> {

   public List<EventMessage> findByOperationId(String operationId) {
      return list("operationId", operationId);
   }

   public List<EventMessage> findByTestCaseId(String testCaseId) {
      return list("testCaseId", testCaseId);
   }

   public List<EventMessage> findByOperationIdAndSourceArtifact(String operationId, String sourceArtifact) {
      return list("operationId = ?1 and sourceArtifact = ?2", operationId, sourceArtifact);
   }

   public List<EventMessage> findByOperationIdIn(List<String> operationIds) {
      return list("operationId in ?1", operationIds);
   }
}
