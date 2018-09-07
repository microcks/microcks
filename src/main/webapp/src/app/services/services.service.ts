import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Service, ServiceView, Api } from '../models/service.model';

@Injectable({ providedIn: 'root' })
export class ServicesService {

  private rootUrl: string = '/api';

  constructor(private http: HttpClient) { }

  public getServices(page: number = 1, pageSize: number = 20): Observable<Service[]> {
    const options = { params: new HttpParams().set('page', String(page - 1)).set('size', String(pageSize)) };
    return this.http.get<Service[]>(this.rootUrl + '/services', options);
  }

  public filterServices(filter: string): Observable<Service[]> {
    const options = { params: new HttpParams().set('name', filter) };
    return this.http.get<Service[]>(this.rootUrl + '/services/search', options);
  }

  public countServices(): Observable<any> { 
    return this.http.get<any>(this.rootUrl + '/services/count');
  }

  public getServiceView(serviceId: string): Observable<ServiceView> {
    const options = { params: new HttpParams().set('messages', 'true') };
    return this.http.get<ServiceView>(this.rootUrl + '/services/' + serviceId, options);
  }

  public getService(serviceId: string): Observable<Service> {
    const options = { params: new HttpParams().set('messages', 'false') };
    return this.http.get<Service>(this.rootUrl + '/services/' + serviceId, options);
  }

  public createDynamicAPI(api: Api): Observable<Service> {
    return this.http.post<Service>(this.rootUrl + '/services/generic', api);
  }
}