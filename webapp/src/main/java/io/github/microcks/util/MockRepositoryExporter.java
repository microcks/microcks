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
package io.github.microcks.util;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;

import java.util.List;

/**
 * Interface definition for exporting Microcks Service and Message definitions into a specific format. You should call
 * the following methods in order :
 * <ul>
 * <li><code>addServiceDefinition()</code> one or many time (some implementation may support multiple services)</li>
 * <li><code>addMessageDefinitions()</code> one or many time (some implementation may support multiple services)</li>
 * <li><code>exportAsString()</code> to get the final export as a String</li>
 * </ul>
 * @author laurent
 */
public interface MockRepositoryExporter {

   /**
    * Add a Service definition to the exporter.
    * @param service The service to add
    * @throws MockRepositoryExportException if something goes wrong during export
    */
   void addServiceDefinition(Service service) throws MockRepositoryExportException;

   /**
    * Add a list of Message definitions to the exporter.
    * @param service   The service to add messages for
    * @param operation The service operation/actions to add messages for
    * @param messages  The list of messages to add
    * @throws MockRepositoryExportException if something goes wrong during export
    */
   void addMessageDefinitions(Service service, Operation operation, List<? extends Exchange> messages)
         throws MockRepositoryExportException;

   /**
    * Export the whole repository as a String (in the format of the exporter).
    * @return The repository export as a String
    * @throws MockRepositoryExportException if something goes wrong during export
    */
   String exportAsString() throws MockRepositoryExportException;
}
