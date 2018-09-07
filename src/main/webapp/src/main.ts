import { enableProdMode } from '@angular/core';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';

import { AppModule } from './app/app.module';
import { environment } from './environments/environment';

if (environment.production) {
  enableProdMode();
}

var keycloak = window["Keycloak"]('http://localhost:8080/api/keycloak/config');
keycloak.init({onLoad: 'login-required'}).success(function (authenticated) {
  if (authenticated) {
      window['keycloak'] = keycloak;
      platformBrowserDynamic().bootstrapModule(AppModule)
      .catch(err => console.log(err));
  }
}).error(function () {
  alert('Failed to initialize authentication subsystem.');
});
