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
package io.github.microcks.util;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * A simple Authenticator for basic network authentication handling.
 * @author laurent
 */
public class UsernamePasswordAuthenticator extends Authenticator{
   
   private final PasswordAuthentication authentication;
   
   /**
    * Constructor.
    * @param username Name of user for network connections
    * @param password Password of user for network connections.
    */
   public UsernamePasswordAuthenticator(String username, String password){
      authentication = new PasswordAuthentication(username, password.toCharArray());
   }
   
   protected PasswordAuthentication getPasswordAuthentication(){
      return authentication;
  }
}
