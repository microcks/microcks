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
import { Observable, BehaviorSubject } from 'rxjs';
import { User } from '../models/user.model';
import { ConfigService } from './config.service';
import { HttpClient } from '@angular/common/http';

/**
 * A version of the authentication service that uses keycloak.js to provide
 * authentication services.
 */
export class KeycloakAuthenticationService extends IAuthenticationService {

  private authenticated: BehaviorSubject<boolean> = new BehaviorSubject(false);
  public authenticated$: Observable<boolean> = this.authenticated.asObservable();

  private authenticatedUser: BehaviorSubject<User> = new BehaviorSubject(new User());
  public authenticatedUser$: Observable<User> = this.authenticatedUser.asObservable();

  private keycloak: any;

  /**
   * Constructor.
   */
  constructor(private http: HttpClient, private config: ConfigService) {
    super();
    const w: any = window;
    this.keycloak = w.keycloak;

    // console.info("Token: %s", JSON.stringify(this.keycloak.tokenParsed, null, 2));
    // console.info("ID Token: %s", JSON.stringify(this.keycloak.idTokenParsed, null, 2));
    // console.info("Access Token: %s", this.keycloak.token);

    const user: User = new User();
    user.name = this.keycloak.tokenParsed.name;
    user.login = this.keycloak.tokenParsed.preferred_username;
    user.email = this.keycloak.tokenParsed.email;

    this.authenticated.next(true);
    this.authenticatedUser.next(user);

    // Periodically refresh
    // TODO run this outsize NgZone using zone.runOutsideAngular() : https://angular.io/api/core/NgZone
    setInterval(() => {
      this.keycloak.updateToken(30);
    }, 30000);
  }

  /**
   * Returns the observable for is/isnot authenticated.
   */
  public isAuthenticated(): Observable<boolean> {
    return this.authenticated$;
  }

  /**
   * Returns an observable over the currently authenticated User (or null if not logged in).
   */
  public getAuthenticatedUser(): Observable<User> {
    return this.authenticatedUser$;
  }

  /**
   * Returns the currently authenticated user.
   */
  public getAuthenticatedUserNow(): User {
    return this.authenticatedUser.getValue();
  }

  /**
   * Not supported.
   */
  public login(user: string, credential: any): Promise<User> {
    throw new Error('Not supported.');
  }

  /**
   * Called to check that user can endorse a role.
   */
  public hasRole(role: string): boolean {
    // console.log("[KeycloakAuthenticationService] hasRole called with " + role);
    // Now default to a resource role for 'microcks-app'
    // return this.keycloak.hasRealmRole(role);

    if (!this.keycloak.resourceAccess) {
      return false;
    }
    const access = this.keycloak.resourceAccess['microcks-app'] || this.keycloak.resourceAccess[this.keycloak.clientId];
    return !!access && access.roles.indexOf(role) >= 0;

    // Don't know why but this fail as the code above is just copy-pasted from implementations...
    // return this.keycloak.hasResourceRole('microcks-app', role);
  }

  /**
   * Called to check that user can endorse role for at least one resource.
   */
  public hasRoleForAnyResource(role: string): boolean {
    const rolePathPrefix = '/microcks/' + role + '/';
    const groups = this.keycloak.tokenParsed['microcks-groups'];

    return !!groups && groups.filter((element: string) => element.startsWith(rolePathPrefix)).length > 0;
  }

  /**
   * Called to check that user can endorse role for a specific resource.
   */
  public hasRoleForResource(role: string, resource: string): boolean {
    const rolePath = '/microcks/' + role + '/' + resource;
    const groups = this.keycloak.tokenParsed['microcks-groups'];

    return !!groups && groups.indexOf(rolePath) >= 0;
  }

  /**
   * Logout.
   */
  public logout(): void {
    this.keycloak.logout({ redirectUri: location.href });
  }

  /**
   * Called to inject authentication headers into a remote API call.
   */
  public injectAuthHeaders(headers: { [header: string]: string }): void {
    const authHeader: string = 'Bearer ' + this.keycloak.token;
    headers['Authorization'] = authHeader;
  }

  /**
   * Called to return the keycloak access token.
   */
  public getAuthenticationSecret(): string {
    return this.keycloak.token;
  }

  /**
   * Return the Keycloak realm name.
   */
  public getRealmName(): string {
    return this.keycloak.realm;
  }

  /**
   * Return the Keycloak realm url.
   */
  public getRealmUrl(): string {
    const realmUrl = this.keycloak.createLogoutUrl();
    return realmUrl.slice(0, realmUrl.indexOf('/protocol/'));
  }

  /**
   * Return the Keycloak administration realm url.
   */
  public getAdminRealmUrl(): string {
    const realmUrl = this.getRealmUrl();
    if (realmUrl.indexOf('/auth/') != -1) {
      // Pre-Keycloak-X url scheme.
      return realmUrl.replace('/auth/', '/auth/admin/');
    }
    // Keycloak-X url scheme.
    return realmUrl.replace('/realms/', '/admin/realms/');
  }
}
