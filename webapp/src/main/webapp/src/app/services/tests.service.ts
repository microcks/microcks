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

import { TestRequest, TestResult } from '../models/test.model';
import { RequestResponsePair, UnidirectionalEvent } from '../models/service.model';

@Injectable({ providedIn: 'root' })
export class TestsService {

  private rootUrl = '/api';

  constructor(private http: HttpClient) { }

  public listByServiceId(serviceId: string, page: number = 1, pageSize: number = 20): Observable<TestResult[]> {
    const options = { params: new HttpParams().set('page', String(page - 1)).set('size', String(pageSize)) };
    return this.http.get<TestResult[]>(this.rootUrl + '/tests/service/' + serviceId, options);
  }

  public countByServiceId(serviceId: string): Observable<any> {
    return this.http.get<any>(this.rootUrl + '/tests/service/' + serviceId + '/count');
  }

  public getTestResult(resultId: string): Observable<TestResult> {
    return this.http.get<any>(this.rootUrl + '/tests/' + resultId);
  }

  public create(testRequest: TestRequest): Observable<TestResult> {
    return this.http.post<TestResult>(this.rootUrl +  '/tests', testRequest);
  }

  public getMessages(test: TestResult, operation: string): Observable<RequestResponsePair[]> {
    // operation may contain / that are forbidden within encoded URI.
    // Replace them by "!" and implement same protocole on server-side.
    // Switched from _ to ! in replacement as less commonly used in URL parameters, in line with other frameworks e.g. Drupal
    operation = operation.replace(/\//g, '!');
    const testCaseId = test.id + '-' + test.testNumber + '-' + encodeURIComponent(operation);
    //console.log('[getMessages] called for ' + testCaseId);
    return this.http.get<RequestResponsePair[]>(this.rootUrl + '/tests/' + test.id + '/messages/' + testCaseId);
  }

  public getEventMessages(test: TestResult, operation: string): Observable<UnidirectionalEvent[]> {
    // operation may contain / that are forbidden within encoded URI.
    // Replace them by "!" and implement same protocole on server-side.
    // Switched from _ to ! in replacement as less commonly used in URL parameters, in line with other frameworks e.g. Drupal
    operation = operation.replace(/\//g, '!');
    const testCaseId = test.id + '-' + test.testNumber + '-' + encodeURIComponent(operation);
    //console.log('[getEventMessages] called for ' + testCaseId);
    return this.http.get<UnidirectionalEvent[]>(this.rootUrl + '/tests/' + test.id + '/events/' + testCaseId);
  }
}
