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
package io.github.microcks.util.graphql;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for GraphQLImporter class.
 * @author laurent
 */
class GraphQLImporterTest {

   @Test
   void testSimpleGraphQLImport() {
      GraphQLImporter importer = null;
      try {
         importer = new GraphQLImporter("target/test-classes/io/github/microcks/util/graphql/films.graphql");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Service definition import should not fail");
      }
      assertEquals(1, services.size());

      Service service = services.get(0);
      assertEquals("Movie Graph API", service.getName());
      assertEquals(ServiceType.GRAPHQL, service.getType());
      assertEquals("1.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = null;
      try {
         resources = importer.getResourceDefinitions(service);
      } catch (MockRepositoryImportException mrie) {
         fail("Resource definition import should not fail");
      }
      assertEquals(1, resources.size());

      Resource resource = resources.get(0);
      assertEquals(ResourceType.GRAPHQL_SCHEMA, resource.getType());
      assertEquals("Movie Graph API-1.0.graphql", resource.getName());

      // Check that operations and input/output have been found.
      assertEquals(4, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         if ("allFilms".equals(operation.getName())) {
            assertEquals("QUERY", operation.getMethod());
            assertEquals("FilmsConnection", operation.getOutputName());
            assertNull(operation.getDispatcher());
         } else if ("film".equals(operation.getName())) {
            assertEquals("QUERY", operation.getMethod());
            assertEquals("Film", operation.getOutputName());
            assertEquals("String", operation.getInputName());
            assertEquals(DispatchStyles.QUERY_ARGS, operation.getDispatcher());
            assertEquals("id", operation.getDispatcherRules());
         } else if ("addStar".equals(operation.getName())) {
            assertEquals("MUTATION", operation.getMethod());
            assertEquals("Film", operation.getOutputName());
            assertEquals("String", operation.getInputName());
            assertEquals(DispatchStyles.QUERY_ARGS, operation.getDispatcher());
            assertEquals("filmId", operation.getDispatcherRules());
         } else if ("addReview".equals(operation.getName())) {
            assertEquals("MUTATION", operation.getMethod());
            assertEquals("Film", operation.getOutputName());
            assertEquals("String, Review", operation.getInputName());
            assertNull(operation.getDispatcher());
         } else {
            fail("Unknown operation");
         }
      }
   }
}
