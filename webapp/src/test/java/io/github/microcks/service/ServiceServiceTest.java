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

import io.github.microcks.domain.*;
import io.github.microcks.repository.GenericResourceRepository;
import io.github.microcks.repository.RepositoryTestsConfiguration;
import io.github.microcks.repository.RequestRepository;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.EntityAlreadyExistsException;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.MockRepositoryImportException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Test case for ServiceService class.
 * @author laurent
 */
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = RepositoryTestsConfiguration.class)
@TestPropertySource(locations = { "classpath:/config/test.properties" })
public class ServiceServiceTest {

   @Autowired
   private ServiceService service;

   @Autowired
   private ServiceRepository repository;

   @Autowired
   private ResourceRepository resourceRepository;

   @Autowired
   private GenericResourceRepository genericResourceRepository;

   @Autowired
   private RequestRepository requestRepository;

   @Autowired
   private ResponseRepository responseRepository;

   @Test
   public void testImportServiceDefinition() {
      List<Service> services = null;
      try {
         File artifactFile = new File("target/test-classes/io/github/microcks/service/weather-forecast-openapi.yaml");
         services = service.importServiceDefinition(artifactFile, null,
               new ArtifactInfo("weather-forecast-openapi.yaml", true));
      } catch (MockRepositoryImportException mrie) {
         mrie.printStackTrace();
         fail("No MockRepositoryImportException should have be thrown");
      }

      assertNotNull(services);
      assertEquals(1, services.size());

      // Inspect Service own attributes.
      Service importedSvc = services.get(0);
      assertEquals("WeatherForecast API", importedSvc.getName());
      assertEquals("1.0.0", importedSvc.getVersion());
      assertEquals("weather-forecast-openapi.yaml", importedSvc.getSourceArtifact());
      assertNotNull(importedSvc.getMetadata());
      assertEquals(1, importedSvc.getOperations().size());
      assertEquals("GET /forecast/{region}", importedSvc.getOperations().get(0).getName());
      assertEquals(5, importedSvc.getOperations().get(0).getResourcePaths().size());

      // Inspect and check resources.
      List<Resource> resources = resourceRepository.findByServiceId(importedSvc.getId());
      assertEquals(1, resources.size());

      Resource resource = resources.get(0);
      assertEquals("WeatherForecast API-1.0.0.yaml", resource.getName());
      assertEquals("weather-forecast-openapi.yaml", resource.getSourceArtifact());

      // Inspect and check requests.
      List<Request> requests = requestRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, importedSvc.getOperations().get(0)));
      assertEquals(5, requests.size());
      for (Request request : requests) {
         assertEquals("weather-forecast-openapi.yaml", request.getSourceArtifact());
      }

      // Inspect and check responses.
      List<Response> responses = responseRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, importedSvc.getOperations().get(0)));
      assertEquals(5, responses.size());
      for (Response response : responses) {
         assertEquals("weather-forecast-openapi.yaml", response.getSourceArtifact());
      }
   }

   @Test
   public void testImportServiceDefinitionFromGitLabURL() {
      List<Service> services = null;
      try {
         services = service.importServiceDefinition(
               "https://gitlab.com/api/v4/projects/53583367/repository/files/complex-example%2Fopenapi.yaml/raw?head=main",
               null, true, true);
      } catch (MockRepositoryImportException mrie) {
         fail("No MockRepositoryImportException should have be thrown");
      }

      assertNotNull(services);
      assertEquals(1, services.size());

      // Inspect Service own attributes.
      Service importedSvc = services.get(0);
      assertEquals("OpenAPI Car API", importedSvc.getName());
      assertEquals("1.0.0", importedSvc.getVersion());
      assertEquals("openapi.yaml", importedSvc.getSourceArtifact());

      // Inspect and check resources.
      List<Resource> resources = resourceRepository.findByServiceId(importedSvc.getId());
      assertEquals(10, resources.size());

      // Now inspect operations.
      assertEquals(1, importedSvc.getOperations().size());
      assertEquals("GET /owner/{owner}/car", importedSvc.getOperations().get(0).getName());
      assertEquals(DispatchStyles.URI_PARTS, importedSvc.getOperations().get(0).getDispatcher());
      assertEquals(3, importedSvc.getOperations().get(0).getResourcePaths().size());

      // Inspect and check requests.
      List<Request> requests = requestRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, importedSvc.getOperations().get(0)));
      assertEquals(3, requests.size());
      for (Request request : requests) {
         assertEquals("openapi.yaml", request.getSourceArtifact());
      }

      // Inspect and check responses.
      List<Response> responses = responseRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, importedSvc.getOperations().get(0)));
      assertEquals(3, responses.size());
      for (Response response : responses) {
         assertEquals("openapi.yaml", response.getSourceArtifact());
         switch (response.getName()) {
            case "laurent":
               assertEquals("/owner=0", response.getDispatchCriteria());
               assertEquals("[{\"model\":\"BMW X5\",\"year\":2018},{\"model\":\"Tesla Model 3\",\"year\":2020}]",
                     response.getContent());
               break;
            case "maxime":
               assertEquals("/owner=1", response.getDispatchCriteria());
               assertEquals("[{\"model\":\"Volkswagen Golf\",\"year\":2017}]", response.getContent());
               break;
            case "NOT_FOUND":
               assertEquals("/owner=999999999", response.getDispatchCriteria());
               assertEquals("{\"error\":\"Could not find owner\"}", response.getContent());
               break;
            default:
               fail("Unknown response message");
         }
      }
   }

   @Test
   public void testImportServiceDefinitionFromGitLabURL2() {
      List<String> resourceNames = List.of("OpenAPI Car API-1.0.0.yaml",
            "OpenAPI Car API-1.0.0-paths-owner--owner--path.yaml",
            "OpenAPI Car API-1.0.0-paths-components-schemas-Error.yaml",
            "OpenAPI Car API-1.0.0-paths-components-schemas-Owner.yaml",
            "OpenAPI Car API-1.0.0-paths-components-schemas-Car.yaml",
            "OpenAPI Car API-1.0.0-paths-components-parameters-path-owner.yaml",
            "OpenAPI Car API-1.0.0-paths-owner--owner--get-404-response.yaml",
            "OpenAPI Car API-1.0.0-paths-owner--owner--get-404-examples-NOT_FOUND.yaml",
            "OpenAPI Car API-1.0.0-paths-owner--owner--get-200-response.yaml",
            "OpenAPI Car API-1.0.0-paths-owner--owner--get-200-examples-maxime.yaml",
            "OpenAPI Car API-1.0.0-paths-owner--owner--get-200-examples-laurent.yaml",
            "OpenAPI Car API-1.0.0-paths-owner--owner--car-path.yaml",
            "OpenAPI Car API-1.0.0-paths-owner--owner--car-get-404-response.yaml",
            "OpenAPI Car API-1.0.0-paths-owner--owner--car-get-404-examples-NOT_FOUND.yaml",
            "OpenAPI Car API-1.0.0-paths-owner--owner--car-get-200-response.yaml",
            "OpenAPI Car API-1.0.0-paths-owner--owner--car-get-200-examples-maxime.yaml",
            "OpenAPI Car API-1.0.0-paths-owner--owner--car-get-200-examples-laurent.yaml");
      List<Service> services = null;
      try {
         services = service.importServiceDefinition(
               "https://gitlab.com/api/v4/projects/53583367/repository/files/complex-example-2%2Fopenapi.yaml/raw?head=main",
               null, true, true);
      } catch (MockRepositoryImportException mrie) {
         fail("No MockRepositoryImportException should have be thrown");
      }

      assertNotNull(services);
      assertEquals(1, services.size());

      // Inspect Service own attributes.
      Service importedSvc = services.get(0);
      assertEquals("OpenAPI Car API", importedSvc.getName());
      assertEquals("1.0.0", importedSvc.getVersion());
      assertEquals("openapi.yaml", importedSvc.getSourceArtifact());

      // Inspect and check resources.
      List<Resource> resources = resourceRepository.findByServiceId(importedSvc.getId());
      assertEquals(resourceNames.size(), resources.size());
      for (Resource resource : resources) {
         assertEquals("openapi.yaml", resource.getSourceArtifact());
         assertTrue(resourceNames.contains(resource.getName()));
      }

      // Now inspect operations.
      assertEquals(2, importedSvc.getOperations().size());
      for (Operation operation : importedSvc.getOperations()) {
         assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
         assertEquals(3, operation.getResourcePaths().size());

         // Inspect and check requests and responses.
         List<Request> requests = requestRepository
               .findByOperationId(IdBuilder.buildOperationId(importedSvc, operation));
         List<Response> responses = responseRepository
               .findByOperationId(IdBuilder.buildOperationId(importedSvc, operation));

         switch (operation.getName()) {
            case "GET /owner/{owner}":
               for (Response response : responses) {
                  assertEquals("openapi.yaml", response.getSourceArtifact());
                  switch (response.getName()) {
                     case "laurent":
                        assertEquals("/owner=0", response.getDispatchCriteria());
                        assertEquals("{\"name\":\"Laurent\"}", response.getContent());
                        break;
                     case "maxime":
                        assertEquals("/owner=1", response.getDispatchCriteria());
                        assertEquals("{\"name\":\"Maxime\"}", response.getContent());
                        break;
                     case "NOT_FOUND":
                        assertEquals("/owner=999999999", response.getDispatchCriteria());
                        assertEquals("{\"error\":\"Could not find owner\"}", response.getContent());
                        break;
                     default:
                        fail("Unknown response message");
                  }
               }
               break;
            case "GET /owner/{owner}/car":
               for (Response response : responses) {
                  assertEquals("openapi.yaml", response.getSourceArtifact());
                  switch (response.getName()) {
                     case "laurent":
                        assertEquals("/owner=0", response.getDispatchCriteria());
                        assertEquals(
                              "[{\"model\":\"BMW X5\",\"year\":2018},{\"model\":\"Tesla Model 3\",\"year\":2020}]",
                              response.getContent());
                        break;
                     case "maxime":
                        assertEquals("/owner=1", response.getDispatchCriteria());
                        assertEquals("[{\"model\":\"Volkswagen Golf\",\"year\":2017}]", response.getContent());
                        break;
                     case "NOT_FOUND":
                        assertEquals("/owner=999999999", response.getDispatchCriteria());
                        assertEquals("{\"error\":\"Could not find owner\"}", response.getContent());
                        break;
                     default:
                        fail("Unknown response message");
                  }
               }
               break;
            default:
               fail("Unknown operation");
         }
      }

   }

   @Test
   public void testImportServiceDefinitionMainAndSecondary() {
      List<Service> services = null;
      try {
         File artifactFile = new File(
               "target/test-classes/io/github/microcks/service/weather-forecast-raw-openapi.yaml");
         services = service.importServiceDefinition(artifactFile, null,
               new ArtifactInfo("weather-forecast-raw-openapi.yaml", true));
      } catch (MockRepositoryImportException mrie) {
         mrie.printStackTrace();
         fail("No MockRepositoryImportException should have be thrown");
      }

      assertNotNull(services);
      assertEquals(1, services.size());

      // Inspect Service own attributes.
      Service importedSvc = services.get(0);
      assertEquals("WeatherForecast API", importedSvc.getName());
      assertEquals("1.1.0", importedSvc.getVersion());
      assertEquals("weather-forecast-raw-openapi.yaml", importedSvc.getSourceArtifact());
      assertNotNull(importedSvc.getMetadata());
      assertEquals(1, importedSvc.getOperations().size());
      assertNull(importedSvc.getOperations().get(0).getResourcePaths());

      // Inspect and check resources.
      List<Resource> resources = resourceRepository.findByServiceId(importedSvc.getId());
      assertEquals(1, resources.size());

      Resource resource = resources.get(0);
      assertEquals("WeatherForecast API-1.1.0.yaml", resource.getName());
      assertEquals("weather-forecast-raw-openapi.yaml", resource.getSourceArtifact());

      // Inspect and check requests.
      List<Request> requests = requestRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, importedSvc.getOperations().get(0)));
      assertEquals(0, requests.size());

      // Inspect and check responses.
      List<Response> responses = responseRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, importedSvc.getOperations().get(0)));
      assertEquals(0, responses.size());

      try {
         File artifactFile = new File("target/test-classes/io/github/microcks/service/weather-forecast-postman.json");
         services = service.importServiceDefinition(artifactFile, null,
               new ArtifactInfo("weather-forecast-postman.json", false));
      } catch (MockRepositoryImportException mrie) {
         mrie.printStackTrace();
         fail("No MockRepositoryImportException should have be thrown");
      }

      // Inspect Service own attributes.
      importedSvc = services.get(0);
      assertEquals("WeatherForecast API", importedSvc.getName());
      assertEquals("1.1.0", importedSvc.getVersion());
      assertEquals("weather-forecast-raw-openapi.yaml", importedSvc.getSourceArtifact());
      assertNotNull(importedSvc.getMetadata());
      assertEquals(1, importedSvc.getOperations().size());
      assertEquals(DispatchStyles.URI_ELEMENTS, importedSvc.getOperations().get(0).getDispatcher());
      assertEquals(5, importedSvc.getOperations().get(0).getResourcePaths().size());

      // Inspect and check resources.
      resources = resourceRepository.findByServiceId(importedSvc.getId());
      assertEquals(2, resources.size());

      for (Resource resourceItem : resources) {
         switch (resourceItem.getType()) {
            case OPEN_API_SPEC:
               assertEquals("WeatherForecast API-1.1.0.yaml", resourceItem.getName());
               assertEquals("weather-forecast-raw-openapi.yaml", resourceItem.getSourceArtifact());
               break;
            case POSTMAN_COLLECTION:
               assertEquals("WeatherForecast API-1.1.0.json", resourceItem.getName());
               assertEquals("weather-forecast-postman.json", resourceItem.getSourceArtifact());
               break;
            default:
               fail("Unexpected resource type: " + resourceItem.getType());
         }
      }

      // Inspect and check requests.
      requests = requestRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, importedSvc.getOperations().get(0)));
      assertEquals(5, requests.size());
      for (Request request : requests) {
         assertEquals("weather-forecast-postman.json", request.getSourceArtifact());
      }

      // Inspect and check responses.
      responses = responseRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, importedSvc.getOperations().get(0)));
      assertEquals(5, requests.size());
      for (Response response : responses) {
         assertEquals("weather-forecast-postman.json", response.getSourceArtifact());
      }
   }

   @Test
   public void testImportServiceDefinitionMainGraphQLAndSecondaryPostman() {
      List<Service> services = null;
      try {
         File artifactFile = new File("target/test-classes/io/github/microcks/util/graphql/films.graphql");
         services = service.importServiceDefinition(artifactFile, null, new ArtifactInfo("films.graphql", true));
      } catch (MockRepositoryImportException mrie) {
         mrie.printStackTrace();
         fail("No MockRepositoryImportException should have be thrown");
      }

      assertNotNull(services);
      assertEquals(1, services.size());

      // Inspect Service own attributes.
      Service importedSvc = services.get(0);
      assertEquals("Movie Graph API", importedSvc.getName());
      assertEquals("1.0", importedSvc.getVersion());
      assertEquals("films.graphql", importedSvc.getSourceArtifact());
      assertNotNull(importedSvc.getMetadata());
      assertEquals(4, importedSvc.getOperations().size());

      // Inspect and check requests.
      List<Request> requests = requestRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, importedSvc.getOperations().get(0)));
      assertEquals(0, requests.size());

      // Inspect and check responses.
      List<Response> responses = responseRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, importedSvc.getOperations().get(0)));
      assertEquals(0, responses.size());

      try {
         File artifactFile = new File("target/test-classes/io/github/microcks/util/graphql/films-postman.json");
         services = service.importServiceDefinition(artifactFile, null, new ArtifactInfo("films-postman.json", false));
      } catch (MockRepositoryImportException mrie) {
         mrie.printStackTrace();
         fail("No MockRepositoryImportException should have be thrown");
      }

      // Inspect Service own attributes.
      importedSvc = services.get(0);
      assertEquals("Movie Graph API", importedSvc.getName());
      assertEquals("1.0", importedSvc.getVersion());
      assertEquals("films.graphql", importedSvc.getSourceArtifact());
      assertNotNull(importedSvc.getMetadata());
      assertEquals(4, importedSvc.getOperations().size());

      // Inspect and check requests.
      requests = requestRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, importedSvc.getOperations().get(0)));
      assertEquals(1, requests.size());
      for (Request request : requests) {
         assertEquals("films-postman.json", request.getSourceArtifact());
      }

      // Inspect and check responses.
      responses = responseRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, importedSvc.getOperations().get(0)));
      assertEquals(1, requests.size());
      for (Response response : responses) {
         assertEquals("films-postman.json", response.getSourceArtifact());
      }
   }

   @Test
   public void testImportServiceDefinitionMainAndSecondariesWithAPIMetadata() {
      List<Service> services = null;
      try {
         File artifactFile = new File(
               "target/test-classes/io/github/microcks/service/weather-forecast-raw-openapi.yaml");
         services = service.importServiceDefinition(artifactFile, null,
               new ArtifactInfo("weather-forecast-raw-openapi.yaml", true));
      } catch (MockRepositoryImportException mrie) {
         mrie.printStackTrace();
         fail("No MockRepositoryImportException should have be thrown");
      }

      try {
         File artifactFile = new File("target/test-classes/io/github/microcks/service/weather-forecast-postman.json");
         services = service.importServiceDefinition(artifactFile, null,
               new ArtifactInfo("weather-forecast-postman.json", false));
      } catch (MockRepositoryImportException mrie) {
         mrie.printStackTrace();
         fail("No MockRepositoryImportException should have be thrown");
      }

      try {
         File artifactFile = new File("target/test-classes/io/github/microcks/service/weather-forecast-metadata.yaml");
         services = service.importServiceDefinition(artifactFile, null,
               new ArtifactInfo("weather-forecast-metadata.yaml", false));
      } catch (MockRepositoryImportException mrie) {
         mrie.printStackTrace();
         fail("No MockRepositoryImportException should have be thrown");
      }

      // Inspect Service own attributes.
      Service importedSvc = services.get(0);
      assertEquals("WeatherForecast API", importedSvc.getName());
      assertEquals("1.1.0", importedSvc.getVersion());
      assertEquals("weather-forecast-raw-openapi.yaml", importedSvc.getSourceArtifact());
      assertNotNull(importedSvc.getMetadata());

      assertEquals(3, importedSvc.getMetadata().getLabels().size());
      assertEquals("weather", importedSvc.getMetadata().getLabels().get("domain"));
      assertEquals("GA", importedSvc.getMetadata().getLabels().get("status"));
      assertEquals("Team C", importedSvc.getMetadata().getLabels().get("team"));

      assertEquals(1, importedSvc.getOperations().size());
      assertEquals(100, importedSvc.getOperations().get(0).getDefaultDelay().longValue());
      assertEquals(DispatchStyles.FALLBACK, importedSvc.getOperations().get(0).getDispatcher());
      assertNotNull(importedSvc.getOperations().get(0).getDispatcherRules());
      assertEquals(5, importedSvc.getOperations().get(0).getResourcePaths().size());
   }

   @Test
   public void testImportServiceDefinitionMainGraphQLAndSecondaryHAR() {
      List<Service> services = null;
      try {
         File artifactFile = new File("target/test-classes/io/github/microcks/util/graphql/films.graphql");
         services = service.importServiceDefinition(artifactFile, null, new ArtifactInfo("films.graphql", true));
      } catch (MockRepositoryImportException mrie) {
         fail("No MockRepositoryImportException should have be thrown");
      }

      // Inspect Service own attributes.
      Service importedSvc = services.get(0);
      assertEquals("Movie Graph API", importedSvc.getName());
      assertEquals("1.0", importedSvc.getVersion());
      assertEquals("films.graphql", importedSvc.getSourceArtifact());
      assertNotNull(importedSvc.getMetadata());
      assertEquals(4, importedSvc.getOperations().size());

      Optional<Operation> opFilmOperation = importedSvc.getOperations().stream()
            .filter(op -> op.getName().equals("film")).findFirst();
      if (opFilmOperation.isEmpty()) {
         fail("film operation should have been discovered");
      }
      Operation filmOperation = opFilmOperation.get();

      // Inspect and check requests.
      List<Request> requests = requestRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, filmOperation));
      assertEquals(0, requests.size());

      // Inspect and check responses.
      List<Response> responses = responseRepository
            .findByOperationId(IdBuilder.buildOperationId(importedSvc, filmOperation));
      assertEquals(0, responses.size());

      try {
         File artifactFile = new File("target/test-classes/io/github/microcks/util/har/movie-graph-api-1.0.har");
         services = service.importServiceDefinition(artifactFile, null,
               new ArtifactInfo("movie-graph-api-1.0.har", false));
      } catch (MockRepositoryImportException mrie) {
         fail("No MockRepositoryImportException should have be thrown");
      }

      // Inspect Service own attributes.
      importedSvc = services.get(0);
      assertEquals("Movie Graph API", importedSvc.getName());
      assertEquals("1.0", importedSvc.getVersion());
      assertEquals("films.graphql", importedSvc.getSourceArtifact());
      assertNotNull(importedSvc.getMetadata());
      assertEquals(4, importedSvc.getOperations().size());

      // Inspect and check requests.
      requests = requestRepository.findByOperationId(IdBuilder.buildOperationId(importedSvc, filmOperation));
      assertEquals(1, requests.size());
      for (Request request : requests) {
         assertEquals("movie-graph-api-1.0.har", request.getSourceArtifact());
      }

      // Inspect and check responses.
      responses = responseRepository.findByOperationId(IdBuilder.buildOperationId(importedSvc, filmOperation));
      assertEquals(1, requests.size());
      for (Response response : responses) {
         assertEquals("movie-graph-api-1.0.har", response.getSourceArtifact());
      }
   }

   @Test
   public void testCreateGenericResourceService() {
      Service created = null;
      try {
         created = service.createGenericResourceService("Order Service", "1.0", "order", null);
      } catch (Exception e) {
         fail("No exception should be thrown");
      }

      // Check created object.
      assertNotNull(created.getId());

      // Retrieve object by id and assert on what has been persisted.
      Service retrieved = repository.findById(created.getId()).orElse(null);
      assertEquals("Order Service", retrieved.getName());
      assertEquals("1.0", retrieved.getVersion());
      assertEquals(ServiceType.GENERIC_REST, retrieved.getType());

      // Now check operations.
      assertEquals(5, retrieved.getOperations().size());
      for (Operation op : retrieved.getOperations()) {
         if ("POST /order".equals(op.getName())) {
            assertEquals("POST", op.getMethod());
         } else if ("GET /order/:id".equals(op.getName())) {
            assertEquals("GET", op.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, op.getDispatcher());
            assertEquals("id", op.getDispatcherRules());
         } else if ("GET /order".equals(op.getName())) {
            assertEquals("GET", op.getMethod());
         } else if ("PUT /order/:id".equals(op.getName())) {
            assertEquals("PUT", op.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, op.getDispatcher());
            assertEquals("id", op.getDispatcherRules());
         } else if ("DELETE /order/:id".equals(op.getName())) {
            assertEquals("DELETE", op.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, op.getDispatcher());
            assertEquals("id", op.getDispatcherRules());
         } else {
            fail("Unknown operation name: " + op.getName());
         }
      }
   }

   @Test(expected = EntityAlreadyExistsException.class)
   public void testCreateGenericResourceServiceFailure() throws EntityAlreadyExistsException {
      try {
         Service first = service.createGenericResourceService("Order Service", "1.0", "order", null);
      } catch (Exception e) {
         fail("No exception should be raised on first save()!");
      }
      Service second = service.createGenericResourceService("Order Service", "1.0", "order", null);
   }

   @Test
   public void testCreateGenericResourceServiceWithReference() {
      Service created = null;
      try {
         created = service.createGenericResourceService("Order Service", "1.0", "order",
               "{\"customerId\": \"123456789\", \"amount\": 12.5}");
      } catch (Exception e) {
         fail("No exception should be thrown");
      }

      // Check created object.
      assertNotNull(created.getId());

      // Check that service has created a reference generic resource.
      List<GenericResource> resources = genericResourceRepository.findReferencesByServiceId(created.getId());
      assertNotNull(resources);
      assertEquals(1, resources.size());

      GenericResource resource = resources.get(0);
      assertTrue(resource.isReference());
      assertEquals("123456789", resource.getPayload().get("customerId"));
      assertEquals(12.5, resource.getPayload().get("amount"));
   }

   @Test
   public void testCreateGenericEventServiceWithReference() {
      Service created = null;
      try {
         created = service.createGenericEventService("Order Service", "2.0", "order",
               "{\"customerId\": \"123456789\",\n \"amount\": 12.5}");
      } catch (Exception e) {
         fail("No exception should be thrown");
      }

      // Check created object.
      assertNotNull(created.getId());

      // Retrieve object by id and assert on what has been persisted.
      Service retrieved = repository.findById(created.getId()).orElse(null);
      assertEquals("Order Service", retrieved.getName());
      assertEquals("2.0", retrieved.getVersion());
      assertEquals(ServiceType.GENERIC_EVENT, retrieved.getType());
      assertEquals(1, retrieved.getOperations().size());

      List<Resource> resources = resourceRepository.findByServiceId(retrieved.getId());
      assertEquals(1, resources.size());

      Resource resource = resources.get(0);
      assertEquals("order-asyncapi.yaml", resource.getName());
      assertEquals(ResourceType.ASYNC_API_SPEC, resource.getType());
      assertNotNull(resource.getContent());
      assertTrue(resource.getContent().contains("payload: {\"customerId\": \"123456789\", \"amount\": 12.5}"));
   }
}
