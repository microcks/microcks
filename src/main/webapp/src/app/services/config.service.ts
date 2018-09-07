import { Injectable } from "@angular/core";
import { User } from "../models/user.model";

let DEFAULT_CONFIG: any = {
  mode: "dev",
  auth: {
    type: "keycloakjs"
  }
};

/**
 * An abstract base class for services that need to make API calls to Github.
 */
@Injectable()
export class ConfigService {

  private config: any;

  constructor() {
    let w: any = window;
    if (w["MicrocksConfig"]) {
      this.config = w["MicrocksConfig"];
      console.info("[ConfigService] Found app config.");
    } else {
      console.error("[ConfigService] App config not found!");
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
}