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
package io.github.microcks.minion.async.producer;

import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceView;
import io.github.microcks.domain.TestCaseResult;
import io.github.microcks.minion.async.client.KeycloakConfig;
import io.github.microcks.minion.async.client.MicrocksAPIConnector;
import io.github.microcks.minion.async.client.dto.TestCaseReturnDTO;

import java.util.Collections;
import java.util.List;

/**
 * A fake implementation of MicrocksAPIConnector for testing purposes.
 * @author laurent
 */
public class FakeMicrocksAPIConnector implements MicrocksAPIConnector {

   private final String serviceId;
   private final String asyncAPIContent;

   FakeMicrocksAPIConnector(String serviceId, String asyncAPIContent) {
      this.serviceId = serviceId;
      this.asyncAPIContent = asyncAPIContent;
   }

   @Override
   public KeycloakConfig getKeycloakConfig() {
      return null;
   }

   @Override
   public List<Service> listServices(String authorization, int page, int size) {
      return null;
   }

   @Override
   public ServiceView getService(String authorization, String serviceId, boolean messages) {
      return null;
   }

   @Override
   public List<Resource> getResources(String serviceId) {
      if (this.serviceId.equals(serviceId)) {
         Resource resource = new Resource();
         resource.setId("578497d2-2695-4aff-b7c6-ff7b9a373aa9");
         resource.setType(ResourceType.ASYNC_API_SPEC);
         resource.setContent(this.asyncAPIContent);
         return List.of(resource);
      }
      return Collections.emptyList();
   }

   @Override
   public TestCaseResult reportTestCaseResult(String testResultId, TestCaseReturnDTO testCaseReturn) {
      return null;
   }
}
