'use strict';

(function () {
  /**
   * Snippet extracted from Keycloak examples
   */
  var auth = {};
  var app = angular.module('microcksApp');

  angular.element(document).ready(function () {
    var keycloak = new Keycloak('api/keycloak/config');
    auth.loggedIn = false;

    keycloak.init({ onLoad: 'login-required' }).success(function (authenticated) {
      auth.loggedIn = true;
      auth.keycloak = keycloak;
      auth.logout = function() {
        auth.loggedIn = false;
        auth.keycloak = null;
        window.location = keycloak.createLogoutUrl();
      };
      app.factory('Auth', function () {
        return auth;
      });
      angular.bootstrap(document, ['microcksApp']);
    }).error(function () {
      window.location.reload();
    });
  });

  app.factory('Auth', function () {
    return auth;
  });

  app.factory('authInterceptor', function ($q, Auth) {
    return {
      request: function (config) {
        var delay = $q.defer();

        if (Auth.keycloak && Auth.keycloak.token) {
          Auth.keycloak.updateToken(30).success(function () {
            config.headers = config.headers || {};
            config.headers['Authorization'] = 'Bearer ' + Auth.keycloak.token;
            delay.resolve(config);
          }).error(function () {
            window.location.reload();
          });
        } else {
          delay.resolve(config);
        }
        return delay.promise;
      }
    };
  });

  app.config(function ($httpProvider) {
    $httpProvider.interceptors.push('authInterceptor');
  });

})();
