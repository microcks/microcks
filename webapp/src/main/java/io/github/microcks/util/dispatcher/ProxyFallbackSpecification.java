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
package io.github.microcks.util.dispatcher;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represent the specification of a Proxy-Fallback evaluation. <code>dispatcher</code> should be the original dispatcher
 * to apply with its <code>dispatcherRules</code> companion. If no response actually found by mock controller, the
 * original request will be forwarded to the <code>proxyUrl</code>.
 */
@JsonPropertyOrder({ "dispatcher", "dispatcherRules", "proxyUrl" })
public class ProxyFallbackSpecification {
   private static final ObjectMapper mapper = new ObjectMapper();

   private String dispatcher;
   private String dispatcherRules;
   private String proxyUrl;

   public String getDispatcher() {
      return dispatcher;
   }

   public void setDispatcher(String dispatcher) {
      this.dispatcher = dispatcher;
   }

   public String getDispatcherRules() {
      return dispatcherRules;
   }

   public void setDispatcherRules(String dispatcherRules) {
      this.dispatcherRules = dispatcherRules;
   }

   public String getProxyUrl() {
      return proxyUrl;
   }

   public void setProxyUrl(String proxyUrl) {
      this.proxyUrl = proxyUrl;
   }

   /**
    * Build a specification from a JSON string.
    * @param jsonPayload The JSON payload representing valid specification
    * @return a newly built ProxyFallbackSpecification
    * @throws JsonMappingException if given JSON string cannot be parsed as a ProxySpecification
    */
   public static ProxyFallbackSpecification buildFromJsonString(String jsonPayload) throws JsonMappingException {
      try {
         return mapper.readValue(jsonPayload, ProxyFallbackSpecification.class);
      } catch (Exception e) {
         throw new JsonMappingException("Given JSON string cannot be interpreted as valid ProxyFallbackSpecification");
      }
   }
}
