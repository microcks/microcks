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
package io.github.microcks.util;

import io.github.microcks.config.ProxySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * A simple Authenticator for basic network authentication handling.
 * @author laurent
 */
public class UsernamePasswordProxyAuthenticator extends Authenticator {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(UsernamePasswordProxyAuthenticator.class);

   private final ProxySettings settings;

   private final PasswordAuthentication authentication;

   /**
    * Constructor.
    * @param settings The proxy settings for network to reach out.
    */
   public UsernamePasswordProxyAuthenticator(ProxySettings settings) {
      this.settings = settings;
      this.authentication = new PasswordAuthentication(settings.getUsername(), settings.getPassword().toCharArray());
   }

   protected PasswordAuthentication getPasswordAuthentication() {
      // Return authentication information only if requester is the identified proxy.
      log.debug("Handling proxy authentication for {}", getRequestingHost());
      if (getRequestorType() == RequestorType.PROXY) {
         if (getRequestingHost().equalsIgnoreCase(settings.getHost()) && getRequestingPort() == settings.getPort()) {
            return authentication;
         }
      }
      return null;
   }
}
