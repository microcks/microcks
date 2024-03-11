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
package io.github.microcks.security;

import io.github.microcks.domain.ImportJob;
import io.github.microcks.domain.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * A Spring component that holds security checking related utility methods.
 * @author laurent
 */
@Component
@PropertySources({ @PropertySource("features.properties"),
      @PropertySource(value = "file:/deployments/config/features.properties", ignoreResourceNotFound = true),
      @PropertySource("application.properties"), })
public class AuthorizationChecker {

   /** The Microcks user role name. */
   public static final String ROLE_USER = "user";
   /** The Microcks manager role name. */
   public static final String ROLE_MANAGER = "manager";
   /** The Microcks admin role name. */
   public static final String ROLE_ADMIN = "admin";

   /** The prefix used for Microcks groups name. */
   private static final String MICROCKS_GROUPS_PREFIX = "/microcks/";

   @Value("${keycloak.enabled}")
   private final Boolean authenticationEnabled = true;

   @Value("${features.feature.repository-tenancy.enabled}")
   private final Boolean authorizationEnabled = false;

   @Value("${features.feature.repository-filter.label-key}")
   private final String filterLabelKey = null;

   /**
    * Check if provided user is having a specific role at the global level.
    * @param userInfo The information representing user to check access for.
    * @param role     The role the user should endorse.
    * @return True if authorized, false otherwise.
    */
   public boolean hasRole(UserInfo userInfo, String role) {
      if (authenticationEnabled && userInfo.getRoles() != null) {
         return Arrays.stream(userInfo.getRoles()).anyMatch(role::equals);
      }
      return true;
   }

   /**
    * Check if provided user is having a specific role for given service.
    * @param userInfo The information representing user to check access for.
    * @param role     The role the user should endorse.
    * @param service  The service the user should be authorized with the role.
    * @return True if authorized, false otherwise.
    */
   public boolean hasRoleForService(UserInfo userInfo, String role, Service service) {
      if (authorizationEnabled && userInfo.getRoles() != null && service.getMetadata().getLabels() != null) {
         // Build the full rolePath that is checked for group membership.
         String rolePath = MICROCKS_GROUPS_PREFIX + role + "/" + service.getMetadata().getLabels().get(filterLabelKey);
         boolean serviceRole = Arrays.stream(userInfo.getGroups()).anyMatch(rolePath::equals);
         return serviceRole || hasRole(userInfo, role);
      }
      // Default to global role endorsing.
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
      if (authorizationEnabled && job.getMetadata().getLabels() != null) {
         // Build the full rolePath that is checked for group membership.
         String rolePath = MICROCKS_GROUPS_PREFIX + role + "/" + job.getMetadata().getLabels().get(filterLabelKey);
         boolean jobRole = Arrays.stream(userInfo.getGroups()).anyMatch(rolePath::equals);
         return jobRole || hasRole(userInfo, role);
      }
      // Default to global role endorsing.
      return hasRole(userInfo, role);
   }
}
