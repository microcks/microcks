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
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { User } from '../models/user.model';
import { IAuthenticationService } from './auth.service';
import { KeycloakAuthenticationService } from './auth-keycloak.service';

@Injectable({ providedIn: 'root' })
export class UsersService {

  private rootUrl = '/api';

  private microcksAppClientId: string | null = null;

  constructor(private http: HttpClient, protected authService: IAuthenticationService) {
    if (authService instanceof KeycloakAuthenticationService) {
      this.rootUrl = (authService as KeycloakAuthenticationService).getAdminRealmUrl();
      this.loadClientId();
    }
  }

  private loadClientId(): void {
    this.http.get<any[]>(this.rootUrl + '/clients?clientId=microcks-app&max=2&search=true').subscribe(
      {
        next: res => {
          const client = res.find(c => c.clientId === 'microcks-app');
          if (client) {
            this.microcksAppClientId = client.id;
          }
        },
        error: err => {
          console.warn('Unable to retrieve microcksAppClientId from Keycloak. Maybe you do not have correct roles?');
          this.microcksAppClientId = null;
        },
      }
    );
  }

  getRealmName(): string | null {
    if (this.authService instanceof KeycloakAuthenticationService) {
      return (this.authService as KeycloakAuthenticationService).getRealmName();
    }
    return null;
  }

  getGroups(): Observable<any[]> {
    // 'search' for Pre-Keycloak-X, 'q' for 'Keycloak-X'
    const options = {
      params: new HttpParams().set('search', 'microcks').set('q', 'microcks')
        .set('populateHierarchy', 'false')
    };
    return this.http.get<any[]>(this.rootUrl + '/groups', options);
  }
  createGroup(parentGroupId: string, name: string): Observable<any> {
    const group = {name};
    return this.http.post<any[]>(this.rootUrl + '/groups/' + parentGroupId + '/children', group);
  }

  getUsers(page: number = 1, pageSize: number = 20): Observable<User[]> {
    let first = 0;
    if (page > 1) {
      first += pageSize * (page - 1);
    }
    const options = { params: new HttpParams().set('first', String(first)).set('max', String(pageSize)) };
    return this.http.get<User[]>(this.rootUrl + '/users', options);
  }

  getMicrocksAppClientId(): string | null {
    return this.microcksAppClientId;
  }

  filterUsers(filter: string): Observable<User[]> {
    const options = { params: new HttpParams().set('search', filter) };
    return this.http.get<User[]>(this.rootUrl + '/users', options);
  }

  countUsers(): Observable<any> {
    return this.http.get<User[]>(this.rootUrl + '/users/count');
  }

  getUserRealmRoles(userId: string): Observable<any[]> {
    return this.http.get<any[]>(this.rootUrl + '/users/' + userId + '/role-mappings/realm');
  }
  getUserRoles(userId: string): Observable<any[]> {
    return this.http.get<any[]>(this.rootUrl + '/users/' + userId + '/role-mappings/clients/' + this.microcksAppClientId);
  }
  getUserGroups(userId: string): Observable<any[]> {
    return this.http.get<any[]>(this.rootUrl + '/users/' + userId + '/groups');
  }

  assignRoleToUser(userId: string, roleName: string): Observable<any> {
    return this.getRoleByName(roleName).pipe(
      switchMap((role: any) => {
        return this.http.post<any[]>(this.rootUrl + '/users/' + userId + '/role-mappings/clients/' + this.microcksAppClientId, [ role ]);
      })
    );
  }
  removeRoleFromUser(userId: string, roleName: string): Observable<any> {
    return this.getRoleByName(roleName).pipe(
      switchMap((role: any) => {
        return this.http.request<any[]>('delete',
          this.rootUrl + '/users/' + userId + '/role-mappings/clients/' + this.microcksAppClientId, { body: [ role ] });
      })
    );
  }

  putUserInGroup(userId: string, groupId: string): Observable<any> {
    const data = {realm: this.getRealmName(), userId, groupId};
    return this.http.put<any[]>(this.rootUrl + '/users/' + userId + '/groups/' + groupId, { body: data });
  }
  removeUserFromGroup(userId: string, groupId: string): Observable<any> {
    return this.http.request<any[]>('delete',
      this.rootUrl + '/users/' + userId + '/groups/' + groupId);
  }

  private getRoleByName(role: string): Observable<any> {
    return this.http.get<any[]>(this.rootUrl + '/clients/' + this.microcksAppClientId + '/roles/' + role);
  }
}
