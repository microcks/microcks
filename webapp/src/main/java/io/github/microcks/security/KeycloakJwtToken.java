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

/**
 * Some specific claims we're expecting in a Keycloak provided token.
 * @author laurent
 */
public class KeycloakJwtToken {

   /** The name of the token claim that holds resource access. */
   public static final String RESOURCE_ACCESS_TOKEN_CLAIM = "resource_access";

   /** The name of the token resource that holds roles information. */
   public static final String MICROCKS_APP_RESOURCE = "microcks-app";

   /** The name of token claim that contains the groups information. */
   public static final String MICROCKS_GROUPS_TOKEN_CLAIM = "microcks-groups";

}
