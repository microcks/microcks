/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.microcks.security;

import io.microcks.domain.ImportJob;
import io.microcks.domain.Service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.Map;

/**
 * Security checking component. When authentication is disabled everything is authorized. Otherwise a null
 * {@code UserInfo} means "the user of the current HTTP request"; outside a request context it is not authorized.
 * @author laurent
 */
@ApplicationScoped
public class AuthorizationChecker {

   /** The prefix used for Microcks groups name. */
   private static final String MICROCKS_GROUPS_PREFIX = "/microcks/";

   @Inject
   AuthConfig authConfig;

   @Inject
   Instance<CurrentUser> currentUser;

   @ConfigProperty(name = "features.feature.repository-tenancy.enabled", defaultValue = "false")
   boolean tenancyEnabled;

   @ConfigProperty(name = "features.feature.repository-filter.label-key", defaultValue = "domain")
   String filterLabelKey;

   /** The Microcks user role name. */
   public static final String ROLE_USER = "user";
   /** The Microcks manager role name. */
   public static final String ROLE_MANAGER = "manager";
   /** The Microcks admin role name. */
   public static final String ROLE_ADMIN = "admin";

   /**
    * Check if provided user is having a specific role at the global level.
    * @param userInfo The information representing user to check access for.
    * @param role     The role the user should endorse.
    * @return True if authorized, false otherwise.
    */
   public boolean hasRole(UserInfo userInfo, String role) {
      if (!authConfig.enabled()) {
         return true;
      }
      UserInfo user = resolve(userInfo);
      return user != null && user.getRoles() != null && Arrays.asList(user.getRoles()).contains(role);
   }

   /**
    * Check if provided user is having a specific role for given service.
    * @param userInfo The information representing user to check access for.
    * @param role     The role the user should endorse.
    * @param service  The service the user should be authorized with the role.
    * @return True if authorized, false otherwise.
    */
   public boolean hasRoleForService(UserInfo userInfo, String role, Service service) {
      if (service.getMetadata() != null) {
         return hasRoleForLabels(userInfo, role, service.getMetadata().getLabels());
      }
      return hasRole(userInfo, role);
   }

   /**
    * Check if provided user is having a specific role for given import job.
    * @param userInfo The information representing user to check access for.
    * @param role     The role the user should endorse.
    * @param job      The import job the user should be authorized with the role.
    * @return True if authorized, false otherwise.
    */
   public boolean hasRoleForImportJob(UserInfo userInfo, String role, ImportJob job) {
      if (job.getMetadata() != null) {
         return hasRoleForLabels(userInfo, role, job.getMetadata().getLabels());
      }
      return hasRole(userInfo, role);
   }

   private boolean hasRoleForLabels(UserInfo userInfo, String role, Map<String, String> labels) {
      if (authConfig.enabled() && tenancyEnabled && labels != null) {
         UserInfo user = resolve(userInfo);
         if (user != null && user.getGroups() != null) {
            // Build the full rolePath that is checked for group membership.
            String rolePath = MICROCKS_GROUPS_PREFIX + role + "/" + labels.get(filterLabelKey);
            if (Arrays.asList(user.getGroups()).contains(rolePath)) {
               return true;
            }
         }
      }
      // Default to global role endorsing.
      return hasRole(userInfo, role);
   }

   private UserInfo resolve(UserInfo userInfo) {
      if (userInfo != null) {
         return userInfo;
      }
      try {
         return currentUser.isResolvable() ? currentUser.get().get() : null;
      } catch (ContextNotActiveException e) {
         return null;
      }
   }
}
