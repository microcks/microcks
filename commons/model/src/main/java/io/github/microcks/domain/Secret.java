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
package io.github.microcks.domain;

import org.springframework.data.annotation.Id;

/**
 * Domain class representing a Secret used for authenticating.
 * @author laurent
 */
public class Secret {

   @Id
   private String id;
   private String name;
   private String description;

   private String username;
   private String password;

   private String token;
   private String tokenHeader;

   private String caCertPem;

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public String getUsername() {
      return username;
   }

   public void setUsername(String username) {
      this.username = username;
   }

   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   public String getToken() {
      return token;
   }

   public void setToken(String token) {
      this.token = token;
   }

   public String getTokenHeader() {
      return tokenHeader;
   }

   public void setTokenHeader(String tokenHeader) {
      this.tokenHeader = tokenHeader;
   }

   public String getCaCertPem() {
      return caCertPem;
   }

   public void setCaCertPem(String caCertPem) {
      this.caCertPem = caCertPem;
   }
}
