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

var services = angular.module('microcksApp.services');

services.factory('InvocationsService', function($http, $q) {
  var invocationService = {
    getInvocationStats: function(day) {
      var delay = $q.defer();
      var url = '/api/invocations/global';
      if (day) url += '?day=' + day.toLocaleFormat('%Y%m%d'); // 20150326;
      $http.get(url).success(function(data) {
        delay.resolve(data)
      });
      return delay.promise;
    },
    getTopInvocations: function(day) {
      var delay = $q.defer();
      var url = '/api/invocations/top';
      if (day) url += '?day=' + day.toLocaleFormat('%Y%m%d'); // 20150326;
      $http.get(url).success(function(data) {
        delay.resolve(data)
      });
      return delay.promise;
    },
    getServiceInvocationStats: function(service, version, day) {
      var delay = $q.defer();
      var url = '/api/invocations/' + service + "/" + version;
      if (day) url += '?day=' + day.toLocaleFormat('%Y%m%d'); // 20150326;
      $http.get(url).success(function(data) {
        delay.resolve(data)
      });
      return delay.promise;
    }
  }
  return invocationService;
});