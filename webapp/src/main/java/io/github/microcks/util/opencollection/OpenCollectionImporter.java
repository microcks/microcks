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
package io.github.microcks.util.opencollection;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Service;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.util.ObjectMapperFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * An implementation of MockRepositoryImporter that deals with OpenCollection specification files.
 * @author SebastienDegodez
 */
public class OpenCollectionImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(OpenCollectionImporter.class);

   /**
    * Build a new importer.
    * @param collectionFilePath The path to OpenCollection file
    * @throws IOException if project file cannot be found or read.
    */
   public OpenCollectionImporter(String collectionFilePath) throws IOException {
      try {
         byte[] yamlBytes = Files.readAllBytes(Paths.get(collectionFilePath));
         ObjectMapperFactory.getYamlObjectMapper().readTree(yamlBytes);
      } catch (Exception e) {
         log.error("Exception while parsing OpenCollection file {}", collectionFilePath, e);
         throw new IOException("OpenCollection file parsing error");
      }
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      return null;
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) throws MockRepositoryImportException {
      return null;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      return null;
   }
}
