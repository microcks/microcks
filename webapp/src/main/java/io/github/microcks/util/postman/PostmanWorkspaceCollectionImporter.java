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
package io.github.microcks.util.postman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Implement of MockRepositoryImporter that uses a Postman collection coming from a Postman Workspace (see
 * https://www.postman.com/postman/workspace/postman-public-workspace/request/12959542-a6a282df-907e-438b-8fe6-e5efaa60b8bf)
 * for building domain objects. Only v2 collection format is supported.
 * @author laurent
 */
public class PostmanWorkspaceCollectionImporter extends PostmanCollectionImporter {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(PostmanWorkspaceCollectionImporter.class);

   private static final String COLLECTION_WRAPPER_PROPERTY = "collection";

   /**
    * Build a new importer.
    * @param collectionFilePath The path to local Postman collection file
    * @throws IOException if project file cannot be found or read.
    */
   public PostmanWorkspaceCollectionImporter(String collectionFilePath) throws IOException {
      super();
      try {
         // Read Json bytes.
         byte[] jsonBytes = Files.readAllBytes(Paths.get(collectionFilePath));
         setCollectionContent(new String(jsonBytes, StandardCharsets.UTF_8));
         // Convert them to Node using Jackson object mapper.
         ObjectMapper mapper = new ObjectMapper();
         JsonNode collectionWrapper = mapper.readTree(jsonBytes);
         setCollection(collectionWrapper.path(COLLECTION_WRAPPER_PROPERTY));
      } catch (Exception e) {
         log.error("Exception while parsing Postman workspace collection file " + collectionFilePath, e);
         throw new IOException("Postman workspace collection file parsing error");
      }
   }
}
