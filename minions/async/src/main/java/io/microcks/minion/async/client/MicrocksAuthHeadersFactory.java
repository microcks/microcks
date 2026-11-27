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

package io.microcks.minion.async.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.jboss.logging.Logger;

/**
 * Adds the service account Bearer token, when Microcks requires one, to every call of the Microcks REST client.
 * @author laurent
 */
@ApplicationScoped
public class MicrocksAuthHeadersFactory implements ClientHeadersFactory {

   private static final Logger logger = Logger.getLogger(MicrocksAuthHeadersFactory.class);

   @Inject
   MicrocksTokenProvider tokenProvider;

   @Override
   public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
         MultivaluedMap<String, String> clientOutgoingHeaders) {
      MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
      try {
         tokenProvider.authorization().ifPresent(value -> headers.add(HttpHeaders.AUTHORIZATION, value));
      } catch (ConnectorException e) {
         logger.error("Unable to authenticate to Microcks API", e);
         throw new IllegalStateException("Unable to authenticate to Microcks API", e);
      }
      return headers;
   }
}
