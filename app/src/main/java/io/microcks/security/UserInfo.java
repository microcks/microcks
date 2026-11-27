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

import java.util.Arrays;

/**
 * This is a wrapper for holding user informations.
 * @author laurent
 */
public class UserInfo {

   private final String name;
   private final String username;
   private final String givenName;
   private final String familyName;
   private final String email;
   private final String[] roles;
   private final String[] groups;

   /**
    * Create a new UserInfo with all required properties.
    * @param name       The full displayable name for this user (eg. Laurent Broudoux)
    * @param username   The system username for this user (eg. lbroudoux)
    * @param givenName  The user given name (eg. Laurent)
    * @param familyName The user family name (eg. Broudoux)
    * @param email      The user email address (eg. laurent@microcks.io)
    * @param roles      The array of endorsed roles for this user (eg. [user, manager, admin])
    * @param groups     The array of groups this user is member of (eg. [/microcks/manager/authentication,
    *                   /microcks/manager/greetings])
    */
   public UserInfo(String name, String username, String givenName, String familyName, String email, String[] roles,
         String[] groups) {
      this.name = name;
      this.username = username;
      this.givenName = givenName;
      this.familyName = familyName;
      this.email = email;
      this.roles = roles;
      this.groups = groups;
   }

   public String getName() {
      return name;
   }

   public String getUsername() {
      return username;
   }

   public String getGivenName() {
      return givenName;
   }

   public String getFamilyName() {
      return familyName;
   }

   public String getEmail() {
      return email;
   }

   public String[] getRoles() {
      return roles;
   }

   public String[] getGroups() {
      return groups;
   }

   @Override
   public String toString() {
      return "UserInfo{" + "name='" + name + '\'' + ", username='" + username + '\'' + ", givenName='" + givenName
            + '\'' + ", familyName='" + familyName + '\'' + ", email='" + email + '\'' + ", roles="
            + Arrays.toString(roles) + ", groups=" + Arrays.toString(groups) + '}';
   }
}
