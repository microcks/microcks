import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { TestRequest, TestResult } from '../models/test.model';
import { RequestResponsePair } from '../models/service.model';

@Injectable({ providedIn: 'root' })
export class TestsService {

  private rootUrl: string = '/api';

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

  public getMessages(test: TestResult, operation: string): Observable<RequestResponsePair> {
    // operation may contain / that are forbidden within encoded URI.
    // Replace them by "_" and implement same protocole on server-side.
    operation = operation.replace(/\//g, '_');
    var testCaseId = test.id + '-' + test.testNumber + '-' + encodeURIComponent(operation);
    console.log("[getMessages] called for " + testCaseId);
    return this.http.get<RequestResponsePair>(this.rootUrl + '/tests/' + test.id + '/messages/' + testCaseId);
  }
}