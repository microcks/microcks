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
package io.github.microcks.util.graphql;

import io.github.microcks.domain.*;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;

import io.github.microcks.util.ReferenceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of MockRepositoryImporter that deals with GraphQL Schema documents.
 * @author laurent
 */
public class GraphQLImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(MockRepositoryImporter.class);

   private String specContent;

   /**
    * Build a new importer.
    * @param graphqlFilePath The path to local GraphQL schema file
    * @throws IOException if project file cannot be found or read.
    */
   public GraphQLImporter(String graphqlFilePath) throws IOException {
      try {
         // Read spec bytes.
         byte[] bytes = Files.readAllBytes(Paths.get(graphqlFilePath));
         specContent = new String(bytes, Charset.forName("UTF-8"));
      }  catch (Exception e) {
         log.error("Exception while parsing GraphQL schema file " + graphqlFilePath, e);
         throw new IOException("GraphQL schema file parsing error");
      }
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> results = new ArrayList<>();

      return results;
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) throws MockRepositoryImportException {
      List<Resource> results = new ArrayList<>();

      Resource graphqlSchema = new Resource();
      graphqlSchema.setName(service.getName() + "-" + service.getVersion() + ".graphql");
      graphqlSchema.setType(ResourceType.GRAPHQL_SCHEMA);
      graphqlSchema.setContent(specContent);
      results.add(graphqlSchema);

      return results;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation) throws MockRepositoryImportException {
      List<Exchange> result = new ArrayList<>();
      return result;
   }
}
