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

import io.microcks.domain.TestResult;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Date;
import java.util.List;

/**
 * Repository for {@code TestResult} domain objects.
 * @author laurent
 */
@ApplicationScoped
public class TestResultRepository implements ObjectIdRepository<TestResult> {

   public List<TestResult> findByServiceId(String serviceId) {
      return list("serviceId", serviceId);
   }

   public List<TestResult> findByServiceId(String serviceId, Page page, Sort sort) {
      return find("serviceId", sort, serviceId).page(page).list();
   }

   public List<TestResult> findAllWithTestDateAfter(Date date) {
      return list("{'testDate': {'$gt': ?1}}", date);
   }

   public long countByServiceId(String serviceId) {
      return count("serviceId", serviceId);
   }
}
