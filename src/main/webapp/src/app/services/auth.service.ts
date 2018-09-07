import { Observable } from "rxjs";

import { User } from "../models/user.model";


export abstract class IAuthenticationService {

  /**
   * A way for consumers to subscribe to the current authentication status of the user/app.
   */
  abstract isAuthenticated(): Observable<boolean>;

  /**
   * Get the currently authenticated user.  May be null if the user is not currently authenticated.
   */
  abstract getAuthenticatedUser(): Observable<User>;

  /**
   * Immediately gets the current authenticated user (if any).  Returns null if no user is
   * currently authenticated.
   */
  abstract getAuthenticatedUserNow(): User;

  /**
   * Called to authenticate a user.
   * @param user
   * @param credential
   */
  abstract login(user: string, credential: any): Promise<User>;

  /**
   * Called to check that user can endorse a role.
   * @param role 
   */
  abstract hasRole(role: string): boolean;

  /**
   * Calles to check that user can endorse role for a specific resource.
   * @param role 
   * @param resource 
   */
  abstract hasRoleForResource(role: string, resource: string): boolean;

  /**
   * Called to log out the current user.
   */
  abstract logout(): void;

  /**
   * Called to inject authentication headers into an API REST call.
   * @param headers
   */
  abstract injectAuthHeaders(headers: { [header: string]: string }): void;

  /**
   * Called to return an authentication secret (e.g. the auth access token).
   */
  abstract getAuthenticationSecret(): string;
}