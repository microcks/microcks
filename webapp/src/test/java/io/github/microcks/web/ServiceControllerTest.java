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

import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.service.MessageService;
import io.github.microcks.service.ServiceService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ServiceController search and count endpoints.
 */
@ExtendWith(MockitoExtension.class)
class ServiceControllerTest {

   @Mock
   private ServiceService serviceService;

   @Mock
   private ServiceRepository serviceRepository;

   @Mock
   private MessageService messageService;

   @InjectMocks
   private ServiceController controller;

   @Test
   void testSearchServicesWithoutPagination() {
      Map<String, String> queryMap = new HashMap<>();
      queryMap.put("name", "TestAPI");
      queryMap.put("labels.domain", "finance");
      queryMap.put("version", "1.0.0");
      queryMap.put("type", "REST");

      Service service = new Service();
      service.setName("TestAPI");
      service.setVersion("1.0.0");
      service.setType(ServiceType.REST);

      when(serviceRepository.search(eq("TestAPI"), eq(Map.of("domain", "finance")), eq("1.0.0"), eq(ServiceType.REST),
            any(Sort.class))).thenReturn(List.of(service));

      List<Service> result = controller.searchServices(queryMap);

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("TestAPI", result.get(0).getName());

      ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
      verify(serviceRepository).search(eq("TestAPI"), eq(Map.of("domain", "finance")), eq("1.0.0"),
            eq(ServiceType.REST), sortCaptor.capture());
      Sort sort = sortCaptor.getValue();
      assertNotNull(sort.getOrderFor("name"));
      assertEquals(Sort.Direction.ASC, sort.getOrderFor("name").getDirection());
      assertNotNull(sort.getOrderFor("version"));
      assertEquals(Sort.Direction.ASC, sort.getOrderFor("version").getDirection());
   }

   @Test
   void testSearchServicesWithPaginationAndSorting() {
      Map<String, String> queryMap = new HashMap<>();
      queryMap.put("name", "TestAPI");
      queryMap.put("page", "1");
      queryMap.put("size", "10");
      queryMap.put("sort", "metadata.lastUpdate,desc");

      when(serviceRepository.search(eq("TestAPI"), eq(Collections.emptyMap()), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(Collections.emptyList());

      List<Service> result = controller.searchServices(queryMap);

      assertNotNull(result);

      ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
      verify(serviceRepository).search(eq("TestAPI"), eq(Collections.emptyMap()), isNull(), isNull(),
            pageableCaptor.capture());
      Pageable pageable = pageableCaptor.getValue();
      assertEquals(1, pageable.getPageNumber());
      assertEquals(10, pageable.getPageSize());
      assertNotNull(pageable.getSort().getOrderFor("metadata.lastUpdate"));
      assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("metadata.lastUpdate").getDirection());
   }

   @Test
   void testSearchServicesPageDefaults() {
      // Test when only page is provided -> size defaults to 20
      Map<String, String> queryMap = new HashMap<>();
      queryMap.put("page", "2");

      when(serviceRepository.search(isNull(), eq(Collections.emptyMap()), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(Collections.emptyList());

      controller.searchServices(queryMap);

      ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
      verify(serviceRepository).search(isNull(), eq(Collections.emptyMap()), isNull(), isNull(),
            pageableCaptor.capture());
      Pageable pageable = pageableCaptor.getValue();
      assertEquals(2, pageable.getPageNumber());
      assertEquals(20, pageable.getPageSize());

      // Test when only size is provided -> page defaults to 0
      queryMap = new HashMap<>();
      queryMap.put("size", "5");

      controller.searchServices(queryMap);

      pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
      verify(serviceRepository, times(2)).search(isNull(), eq(Collections.emptyMap()), isNull(), isNull(),
            pageableCaptor.capture());
      pageable = pageableCaptor.getValue();
      assertEquals(0, pageable.getPageNumber());
      assertEquals(5, pageable.getPageSize());
   }

   @Test
   void testSearchServicesInvalidSortFallsBackToDefault() {
      Map<String, String> queryMap = new HashMap<>();
      queryMap.put("sort", "unsupportedProperty,desc");

      when(serviceRepository.search(isNull(), eq(Collections.emptyMap()), isNull(), isNull(), any(Sort.class)))
            .thenReturn(Collections.emptyList());

      controller.searchServices(queryMap);

      ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
      verify(serviceRepository).search(isNull(), eq(Collections.emptyMap()), isNull(), isNull(), sortCaptor.capture());
      Sort sort = sortCaptor.getValue();
      assertNotNull(sort.getOrderFor("name"));
      assertNotNull(sort.getOrderFor("version"));
      assertNull(sort.getOrderFor("unsupportedProperty"));
   }

   @Test
   void testSearchServicesWithEmptyQueryMap() {
      // Verifies the bug reported in #2298 comments where empty queryMap caused IllegalArgumentException
      when(serviceRepository.search(isNull(), eq(Collections.emptyMap()), isNull(), isNull(), any(Sort.class)))
            .thenReturn(List.of(new Service(), new Service()));

      List<Service> result = controller.searchServices(Collections.emptyMap());

      assertNotNull(result);
      assertEquals(2, result.size());
      verify(serviceRepository).search(isNull(), eq(Collections.emptyMap()), isNull(), isNull(), any(Sort.class));
   }

   @Test
   void testSearchServicesWithBlankValuesIgnored() {
      Map<String, String> queryMap = new HashMap<>();
      queryMap.put("name", "   ");
      queryMap.put("version", "");
      queryMap.put("type", "  ");
      queryMap.put("labels.domain", "  ");

      when(serviceRepository.search(isNull(), eq(Collections.emptyMap()), isNull(), isNull(), any(Sort.class)))
            .thenReturn(Collections.emptyList());

      List<Service> result = controller.searchServices(queryMap);

      assertNotNull(result);
      verify(serviceRepository).search(isNull(), eq(Collections.emptyMap()), isNull(), isNull(), any(Sort.class));
   }

   @Test
   void testCountSearchServices() {
      Map<String, String> queryMap = new HashMap<>();
      queryMap.put("name", "TestAPI");
      queryMap.put("labels.domain", "finance");
      queryMap.put("version", "1.0.0");
      queryMap.put("type", "REST");

      when(serviceRepository.countSearch("TestAPI", Map.of("domain", "finance"), "1.0.0", ServiceType.REST))
            .thenReturn(5L);

      Map<String, Long> result = controller.countSearchServices(queryMap);

      assertNotNull(result);
      assertTrue(result.containsKey("counter"));
      assertEquals(5L, result.get("counter"));
      verify(serviceRepository).countSearch("TestAPI", Map.of("domain", "finance"), "1.0.0", ServiceType.REST);
   }
}
