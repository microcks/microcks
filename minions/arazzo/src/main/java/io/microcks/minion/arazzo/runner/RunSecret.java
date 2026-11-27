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
package io.microcks.minion.arazzo.runner;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The authorization every call of a run carries, from a secret of Microcks.
 * @param username    The username of Basic authentication; null for none
 * @param password    The password of Basic authentication; null for none
 * @param token       The token; null for none
 * @param tokenHeader The header carrying the token; null or blank for Authorization: Bearer
 * @author SebastienDegodez
 */
public record RunSecret(String username, String password, String token, String tokenHeader) {

   /** No secret: the calls carry only the headers of their steps. */
   public static final RunSecret NONE = new RunSecret(null, null, null, null);

   /** What a reported call shows instead of a value of the secret, as contract tests record it. */
   static final String VALUE_FROM_SECRET = "<value-from-secret>";

   private static final String AUTHORIZATION = "Authorization";

   /**
    * @param call A call, as its step builds it
    * @return The call as sent, with the authorization of the secret
    */
   public HttpCall authorize(HttpCall call) {
      return with(call, false);
   }

   /**
    * @param call A call, as its step builds it
    * @return The call as reported, its authorization telling where its values come from rather than giving them
    */
   public HttpCall trace(HttpCall call) {
      return with(call, true);
   }

   /** Basic authentication with a username and a password; the token in its own header, or as Bearer. */
   private HttpCall with(HttpCall call, boolean traced) {
      Map<String, String> headers = new LinkedHashMap<>(call.headers());
      if (username != null && password != null) {
         set(headers, AUTHORIZATION, "Basic " + (traced ? VALUE_FROM_SECRET
               : Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8))));
      }
      if (token != null) {
         String value = traced ? VALUE_FROM_SECRET : token;
         if (tokenHeader != null && !tokenHeader.isBlank()) {
            set(headers, tokenHeader.strip(), value);
         } else {
            set(headers, AUTHORIZATION, "Bearer " + value);
         }
      }
      return new HttpCall(call.method(), call.uri(), headers, call.body());
   }

   /** Header names are case-insensitive: the secret replaces a header of the step with the same name. */
   private static void set(Map<String, String> headers, String name, String value) {
      headers.keySet().removeIf(existing -> existing.equalsIgnoreCase(name));
      headers.put(name, value);
   }
}
