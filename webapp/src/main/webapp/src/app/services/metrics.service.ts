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

import { DailyInvocations, TestConformanceMetric, TestResultSummary, WeightedMetricValue,  } from '../models/metric.model';

@Injectable({ providedIn: 'root' })
export class MetricsService {

  private rootUrl = '/api';

  constructor(private http: HttpClient) { }

  public getInvocationStats(day: Date): Observable<DailyInvocations> {
    if (day != null) {
      const dayStr = this.formatDayDate(day);
      const options = { params: new HttpParams().set('day', dayStr) };
      return this.http.get<DailyInvocations>(this.rootUrl + '/metrics/invocations/global', options);
    }
    return this.http.get<DailyInvocations>(this.rootUrl + '/metrics/invocations/global');
  }

  public getTopInvocations(day: Date): Observable<DailyInvocations[]> {
    if (day != null) {
      const dayStr = this.formatDayDate(day);
      const options = { params: new HttpParams().set('day', dayStr) };
      return this.http.get<DailyInvocations[]>(this.rootUrl + '/metrics/invocations/top', options);
    }
    return this.http.get<DailyInvocations[]>(this.rootUrl + '/metrics/invocations/top');
  }

  public getServiceInvocationStats(serviceName: string, serviceVersion: string, day: Date): Observable<DailyInvocations> {
    if (day != null) {
      const dayStr = this.formatDayDate(day);
      const options = { params: new HttpParams().set('day', dayStr) };
      return this.http.get<DailyInvocations>(this.rootUrl + '/metrics/invocations/' + serviceName + '/' + serviceVersion, options);
    }
    return this.http.get<DailyInvocations>(this.rootUrl + '/metrics/invocations/' + serviceName + '/' + serviceVersion);
  }

  public getInvocationsStatsTrend(limit: number): Observable<any> {
    if (limit != null) {
      const options = { params: new HttpParams().set('limit', limit.toString()) };
      return this.http.get<any>(this.rootUrl + '/metrics/invocations/global/latest', options);
    }
    return this.http.get<any>(this.rootUrl + '/metrics/invocations/global/latest');
  }

  public getServiceTestConformanceMetric(serviceId: string): Observable<TestConformanceMetric> {
    return this.http.get<TestConformanceMetric>(this.rootUrl + '/metrics/conformance/service/' + serviceId);
  }

  public getAggregatedTestConformanceMetrics(): Observable<WeightedMetricValue[]> {
    return this.http.get<WeightedMetricValue[]>(this.rootUrl + '/metrics/conformance/aggregate');
  }

  public getLatestTestsTrend(limit: number): Observable<TestResultSummary[]> {
    if (limit != null) {
      const options = { params: new HttpParams().set('limit', limit.toString()) };
      return this.http.get<TestResultSummary[]>(this.rootUrl + '/metrics/tests/latest', options);
    }
    return this.http.get<TestResultSummary[]>(this.rootUrl + '/metrics/tests/latest');
  }

  public formatDayDate(day: Date): string {
    let result = day.getFullYear().toString();
    result += day.getMonth() < 9 ? '0' + (day.getMonth() + 1).toString() : (day.getMonth() + 1).toString();
    result += day.getDate() < 10 ? '0' + day.getDate().toString() : day.getDate().toString();
    return result;
  }
}
