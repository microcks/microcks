import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Contract } from '../models/service.model';

@Injectable({ providedIn: 'root' })
export class ContractsService {

  private rootUrl: string = '/api';

  constructor(private http: HttpClient) { }

  public listByServiceId(serviceId: string): Observable<Contract[]> {
    return this.http.get<Contract[]>(this.rootUrl + '/resources/service/' + serviceId);
  }
}