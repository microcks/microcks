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

services.factory('TestsService', ['$http', '$q', function($http, $q) {
  var testService = {
    listByService: function(serviceId, page, pageSize) {
      var delay = $q.defer();
      $http.get('/api/tests/service/' + serviceId, {page: page, size: pageSize})
      .success(function(data) {
        delay.resolve(data);
      });
      return delay.promise;
    },
    get: function(id) {
      var delay = $q.defer();
      $http.get('/api/tests/' + id)
      .success(function(data) {
        delay.resolve(data);
      });
      return delay.promise;
    },
    create: function(test) {
      var delay = $q.defer();
      $http.post('/api/tests', test)
      .success(function(data) {
        delay.resolve(data);
      });
      return delay.promise;
    },
    getMessages: function(test, operation) {
      var delay = $q.defer();
      var testCaseId = test.id + '-' + test.testNumber + '-' + operation;
      $http.get('/api/tests/' + test.id + '/messages/' + testCaseId)
      .success(function(data) {
        delay.resolve(data);
      });
      return delay.promise;
    }
  }
  return testService;
}]);
