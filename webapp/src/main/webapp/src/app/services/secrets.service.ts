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

import { Secret } from '../models/secret.model';
import { IAuthenticationService } from './auth.service';


@Injectable({ providedIn: 'root' })
export class SecretsService {

  private rootUrl = '/api';

  constructor(private http: HttpClient) { }

  getSecrets(page: number = 1, pageSize: number = 20): Observable<Secret[]> {
    const options = { params: new HttpParams().set('page', String(page - 1)).set('size', String(pageSize)) };
    return this.http.get<Secret[]>(this.rootUrl + '/secrets', options);
  }

  filterSecrets(filter: string): Observable<Secret[]> {
    const options = { params: new HttpParams().set('name', filter) };
    return this.http.get<Secret[]>(this.rootUrl + '/secrets', options);
  }

  countSecrets(): Observable<any> {
    return this.http.get<any>(this.rootUrl + '/secrets/count');
  }

  createSecret(secret: Secret): Observable<Secret> {
    return this.http.post<Secret>(this.rootUrl + '/secrets', secret);
  }

  updateSecret(secret: Secret): Observable<Secret> {
    return this.http.put<Secret>(this.rootUrl + '/secrets/' + secret.id, secret);
  }

  deleteSecret(secret: Secret): Observable<Secret> {
    return this.http.delete<Secret>(this.rootUrl + '/secrets/' + secret.id);
  }
}
