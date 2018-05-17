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

import io.github.microcks.util.openapi.OpenAPIImporter;
import io.github.microcks.util.postman.PostmanCollectionImporter;
import io.github.microcks.util.soapui.SoapUIProjectImporter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

/**
 * Factory for building/retieving mock repository importer implementations. For now, it implements
 * a very simple algorithm : if repository is a JSON file (guess on first lines content), it assume repository it
 * implemented as a Postman collection and then uses PostmanCollectionImporter; otherwise it uses SoapUIProjectImporter.
 * @author laurent
 */
public class MockRepositoryImporterFactory {

   /**
    * Create the right MockRepsoitoryImporter implementation depending on repository type.
    * @param mockRepository The file representing the repository type
    * @return An instance of MockRepositoryImporter implementation
    * @throws IOException in case of file access
    */
   public static MockRepositoryImporter getMockRepositoryImporter(File mockRepository) throws IOException {
      MockRepositoryImporter importer = null;

      // Analyse first lines of file content to guess repository type.
      String line = null;
      BufferedReader reader = Files.newBufferedReader(mockRepository.toPath(), Charset.forName("UTF-8"));
      while ((line = reader.readLine()) != null) {
         line = line.trim();
         // Check is we start with json object or array definition.
         if (line.startsWith("\"_postman_id\":")) {
            importer  = new PostmanCollectionImporter(mockRepository.getPath());
            break;
         } else if (line.startsWith("<?xml")) {
            importer = new SoapUIProjectImporter(mockRepository.getPath());
            break;
         } else if (line.startsWith("openapi: 3")
               || line.startsWith("\"openapi\": \"3") || line.startsWith("'openapi': '3")) {
            importer = new OpenAPIImporter(mockRepository.getPath());
            break;
         }
      }
      reader.close();

      // Otherwise, default to SoapUI project importer implementation.
      if (importer == null) {
         importer = new SoapUIProjectImporter(mockRepository.getPath());
      }

      return importer;
   }
}
