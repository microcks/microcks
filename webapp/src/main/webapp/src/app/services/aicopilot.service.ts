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

import { Service, Exchange } from '../models/service.model';

@Injectable({ providedIn: 'root' })
export class AICopilotService {

  private rootUrl = '/api';

  constructor(private http: HttpClient) { }

  public getSamplesSuggestions(service: Service, operationName: string, numberOfSamples: number = 2): Observable<Exchange[]> {
    const options = { params: new HttpParams().set('operation', operationName) };
    return this.http.get<Exchange[]>(this.rootUrl + '/copilot/samples/' + service.id, options);
  }

  public launchSamplesGeneration(service: Service): Observable<any> {
    return this.http.get<any>(this.rootUrl + '/copilot/samples/' + service.id);
  }

  public getGenerationTaskStatus(taskId: string): Observable<any> {
    return this.http.get<any>(this.rootUrl + '/copilot/samples/task/' + taskId + '/status');
  }

  public addSamplesSuggestions(service: Service, operationName: string, exchanges: Exchange[]): Observable<any> {
    const options = { params: new HttpParams().set('operation', operationName) };
    return this.http.post<any>(this.rootUrl + '/copilot/samples/' + service.id, exchanges, options);
  }

  public removeExchanges(service: Service, exchangeSelection: any): Observable<any> {
    return this.http.post<any>(this.rootUrl + '/copilot/samples/' + service.id + '/cleanup', exchangeSelection);
  }
}
