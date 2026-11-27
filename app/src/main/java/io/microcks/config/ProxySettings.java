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
package io.github.microcks.config;

/**
 * This is a bean for holding HTTP/HTTPS proxy authentication settings.
 * @author laurent
 */
public class ProxySettings {

   private String host;
   private Integer port;
   private String username;
   private String password;

   /**
    * Build a new proxy settings bean.
    * @param host     The network proxy host
    * @param port     The network proxy port
    * @param username The proxy username
    * @param password The proxy credentials
    */
   public ProxySettings(String host, Integer port, String username, String password) {
      this.host = host;
      this.port = port;
      this.username = username;
      this.password = password;
   }

   public String getHost() {
      return host;
   }

   public Integer getPort() {
      return port;
   }

   public String getUsername() {
      return username;
   }

   public String getPassword() {
      return password;
   }
}
