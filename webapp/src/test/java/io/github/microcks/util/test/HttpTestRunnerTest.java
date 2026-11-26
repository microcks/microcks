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
package io.github.microcks.util.test;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.TestResult;
import io.github.microcks.domain.TestReturn;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpTestRunnerTest {

   @Test
   void testRunHeadOperation() throws Exception {
      HttpTestRunner runner = new HttpTestRunner();
      runner.setClientHttpRequestFactory((uri, method) -> {
         assertEquals(HttpMethod.HEAD, method);
         assertEquals(URI.create("https://example.test/heartbeat?store=bakery"), uri);
         MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
         request.setResponse(new MockClientHttpResponse(new byte[0], HttpStatus.NO_CONTENT));
         return request;
      });

      Service service = new Service();
      service.setType(ServiceType.REST);
      Operation operation = new Operation();
      operation.setName("HEAD /heartbeat");
      operation.setMethod("  hEaD  ");
      Parameter store = new Parameter();
      store.setName("store");
      store.setValue("bakery");
      Request request = new Request();
      request.addQueryParameter(store);

      List<TestReturn> results = runner.runTest(service, operation, new TestResult(), List.of(request),
            "https://example.test", runner.buildMethod(operation.getMethod()));

      assertEquals(1, results.size());
      assertEquals(TestReturn.SUCCESS_CODE, results.getFirst().getCode());
      assertEquals("204", results.getFirst().getResponse().getStatus());
   }
}
