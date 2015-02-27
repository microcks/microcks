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
package com.github.lbroudoux.microcks.util;

import com.github.lbroudoux.microcks.domain.Operation;
import com.github.lbroudoux.microcks.domain.Request;
import com.github.lbroudoux.microcks.domain.Resource;
import com.github.lbroudoux.microcks.domain.Response;
import com.github.lbroudoux.microcks.domain.Service;

import java.util.List;
import java.util.Map;

/**
 * Interface definition for loading Microcks domain objects definitions from a
 * source repository. Source repositories may have different forms : SoapUI project
 * file, custom XML format, directory structure with naming conventions, and so on ...<br/>
 * After usage of the companion factory, user should call the following methods in order :
 * <ul>
 *    <ol><code>getServiceDefinitions()</code>,</ol>
 *    <ol><code>getResourceDefinitions()</code>,</ol>
 *    <ol><code>getMessageDefinitions()</code>,</ol>
 * </ul>
 * in order to incrementally populate the domain objects.
 * @author laurent
 */
public interface MockRepositoryImporter {

   /**
    * Just after repository importer initialization, this method
    * should return the definitions of Service domain objects as found
    * into the target imported repository.
    * @return The list of found Services into repository. May be empty.
    */
   List<Service> getServiceDefinitions();

   /**
    * Once Service definition has been initialized, attahed resources may be
    * identified and retrieved.
    * @param service The service to get resources for
    * @return The list of found Resources into repository. May be empty.
    */
   List<Resource> getResourceDefinitions(Service service);

   /**
    * For any operation of a service a map of associated Request and Response
    * should be retrieve for full definition of a Service.
    * @param service The service to get messages for
    * @param operation The service operation/actions to get messages for
    * @return A map of corralated Request/Response. May be empty.
    * @throws MockRepositoryImportException
    */
   Map<Request, Response> getMessageDefinitions(Service service, Operation operation) throws MockRepositoryImportException;
}
