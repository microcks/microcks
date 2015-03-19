/*
* Licensed to Laurent Broudoux (the "Author") under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. Author licenses this
* file to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/
'use strict';

/**
 * Main module of the application.
 */
angular
  .module('microcksApp', [
    'microcksApp.services',
    'microcksApp.directives',
    'ngAnimate',
    'ngCookies',
    'ngResource',
    'ngRoute',
    'ngSanitize',
    'ui.bootstrap',
    'cgNotify', 
    'hljs',
    'angularFileUpload'
  ])
  .config(function ($routeProvider) {
    $routeProvider
      .when('/', {
        templateUrl: 'views/main.html',
        controller: 'MainCtrl'
      })
      .when('/about', {
        templateUrl: 'views/about.html',
        controller: 'AboutCtrl'
      })
      .when('/services', {
        templateUrl: 'views/services.html',
        controller: 'ServicesController',
        resolve: {
          services: function ($location, Service) {
            var searchObject = $location.search();
            if (Object.keys(searchObject).indexOf('searchTerm') != -1) {
              return Service.search({name: searchObject.searchTerm});
            }
            return Service.query({size: 20});
          }
        }
      })
      .when('/service/:id', {
        templateUrl: 'views/service.html',
        controller: 'ServiceController',
        resolve: {
          service: function ($route, Service) {
            return Service.get({serviceId: $route.current.params.id}).$promise;
          }
        }
      })
      .when('/tests/create', {
        templateUrl: 'views/test-form.html',
        controller: 'TestController'
      })
      .when('/tests/service/:serviceId', {
        templateUrl: 'views/tests.html',
        controller: 'TestsController',
        resolve: {
          tests: function ($route, TestsService) {
            return TestsService.listByService($route.current.params.serviceId, 0, 20);
          },
          service: function ($route, Service) {
            return Service.get({serviceId: $route.current.params.serviceId, messages: false}).$promise;
          }
        }
      })
      .when('/test/:id', {
        templateUrl: 'views/test.html',
        controller: 'TestController'
      })
      .when('/jobs', {
        templateUrl: 'views/jobs.html',
        controller: 'JobsController'
      })
      .when('/admin/invocations', {
        templateUrl: 'views/invocations.html',
        controller: 'AdminController'
      })
      .when('/admin/invocations/:serviceName/:serviceVersion', {
        templateUrl: 'views/invocations.html',
        controller: 'AdminController'
      })
      .when('/admin/export', {
        templateUrl: 'views/export.html',
        controller: 'AdminController'
      })
      .when('/admin/import', {
        templateUrl: 'views/import.html',
        controller: 'AdminController'
      })
      .when('/admin/delays', {
        templateUrl: 'views/delays.html',
        controller: 'AdminController'
      })
      .otherwise({
        redirectTo: '/'
      });
    })
    .config(function (hljsServiceProvider) {
      hljsServiceProvider.setOptions({
        tabReplace: '  ',
        languages: ['xml', 'json']
      });
  });
