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

import { WebhookRegistration, WebhookRegistrationRequest } from '../models/webhook.model';

@Injectable({ providedIn: 'root' })
export class WebhooksService {

  private rootUrl = '/api';

  constructor(private http: HttpClient) { }

  public listByOperationId(operationId: string, page: number = 1, pageSize: number = 20): Observable<WebhookRegistration[]> {
    const options = { params: new HttpParams().set('page', String(page - 1)).set('size', String(pageSize)) };
    return this.http.get<WebhookRegistration[]>(this.rootUrl + '/webhooks/operation/' + this.encodeOperationId(operationId));
  }

  public countByOperationId(operationId: string): Observable<any> {
    return this.http.get<any>(this.rootUrl + '/webhooks/operation/' + this.encodeOperationId(operationId) + '/count');
  }

  public create(registrationRequest: WebhookRegistrationRequest): Observable<WebhookRegistration> {
    registrationRequest.operationId = this.encodeOperationId(registrationRequest.operationId);
    return this.http.post<WebhookRegistration>(this.rootUrl +  '/webhooks', registrationRequest);
  }

  public delete(webhookId: string): Observable<void> {
    return this.http.delete<void>(this.rootUrl + '/webhooks/' + webhookId);
  }

  private encodeOperationId(operationId: string): string {
    // operation may contain / that are forbidden within encoded URI.
    // Replace them by "!" and implement same protocole on server-side.
    // Switched from _ to ! in replacement as less commonly used in URL parameters, in line with other frameworks e.g. Drupal
    return operationId.replace(/\//g, '!');
  }
}