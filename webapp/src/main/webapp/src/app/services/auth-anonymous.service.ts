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
import { Observable, of } from 'rxjs';
import { User } from '../models/user.model';
import { ConfigService } from './config.service';
import { HttpClient } from '@angular/common/http';

/**
 * A version of the authentication service that uses keycloak.js to provide
 * authentication services.
 */
export class AnonymousAuthenticationService extends IAuthenticationService {

  private user: User;

  /**
   * Constructor.
   */
  constructor(private http: HttpClient, private config: ConfigService) {
    super();
    this.user = new User();
    this.user.login = 'admin';
    this.user.username = 'Anonymous Admin';
    this.user.name = 'Anonymous Admin';
    this.user.email = 'anonymous.admin@microcks.io';
  }

  /**
   * Returns the observable for is/isnot authenticated.
   */
  public isAuthenticated(): Observable<boolean> {
    return of(true);
  }

  /**
   * Returns an observable over the currently authenticated User (or null if not logged in).
   */
  public getAuthenticatedUser(): Observable<User> {
    return of(this.user);
  }

  /**
   * Returns the currently authenticated user.
   */
  public getAuthenticatedUserNow(): User {
    return this.user;
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
    return true;
  }

  /**
   * Called to check that user can endorse role for at least one resource.
   */
  public hasRoleForAnyResource(role: string): boolean {
    return true;
  }

  /**
   * Called to check that user can endorse role for a specific resource.
   */
  public hasRoleForResource(role: string, resource: string): boolean {
    return true;
  }

  /**
   * Logout.
   */
  public logout(): void {
    // Nothing to to here.
  }

  /**
   * Called to inject authentication headers into a remote API call.
   */
  public injectAuthHeaders(headers: { [header: string]: string }): void {
    // Nothing to do here.
  }

  /**
   * Called to return the keycloak access token.
   */
  public getAuthenticationSecret(): string {
    return 'admin';
  }

  /**
   * Return the Keycloak realm name.
   */
  public getRealmName(): string {
    return 'microcks';
  }

  /**
   * Return the Keycloak realm url.
   */
  public getRealmUrl(): string {
    throw new Error('Not supported.');
  }

  /**
   * Return the Keycloak administration realm url.
   */
  public getAdminRealmUrl(): string {
    throw new Error('Not supported.');
  }
}
