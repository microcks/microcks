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

import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestResult;
import io.github.microcks.domain.TestRunnerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for TestResultRepository implementation.
 * @author laurent
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringJUnitConfig(classes = RepositoryTestsConfiguration.class)
@TestPropertySource(locations = { "classpath:/config/test.properties" })
class TestResultRepositoryTest {

   @Autowired
   ServiceRepository serviceRepository;

   @Autowired
   TestResultRepository repository;

   String serviceId;

   @BeforeEach
   public void setUp() {
      // Create a service...
      Service service = new Service();
      service.setName("HelloWorld");
      service.setVersion("1.0");
      serviceRepository.save(service);
      // Create a bunch of test results for this service.
      TestResult testResult = new TestResult();
      testResult.setInProgress(false);
      testResult.setRunnerType(TestRunnerType.HTTP);
      testResult.setTestNumber(1L);
      testResult.setServiceId(service.getId());
      testResult.setTestedEndpoint("http://localhost:8088/HelloWorld");
      repository.save(testResult);
      // ... another one.
      testResult = new TestResult();
      testResult.setInProgress(false);
      testResult.setRunnerType(TestRunnerType.HTTP);
      testResult.setTestNumber(2L);
      testResult.setServiceId(service.getId());
      testResult.setTestedEndpoint("http://localhost:8088/HelloWorld");
      repository.save(testResult);
      serviceId = service.getId();
   }

   @Test
   void testFindByService() {
      long count = repository.countByServiceId(serviceId);
      assertEquals(2, count);
      List<TestResult> results = repository.findByServiceId(serviceId);
      assertEquals(2, results.size());
   }

   @Test
   void testFindLastOnesForService() {
      List<TestResult> older = repository.findByServiceId(serviceId,
            PageRequest.of(0, 2, Sort.Direction.DESC, "testNumber"));
      assertEquals(2, older.size());
      Long newTestNumber = older.get(0).getTestNumber() + 1L;
      assertEquals(3L, newTestNumber.longValue());
   }
}
