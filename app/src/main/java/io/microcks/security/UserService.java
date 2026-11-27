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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains the Microcks users: onboarding at first login, refresh at each login, and the disabled flag. The disabled
 * state is looked up per request through a short cache.
 * @author laurent
 */
@ApplicationScoped
public class UserService {

   private static final long CACHE_TTL_MS = 15_000L;

   @Inject
   UserRepository users;

   private final Map<String, CachedState> disabledCache = new ConcurrentHashMap<>();

   private record CachedState(long expiresAt, boolean disabled) {
   }

   /**
    * Create the user at first login, or refresh its identity and roles.
    * @param info          The identity and roles asserted by the identity provider
    * @param idpSubject    The <code>sub</code> claim, may be null
    * @param emailVerified Whether the identity provider asserted that the email is verified
    * @return The stored user
    */
   public User recordLogin(UserInfo info, String idpSubject, boolean emailVerified) {
      User user = users.findByUsername(info.getUsername());
      boolean created = user == null;
      if (created) {
         user = new User();
         user.username = info.getUsername();
         user.createdAt = new Date();
      }
      user.idpSubject = idpSubject;
      user.name = info.getName();
      user.givenName = info.getGivenName();
      user.familyName = info.getFamilyName();
      user.email = info.getEmail() == null ? null : info.getEmail().toLowerCase(Locale.ROOT);
      user.emailVerified = emailVerified;
      user.roles = info.getRoles() == null ? java.util.List.of() : Arrays.asList(info.getRoles());
      user.lastLoginAt = new Date();
      if (created) {
         users.persist(user);
      } else {
         users.update(user);
      }
      disabledCache.remove(user.username);
      return user;
   }

   /**
    * Tell if a user has been disabled. Unknown users (service accounts, tokens issued before onboarding) are not.
    * @param username The token subject
    * @return True if the user is known and disabled
    */
   public boolean isDisabled(String username) {
      if (username == null) {
         return false;
      }
      CachedState cached = disabledCache.get(username);
      if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
         return cached.disabled();
      }
      User user = users.findByUsername(username);
      boolean disabled = user != null && user.disabled;
      disabledCache.put(username, new CachedState(System.currentTimeMillis() + CACHE_TTL_MS, disabled));
      return disabled;
   }

   /**
    * Enable or disable a user.
    * @param user     The user
    * @param disabled The new state
    */
   public void setDisabled(User user, boolean disabled) {
      user.disabled = disabled;
      users.update(user);
      disabledCache.remove(user.username);
   }

   /**
    * Remove a user record. It is created again at next login.
    * @param user The user
    */
   public void delete(User user) {
      users.deleteById(user.id);
      disabledCache.remove(user.username);
   }
}
