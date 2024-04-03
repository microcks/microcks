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
package io.github.microcks.util.openapi;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.ReferenceResolver;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * An implementation of MockRepositoryImporter that deals with Swagger v2.x.x specification file ; whether encoding into
 * JSON or YAML documents.
 * @author laurent
 */
public class SwaggerImporter extends OpenAPIImporter {

   /**
    * Build a new importer.
    * @param specificationFilePath The path to local OpenAPI spec file
    * @param referenceResolver     An optional resolver for references present into the OpenAPI file
    * @throws IOException if project file cannot be found or read.
    */
   public SwaggerImporter(String specificationFilePath, ReferenceResolver referenceResolver) throws IOException {
      super(specificationFilePath, referenceResolver);
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) {
      List<Resource> results = super.getResourceDefinitions(service);
      if (!results.isEmpty()) {
         results.get(0).setType(ResourceType.SWAGGER);
      }
      return results;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      return Collections.emptyList();
   }
}
