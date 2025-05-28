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

import { ConfigService } from './config.service';
import { APIPackage, APIVersion } from '../models/hub.model';

@Injectable({ providedIn: 'root' })
export class HubService {

  private rootUrl: string | null = null;
  // private rootUrl: string = 'http://localhost:4000/api';
  // private rootUrl: string = 'http://microcks-hub/api';

  constructor(private http: HttpClient, private config: ConfigService) {
    this.rootUrl = this.config.getFeatureProperty('microcks-hub', 'endpoint');
  }

  public getPackages(): Observable<APIPackage[]> {
    this.ensureRootUrl();
    return this.http.get<APIPackage[]>(this.rootUrl + '/mocks');
  }

  public getPackage(name: string): Observable<APIPackage> {
    this.ensureRootUrl();
    return this.http.get<APIPackage>(this.rootUrl + '/mocks/' + name);
  }

  public getLatestAPIVersions(packageName: string): Observable<APIVersion[]> {
    this.ensureRootUrl();
    return this.http.get<APIVersion[]>(this.rootUrl + '/mocks/' + packageName + '/apis');
  }

  public getAPIVersion(packageName: string, apiVersionName: string): Observable<APIVersion> {
    this.ensureRootUrl();
    return this.http.get<APIVersion>(this.rootUrl + '/mocks/' + packageName + '/apis/' + apiVersionName);
  }

  public importAPIVersionContractContent(contractUrl: string, mainArtifact: boolean = true): Observable<any> {
    this.ensureRootUrl();
    const options = { params: new HttpParams().set('url', contractUrl).set('mainArtifact', String(mainArtifact)) };
    return this.http.post<any>('/api/artifact/download', {}, options);
  }

  private ensureRootUrl(): void {
    if (this.rootUrl == null) {
      this.rootUrl = this.config.getFeatureProperty('microcks-hub', 'endpoint');
    }
  }
}
