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
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

import { TestRequest, TestResult } from '../models/test.model';
import { RequestResponsePair, UnidirectionalEvent } from '../models/service.model';

const ENDPOINTS = {
  TESTS: () => `${environment.apiUrl}api/tests`,
  TESTS_SERVICES: () => ENDPOINTS.TESTS() + `/service`
};

@Injectable({ providedIn: 'root' })
export class TestsService {

  constructor(private http: HttpClient) { }

  public listByServiceId(serviceId: string, page: number = 1, pageSize: number = 20): Observable<TestResult[]> {
    const options = { params: new HttpParams().set('page', String(page - 1)).set('size', String(pageSize)) };
    return this.http.get<TestResult[]>(ENDPOINTS.TESTS_SERVICES() + '/' + serviceId, options);
  }

  public countByServiceId(serviceId: string): Observable<any> {
    return this.http.get<any>(ENDPOINTS.TESTS_SERVICES() + '/' + serviceId + '/count');
  }

  public getTestResult(resultId: string): Observable<TestResult> {
    return this.http.get<any>(ENDPOINTS.TESTS() + '/' + resultId);
  }

  public create(testRequest: TestRequest): Observable<TestResult> {
    return this.http.post<TestResult>(ENDPOINTS.TESTS(), testRequest);
  }

  public getMessages(test: TestResult, operation: string): Observable<RequestResponsePair> {
    // operation may contain / that are forbidden within encoded URI.
    // Replace them by "_" and implement same protocole on server-side.
    operation = operation.replace(/\//g, '_');
    var testCaseId = test.id + '-' + test.testNumber + '-' + encodeURIComponent(operation);
    console.log("[getMessages] called for " + testCaseId);
    return this.http.get<RequestResponsePair>(ENDPOINTS.TESTS() + '/' + test.id + '/messages/' + testCaseId);
  }

  public getEventMessages(test: TestResult, operation: string): Observable<UnidirectionalEvent> {
    // operation may contain / that are forbidden within encoded URI.
    // Replace them by "_" and implement same protocole on server-side.
    operation = operation.replace(/\//g, '_');
    var testCaseId = test.id + '-' + test.testNumber + '-' + encodeURIComponent(operation);
    console.log("[getEventMessages] called for " + testCaseId);
    return this.http.get<UnidirectionalEvent>(ENDPOINTS.TESTS() + '/' + test.id + '/events/' + testCaseId);
  }
}