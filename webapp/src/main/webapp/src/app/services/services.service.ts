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

import { Service, ServiceView, Api, GenericResource, OperationMutableProperties, Metadata } from '../models/service.model';

const ENDPOINTS = {
  GENERIC_RESOURCE: () => `${environment.apiUrl}api/genericresources/service`,
  SERVICES: () => `${environment.apiUrl}api/services`
};

@Injectable({ providedIn: 'root' })
export class ServicesService {


  constructor(private http: HttpClient) { }

  public getServices(page: number = 1, pageSize: number = 20): Observable<Service[]> {
    const options = { params: new HttpParams().set('page', String(page - 1)).set('size', String(pageSize)) };
    return this.http.get<Service[]>(ENDPOINTS.SERVICES(), options);
  }

  public filterServices(labelsFilter: Map<string, string>, nameFilter: string): Observable<Service[]> {
    let httpParams: HttpParams = new HttpParams();
    if (nameFilter != null) {
      httpParams = httpParams.set('name', nameFilter);
    }
    if (labelsFilter != null) {
      for (let key of Array.from(labelsFilter.keys())) {
        httpParams = httpParams.set('labels.' + key, labelsFilter.get(key));
      }
    }

    const options = { params: httpParams };
    return this.http.get<Service[]>(ENDPOINTS.SERVICES() + '/search', options);
  }

  public countServices(): Observable<any> {
    return this.http.get<any>(ENDPOINTS.SERVICES() + '/count');
  }

  public getServicesMap(): Observable<any> {
    return this.http.get<any>(ENDPOINTS.SERVICES() + '/map');
  }

  public getServicesLabels(): Observable<any> {
    return this.http.get<any>(ENDPOINTS.SERVICES() + '/labels');
  }

  public getServiceView(serviceId: string): Observable<ServiceView> {
    const options = { params: new HttpParams().set('messages', 'true') };
    return this.http.get<ServiceView>(ENDPOINTS.SERVICES() + '/' + serviceId, options);
  }

  public getService(serviceId: string): Observable<Service> {
    const options = { params: new HttpParams().set('messages', 'false') };
    return this.http.get<Service>(ENDPOINTS.SERVICES() + '/' + serviceId, options);
  }

  public createDynamicAPI(api: Api): Observable<Service> {
    return this.http.post<Service>(ENDPOINTS.SERVICES() + '/generic', api);
  }

  public deleteService(service: Service): Observable<Service> {
    return this.http.delete<Service>(ENDPOINTS.SERVICES() + '/' + service.id);
  }

  public getGenericResources(service: Service, page: number = 1, pageSize: number = 20): Observable<GenericResource[]> {
    const options = { params: new HttpParams().set('page', String(page - 1)).set('size', String(pageSize)) };
    return this.http.get<GenericResource[]>(ENDPOINTS.GENERIC_RESOURCE() + '/' + service.id, options);
  }

  public updateServiceMetadata(service: Service, metadata: Metadata): Observable<any> {
    return this.http.put<any>(ENDPOINTS.SERVICES() + '/' + service.id + '/metadata', metadata);
  }

  public updateServiceOperationProperties(service: Service, operationName: string, properties: OperationMutableProperties): Observable<any> {
    const options = { params: new HttpParams().set('operationName', operationName) };
    return this.http.put<any>(ENDPOINTS.SERVICES() + '/' + service.id + '/operation', properties, options);
  }

  public countGenericResources(service: Service): Observable<any> {
    return this.http.get<any>(ENDPOINTS.GENERIC_RESOURCE() + '/' + service.id + '/count');
  }
}