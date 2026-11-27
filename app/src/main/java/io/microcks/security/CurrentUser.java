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
package io.microcks.security;

import jakarta.enterprise.context.RequestScoped;

/**
 * Holds the user of the current HTTP request, as resolved by the {@link AuthenticationFilter}.
 * @author laurent
 */
@RequestScoped
public class CurrentUser {

   /** The identity used when authentication is disabled: every role is granted. */
   public static final UserInfo ANONYMOUS = new UserInfo("Anonymous", "anonymous", null, null, null, new String[] {
         AuthorizationChecker.ROLE_USER, AuthorizationChecker.ROLE_MANAGER, AuthorizationChecker.ROLE_ADMIN },
         new String[0]);

   private UserInfo user;

   /** @return The authenticated user or null if the request is not authenticated */
   public UserInfo get() {
      return user;
   }

   /** @param user The authenticated user of the current request */
   public void set(UserInfo user) {
      this.user = user;
   }
}
