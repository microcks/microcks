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
package io.microcks.test;

import io.microcks.domain.Operation;
import io.microcks.domain.Parameter;
import io.microcks.domain.Request;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceType;
import io.microcks.domain.TestResult;
import io.microcks.domain.TestReturn;

import io.microcks.util.http.ClientHttpResponse;
import io.microcks.util.http.HttpHeaders;
import io.microcks.util.http.HttpMethod;
import io.microcks.util.http.HttpStatusCode;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
         HttpHeaders headers = new HttpHeaders();
         return new io.microcks.util.http.ClientHttpRequest() {
            @Override
            public HttpHeaders getHeaders() {
               return headers;
            }

            @Override
            public OutputStream getBody() {
               return new ByteArrayOutputStream();
            }

            @Override
            public ClientHttpResponse execute() {
               return new ClientHttpResponse() {
                  @Override
                  public HttpStatusCode getStatusCode() {
                     return new HttpStatusCode(204);
                  }

                  @Override
                  public HttpHeaders getHeaders() {
                     return new HttpHeaders();
                  }

                  @Override
                  public InputStream getBody() {
                     return new ByteArrayInputStream(new byte[0]);
                  }

                  @Override
                  public void close() {
                     // Nothing to release.
                  }
               };
            }
         };
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
