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
import { Injectable } from "@angular/core";
import { HttpClient } from '@angular/common/http';
import { User } from "../models/user.model";

let DEFAULT_CONFIG: any = {
  mode: "dev",
  auth: {
    type: "keycloakjs"
  }
};

/**
 * A base service holding configuration of Microcks App.
 */
@Injectable({ providedIn: 'root' })
export class ConfigService {

  private config: any;

  
  constructor(private http: HttpClient) {
    let w: any = window;
    if (w["MicrocksConfig"]) {
      this.config = w["MicrocksConfig"];
      console.info("[ConfigService] Found app config.");
    } else {
      console.info("[ConfigService] App config not found!");
      this.config = DEFAULT_CONFIG;
    }
  }

  public authType(): string {
    if (!this.config.auth) {
      return null;
    }
    return this.config.auth.type;
  }

  public authToken(): string {
    if (!this.config.auth) {
      return null;
    }
    return this.config.auth.token;
  }

  public authRefreshPeriod(): number {
    if (!this.config.auth) {
      return null;
    }
    return this.config.auth.tokenRefreshPeriod;
  }

  public authData(): any {
    if (!this.config.auth) {
      return null;
    }
    return this.config.auth.data;
  }

  public logoutUrl(): string {
    if (!this.config.auth) {
      return null;
    }
    return this.config.auth.logoutUrl;
  }

  public user(): User {
    return <any>this.config.user;
  }

  public loadConfiguredFeatures() : Promise<any>  {
    console.info("[ConfigService] Completing config with additional features...");
    const featurePromise = this.http.get<any>('/api/features/config')
      .toPromise().then(results => {
        this.config.features = results;
        console.info("[ConfigService] Got result: " + JSON.stringify(this.config.features));
        return results;
      });
    return featurePromise;
  }

  public hasFeatureEnabled(feature: string): boolean {
    if (this.config.features) {
      let featureConfig = this.config.features[feature];
      return featureConfig['enabled'] === 'true';
    }
    return false;
  }

  public getFeatureProperty(feature: string, property: string): string {
    if (this.config.features) {
      let featureConfig = this.config.features[feature];
      return featureConfig[property];
    }
    return null;
  }
}