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

import io.microcks.domain.WebhookRegistration;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for {@code WebhookRegistration} domain objects.
 * @author laurent
 */
@ApplicationScoped
public class WebhookRegistrationRepository implements ObjectIdRepository<WebhookRegistration> {

   public List<WebhookRegistration> findByOperationId(String operationId, Page page, Sort sort) {
      return find("operationId", sort, operationId).page(page).list();
   }

   public List<WebhookRegistration> findByFrequency(Long frequency) {
      return list("frequency", frequency);
   }

   public long countByOperationId(String operationId) {
      return count("operationId", operationId);
   }
}
