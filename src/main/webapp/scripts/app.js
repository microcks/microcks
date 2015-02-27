'use strict';

/**
 * @ngdoc overview
 * @name microcksApp
 * @description
 * # microcksApp
 *
 * Main module of the application.
 */
angular
  .module('microcksApp', [
    'microcksApp.services',
    'ngAnimate',
    'ngCookies',
    'ngResource',
    'ngRoute',
    'ngSanitize',
    'ui.bootstrap',
    'cgNotify'
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
        controller: 'ServicesController'
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
      .when('/jobs', {
        templateUrl: 'views/jobs.html',
        controller: 'JobsController'
      })
      .otherwise({
        redirectTo: '/'
      });
  });
