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
import { IAuthenticationService } from './auth.service';
import { ConfigService } from './config.service';
import { KeycloakAuthenticationService } from './auth-keycloak.service';
import { AnonymousAuthenticationService } from './auth-anonymous.service';
import { HttpClient } from '@angular/common/http';


export function AuthenticationServiceFactory(http: HttpClient, config: ConfigService): IAuthenticationService {
  console.info('[AuthenticationServiceFactory] Creating AuthenticationService...');
  if (config.authType() === 'keycloakjs') {
    console.info('[AuthenticationServiceFactory] Creating keycloak.js auth service.');
    return new KeycloakAuthenticationService(http, config);
  } else if (config.authType() === 'anonymous') {
    console.info('[AuthenticationServiceFactory] Creating Anonymous auth service.');
    return new AnonymousAuthenticationService(http, config);
  } else {
    console.error('[AuthenticationServiceFactory] Unsupported auth type: %s', config.authType());
    //return null;
    return new AnonymousAuthenticationService(http, config);
  }
}


export let AuthenticationServiceProvider = {
  provide: IAuthenticationService,
  useFactory: AuthenticationServiceFactory,
  deps: [HttpClient, ConfigService]
};
