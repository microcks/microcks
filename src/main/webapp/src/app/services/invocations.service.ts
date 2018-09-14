import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { DailyInvocations } from '../models/metric.model';

@Injectable({ providedIn: 'root' })
export class InvocationsService {

  private rootUrl: string = '/api';

  constructor(private http: HttpClient) { }

  public getInvocationStats(day: Date) : Observable<DailyInvocations> {
    if (day != null) {
      const dayStr = this.formatDayDate(day);
      const options = { params: new HttpParams().set('day', dayStr) };
      return this.http.get<DailyInvocations>(this.rootUrl + '/invocations/global', options);
    }
    return this.http.get<DailyInvocations>(this.rootUrl + '/invocations/global');
  }

  public getTopInvocations(day: Date) : Observable<DailyInvocations[]> {
    if (day != null) {
      const dayStr = this.formatDayDate(day);
      const options = { params: new HttpParams().set('day', dayStr) };
      return this.http.get<DailyInvocations[]>(this.rootUrl + '/invocations/top', options);
    }
    return this.http.get<DailyInvocations[]>(this.rootUrl + '/invocations/top');
  }

  public getServiceInvocationStats(serviceName: string, serviceVersion: string, day: Date) : Observable<DailyInvocations> {
    if (day != null) {
      const dayStr = this.formatDayDate(day);
      const options = { params: new HttpParams().set('day', dayStr) };
      return this.http.get<DailyInvocations>(this.rootUrl + '/invocations/' + serviceName + '/' + serviceVersion, options);
    }
    return this.http.get<DailyInvocations>(this.rootUrl + '/invocations/' + serviceName + '/' + serviceVersion);
  }

  private formatDayDate(day: Date) : string {
    var result = day.getFullYear().toString();
    result += day.getMonth() < 9 ? '0' + (day.getMonth()+1).toString() : (day.getMonth()+1).toString();
    result += day.getDate() < 10 ? '0' + day.getDate().toString() : day.getDate().toString();
    return result;
  }
}