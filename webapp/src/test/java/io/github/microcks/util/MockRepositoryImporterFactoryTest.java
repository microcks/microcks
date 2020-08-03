/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.util;

import io.github.microcks.util.asyncapi.AsyncAPIImporter;
import io.github.microcks.util.openapi.OpenAPIImporter;
import io.github.microcks.util.postman.PostmanCollectionImporter;
import io.github.microcks.util.soapui.SoapUIProjectImporter;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
/**
 * This is a test case for MockRepositoryImporterFactory.
 * @author laurent
 */
public class MockRepositoryImporterFactoryTest {

   @Test
   public void testGetMockRepositoryImporter() {

      // Load a SoapUI file.
      File soapUIProject = new File("../samples/HelloService-soapui-project.xml");
      MockRepositoryImporter importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(soapUIProject);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof SoapUIProjectImporter);

      // Load a Postman file.
      File postmanCollection = new File("../samples/PetstoreAPI-collection.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(postmanCollection);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof PostmanCollectionImporter);

      // Load an OpenAPI YAML file.
      importer = null;
      File openAPISpec = new File("target/test-classes/io/github/microcks/util/openapi/cars-openapi.yaml");
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(openAPISpec);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof OpenAPIImporter);

      // Load an OpenAPI JSON file.
      openAPISpec = new File("target/test-classes/io/github/microcks/util/openapi/cars-openapi.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(openAPISpec);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof OpenAPIImporter);

      // Load an AsyncAPI YAML file.
      File asyncAPISpec = new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi.yaml");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(asyncAPISpec);
      } catch (Throwable t) {
         fail("Getting importer should not fail !");
      }
      assertTrue(importer instanceof AsyncAPIImporter);
   }
}
