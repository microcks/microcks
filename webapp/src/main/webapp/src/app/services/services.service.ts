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

import { Metadata } from '../models/commons.model';
import {
  Service,
  ServiceView,
  Api,
  GenericResource,
  OperationMutableProperties,
} from '../models/service.model';

@Injectable({ providedIn: 'root' })
export class ServicesService {
  private rootUrl = '/api';

  constructor(private http: HttpClient) {}

  public getServices(
    page: number = 1,
    pageSize: number = 20
  ): Observable<Service[]> {
    const options = {
      params: new HttpParams()
        .set('page', String(page - 1))
        .set('size', String(pageSize)),
    };
    return this.http.get<Service[]>(this.rootUrl + '/services', options);
  }

  public filterServices(
    labelsFilter: Map<string, string>,
    nameFilter: string
  ): Observable<Service[]> {
    let httpParams: HttpParams = new HttpParams();
    if (nameFilter != null) {
      httpParams = httpParams.set('name', nameFilter);
    }
    if (labelsFilter != null) {
      for (const key of Array.from(labelsFilter.keys())) {
        httpParams = httpParams.set('labels.' + key, labelsFilter.get(key) as string);
      }
    }

    const options = { params: httpParams };
    return this.http.get<Service[]>(this.rootUrl + '/services/search', options);
  }

  public countServices(): Observable<any> {
    return this.http.get<any>(this.rootUrl + '/services/count');
  }

  public getServicesMap(): Observable<any> {
    return this.http.get<any>(this.rootUrl + '/services/map');
  }

  public getServicesLabels(): Observable<any> {
    return this.http.get<any>(this.rootUrl + '/services/labels');
  }

  public getServiceView(serviceId: string): Observable<ServiceView> {
    const options = { params: new HttpParams().set('messages', 'true') };
    return this.http.get<ServiceView>(
      this.rootUrl + '/services/' + serviceId,
      options
    );
  }

  public getService(serviceId: string): Observable<Service> {
    const options = { params: new HttpParams().set('messages', 'false') };
    return this.http.get<Service>(
      this.rootUrl + '/services/' + serviceId,
      options
    );
  }

  public createDirectResourceAPI(api: Api): Observable<Service> {
    return this.http.post<Service>(this.rootUrl + '/services/generic', api);
  }

  public createDirectEventAPI(api: Api): Observable<Service> {
    return this.http.post<Service>(
      this.rootUrl + '/services/generic/event',
      api
    );
  }

  public deleteService(service: Service): Observable<Service> {
    return this.http.delete<Service>(this.rootUrl + '/services/' + service.id);
  }

  public getGenericResources(
    service: Service,
    page: number = 1,
    pageSize: number = 20
  ): Observable<GenericResource[]> {
    const options = {
      params: new HttpParams()
        .set('page', String(page - 1))
        .set('size', String(pageSize)),
    };
    return this.http.get<GenericResource[]>(
      this.rootUrl + '/genericresources/service/' + service.id,
      options
    );
  }

  public updateServiceMetadata(
    service: Service,
    metadata: Metadata
  ): Observable<any> {
    return this.http.put<any>(
      this.rootUrl + '/services/' + service.id + '/metadata',
      metadata
    );
  }

  public updateServiceOperationProperties(
    service: Service,
    operationName: string,
    properties: OperationMutableProperties
  ): Observable<any> {
    const options = {
      params: new HttpParams().set('operationName', operationName),
    };
    return this.http.put<any>(
      this.rootUrl + '/services/' + service.id + '/operation',
      properties,
      options
    );
  }

  public countGenericResources(service: Service): Observable<any> {
    return this.http.get<any>(
      this.rootUrl + '/genericresources/service/' + service.id + '/count'
    );
  }
}
