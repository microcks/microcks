import { IAuthenticationService } from "./auth.service";
import { ConfigService } from "./config.service";
import { KeycloakAuthenticationService } from "./auth-keycloak.service";
import { HttpClient } from "@angular/common/http";


export function AuthenticationServiceFactory(http: HttpClient, config: ConfigService): IAuthenticationService {
  console.info("[AuthenticationServiceFactory] Creating AuthenticationService...");
  if (config.authType() === "keycloakjs") {
    console.info("[AuthenticationServiceFactory] Creating keycloak.js auth service.");
    return new KeycloakAuthenticationService(http, config);
  } else {
    console.error("[AuthenticationServiceFactory] Unsupported auth type: %s", config.authType());
    return null;
  }
};


export let AuthenticationServiceProvider =
{
  provide: IAuthenticationService,
  useFactory: AuthenticationServiceFactory,
  deps: [HttpClient, ConfigService]
};