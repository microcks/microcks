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
package io.github.microcks.security;

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
@PropertySources({
      @PropertySource("features.properties"),
      @PropertySource(value = "file:/deployments/config/features.properties", ignoreResourceNotFound = true)
})
public class AuthorizationChecker {

   /** The Microcks user role name. */
   public static final String ROLE_USER = "user";
   /** The Microcks manager role name. */
   public static final String ROLE_MANAGER = "manager";
   /** The Microcks admin role name. */
   public static final String ROLE_ADMIN = "admin";

   /** The prefix used for Microcks groups name. */
   private static final String MICROCKS_GROUPS_PREFIX = "/microcks/";

   @Value("${features.feature.repository-tenancy.enabled}")
   private final boolean authorizationEnabled = false;

   @Value("${features.feature.repository-filter.label-key}")
   private final String filterLabelKey = null;

   /**
    * Check if provided user is having a specific role for given service.
    * @param userInfo The information representing user to check access for.
    * @param role The role the user should endorse.
    * @param service The service the user should be authorized with the role.
    * @return True if authorized, false otherwise.
    */
   public boolean hasRoleForService(UserInfo userInfo, String role, Service service) {
      if (authorizationEnabled) {
         // Build the full rolePath that is checked for group membership.
         String rolePath = MICROCKS_GROUPS_PREFIX + role + "/" + service.getMetadata().getLabels().get(filterLabelKey);
         return Arrays.stream(userInfo.getGroups()).anyMatch(rolePath::equals);
      }
      return true;
   }
}
