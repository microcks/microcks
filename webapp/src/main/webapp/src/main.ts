import { enableProdMode } from '@angular/core';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';

import { AppModule } from './app/app.module';
import { environment } from './environments/environment';
import { environmentLoader } from './environments/environment-loader';


environmentLoader.then((env) => {

  /**
   * Replaces the environment values with the assets environment.json data.
   */
  Object.assign(environment, env);

  /**
   * Enables production mode and disables all logs.
   */
  if (environment.production) {
    enableProdMode();
    console.log = () => { };
    console.warn = () => { };
  }

  console.log('loaded environment: ' + JSON.stringify(env));

  let keycloakUrl = `${environment.apiUrl}api/keycloak/config`

  initKeycloakConnection(keycloakUrl);
});

function initKeycloakConnection(keycloakUrl: string) {
  var keycloak = window["Keycloak"](keycloakUrl);

  var loginOptions = { onLoad: 'login-required' };
  if (location.origin.indexOf("/localhost:") != -1) {
    console.log("[Microcks launch] Running locally so disabling Keycloak checkLogin Iframe to respect modern browser restrictions");
    loginOptions['checkLoginIframe'] = false;
  }
  keycloak.init(loginOptions).then(function (authenticated) {
    if (authenticated) {
      window['keycloak'] = keycloak;
      platformBrowserDynamic().bootstrapModule(AppModule)
        .catch(err => console.log(err));
    }
  }).catch(function () {
    alert('Failed to initialize authentication subsystem.');
  });
}

