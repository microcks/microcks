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
package io.github.microcks.service;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.ai.AICopilot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AICopilotRunnerServiceTest {

   @Mock
   private ServiceService serviceService;

   @Mock
   private AICopilot copilot;

   @Test
   void shouldContinueBulkGenerationWhenOperationsFailOrReturnNoExchanges() throws Exception {
      AICopilotRunnerService runnerService = new AICopilotRunnerService(serviceService, Optional.of(copilot));
      Service service = buildService();
      Resource contract = new Resource();

      Operation firstOperation = service.getOperations().get(0);
      Operation secondOperation = service.getOperations().get(1);
      Operation thirdOperation = service.getOperations().get(2);

      when(copilot.suggestSampleExchanges(service, firstOperation, contract, 3)).thenReturn(List.of());
      when(copilot.suggestSampleExchanges(service, secondOperation, contract, 3))
            .thenThrow(new IllegalStateException("Intentional failure"));
      doReturn(List.of(buildExchange())).when(copilot).suggestSampleExchanges(service, thirdOperation, contract, 3);

      CompletableFuture<Boolean> result = runnerService.generateSamplesForService(service, contract, null);

      assertTrue(result.join());
      verify(copilot, times(1)).suggestSampleExchanges(service, firstOperation, contract, 3);
      verify(copilot, times(1)).suggestSampleExchanges(service, secondOperation, contract, 3);
      verify(copilot, times(1)).suggestSampleExchanges(service, thirdOperation, contract, 3);
      verify(serviceService, never()).addAICopilotExchangesToServiceOperation(eq(service.getId()),
            eq(firstOperation.getName()), ArgumentMatchers.<List<Exchange>>any(), eq(null));
      verify(serviceService, never()).addAICopilotExchangesToServiceOperation(eq(service.getId()),
            eq(secondOperation.getName()), ArgumentMatchers.<List<Exchange>>any(), eq(null));
      verify(serviceService, times(1)).addAICopilotExchangesToServiceOperation(eq(service.getId()),
            eq(thirdOperation.getName()), ArgumentMatchers.<List<Exchange>>any(), eq(null));
   }

   private Service buildService() {
      Service service = new Service();
      service.setId("service-id");
      service.setName("Pastry API");
      service.setType(ServiceType.REST);

      Operation firstOperation = new Operation();
      firstOperation.setName("GET /pastries/{name}");

      Operation secondOperation = new Operation();
      secondOperation.setName("POST /pastries");

      Operation thirdOperation = new Operation();
      thirdOperation.setName("DELETE /pastries/{name}");

      service.setOperations(List.of(firstOperation, secondOperation, thirdOperation));
      return service;
   }

   private RequestResponsePair buildExchange() {
      Request request = new Request();
      request.setName("sample");
      request.setContent("{\"name\":\"croissant\"}");

      Response response = new Response();
      response.setName("sample");
      response.setStatus("200");
      response.setContent("{\"status\":\"deleted\"}");

      return new RequestResponsePair(request, response);
   }
}
