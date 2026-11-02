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
package io.github.microcks.minion.async.handler;

import java.util.Map;
import java.util.UUID;

/**
 * Shared helper for handling the AsyncAPI correlation ID convention within request-reply handlers. Every protocol
 * specific handler should use this class so that the correlation ID contract (header name, generation fallback) stays
 * consistent across all supported bindings.
 * @author laurent
 */
public final class CorrelationIdSupport {

   /** Standard header/attribute name for message correlation ID, as per AsyncAPI spec. */
   public static final String CORRELATION_ID_HEADER = "correlationId";

   private CorrelationIdSupport() {
      // Hide default constructor as this is a utility class.
   }

   /**
    * Resolve the correlation identifier to use for a request-reply exchange. Reads the {@code correlationId} entry from
    * the incoming request headers/attributes if present, otherwise generates a random one so that the reply can always
    * be correlated by consumers relying on this convention.
    *
    * @param headers The request headers or attributes (protocol representation agnostic, as a simple Map)
    * @return The correlation ID to propagate on the reply message
    */
   public static String resolveCorrelationId(Map<String, String> headers) {
      if (headers != null && headers.containsKey(CORRELATION_ID_HEADER)) {
         return headers.get(CORRELATION_ID_HEADER);
      }
      return UUID.randomUUID().toString();
   }
}
