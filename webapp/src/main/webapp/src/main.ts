import { enableProdMode } from '@angular/core';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';

import { AppModule } from './app/app.module';
import { environment } from './environments/environment';

if (environment.production) {
  enableProdMode();
}

var keycloak = window["Keycloak"](location.origin + '/api/keycloak/config');
var loginOptions = {onLoad: 'login-required'};
console.log("location.origin: " + location.origin);
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
}).catch(err => {
  console.log(err);
  alert('Failed to initialize authentication subsystem.');
});
