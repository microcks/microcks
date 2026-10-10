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
package io.github.microcks.web;

import io.github.microcks.domain.ResponseExampleComparisonMode;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestOptionals;
import io.github.microcks.domain.TestResult;
import io.github.microcks.domain.TestRunnerType;
import io.github.microcks.repository.SecretRepository;
import io.github.microcks.repository.TestResultRepository;
import io.github.microcks.service.MessageService;
import io.github.microcks.service.ServiceService;
import io.github.microcks.service.TestService;
import io.github.microcks.web.dto.TestRequestDTO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestControllerTest {

   private static final ObjectMapper MAPPER = new ObjectMapper();

   @Mock
   private TestResultRepository testResultRepository;
   @Mock
   private SecretRepository secretRepository;
   @Mock
   private TestService testService;
   @Mock
   private MessageService messageService;
   @Mock
   private ServiceService serviceService;
   @Mock
   private TestRequestDTO testRequest;

   @InjectMocks
   private TestController controller;

   @Test
   void shouldDeserializeResponseExampleComparison() throws JsonProcessingException {
      TestRequestDTO request = MAPPER.readValue("""
            {
              "serviceId": "service-1",
              "testEndpoint": "https://example.com",
              "runnerType": "OPEN_API_SCHEMA",
              "timeout": 2000,
              "responseExampleComparison": "STRICT"
            }
            """, TestRequestDTO.class);

      assertEquals(ResponseExampleComparisonMode.STRICT, request.getResponseExampleComparison());
   }

   @Test
   void shouldForwardResponseExampleComparisonToTestService() {
      Service service = new Service();
      service.setId("service-1");
      TestResult testResult = new TestResult();

      when(testRequest.getServiceId()).thenReturn(service.getId());
      when(testRequest.getTestEndpoint()).thenReturn("https://example.com");
      when(testRequest.getRunnerType()).thenReturn(TestRunnerType.OPEN_API_SCHEMA.name());
      when(testRequest.getTimeout()).thenReturn(2000L);
      when(testRequest.getResponseExampleComparison()).thenReturn(ResponseExampleComparisonMode.LENIENT);
      when(serviceService.getServiceById(service.getId())).thenReturn(service);
      when(testService.launchTests(eq(service), eq("https://example.com"), eq(TestRunnerType.OPEN_API_SCHEMA),
            any(TestOptionals.class))).thenReturn(testResult);

      ResponseEntity<Object> response = controller.createTest(testRequest);

      ArgumentCaptor<TestOptionals> optionals = ArgumentCaptor.forClass(TestOptionals.class);
      verify(testService).launchTests(eq(service), eq("https://example.com"), eq(TestRunnerType.OPEN_API_SCHEMA),
            optionals.capture());
      assertEquals(ResponseExampleComparisonMode.LENIENT, optionals.getValue().getResponseExampleComparison());
      assertEquals(HttpStatus.CREATED, response.getStatusCode());
      assertEquals(testResult, response.getBody());
   }
}
