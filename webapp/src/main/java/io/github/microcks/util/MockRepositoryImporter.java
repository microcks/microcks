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
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.Operation;

import java.util.List;

/**
 * Interface definition for loading Microcks domain objects definitions from a source repository. Source repositories
 * may have different forms : SoapUI project file, Postman collection v2 file, custom XML format, directory structure
 * with naming conventions, and so on ...<br>
 * After usage of the companion factory, user should call the following methods in order :
 * <ul>
 * <li><code>getServiceDefinitions()</code>,</li>
 * <li><code>getResourceDefinitions()</code>,</li>
 * <li><code>getMessageDefinitions()</code>,</li>
 * </ul>
 * in order to incrementally populate the domain objects.
 * @author laurent
 */
public interface MockRepositoryImporter {

   /**
    * Just after repository importer initialization, this method should return the definitions of Service domain objects
    * as found into the target imported repository.
    * @return The list of found Services into repository. May be empty.
    * @throws MockRepositoryImportException if something goes wrong during import
    */
   List<Service> getServiceDefinitions() throws MockRepositoryImportException;

   /**
    * Once Service definition has been initialized, attahed resources may be identified and retrieved.
    * @param service The service to get resources for
    * @return The list of found Resources into repository. May be empty.
    * @throws MockRepositoryImportException if something goes wrong during import
    */
   List<Resource> getResourceDefinitions(Service service) throws MockRepositoryImportException;

   /**
    * For any operation of a service a map of associated Request and Response should be retrieve for full definition of
    * a Service.
    * @param service   The service to get messages for
    * @param operation The service operation/actions to get messages for
    * @return A list of Exchange messages
    * @throws MockRepositoryImportException if something goes wrong during import
    */
   List<Exchange> getMessageDefinitions(Service service, Operation operation) throws MockRepositoryImportException;
}
