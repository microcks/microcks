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

angular.module('microcksApp')
  .controller('ServiceController', ['$rootScope', '$scope', '$location', 'service', 'Service', 'TestsService',
      function ($rootScope, $scope, $location, service, Service, TestsService) {

  $scope.view = service;
  $rootScope.service = $scope.view.service;

  $scope.serviceTests = function() {
    return TestsService.listByService($scope.view.service.id, 0, 20);
  }

  $scope.formatRequestUrl = function(operationName, dispatchCriteria) {
    var parts = {};
    var params = {};
    var partsCriteria = (dispatchCriteria.indexOf('?') == -1 ? dispatchCriteria : dispatchCriteria.substring(0, dispatchCriteria.indexOf('?')));
    var paramsCriteria = (dispatchCriteria.indexOf('?') == -1 ? null : dispatchCriteria.substring(dispatchCriteria.indexOf('?') + 1));
    partsCriteria.split('/').forEach(function(element, index, array) {
      if (element){
        parts[element.split('=')[0]] = element.split('=')[1];
      }
    });
    operationName = operationName.replace(/{(\w+)}/g, function(match, p1, string) {
      return parts[p1];
    });
    // Support also Postman syntax with /:part
    operationName = operationName.replace(/:(\w+)/g, function(match, p1, string) {
      return parts[p1];
    });
    if (paramsCriteria != null) {
      operationName += '?' + paramsCriteria.replace('?', '&');
    }

    // Remove leading VERB in Postman import case.
    operationName = $scope.removeVerbInUrl(operationName);
    return operationName;
  }

  $scope.removeVerbInUrl = function(operationName) {
    if (operationName.startsWith("GET ") || operationName.startsWith("PUT ")) {
      operationName = operationName.slice(4);
    } else if (operationName.startsWith("POST ")) {
      operationName = operationName.slice(5);
    } else if (operationName.startsWith("DELETE ")) {
      operationName = operationName.slice(7);
    }
    return operationName;
  }

  $scope.encodeUrl = function(url) {
    return url.replace(/\s/g, '%20');
  }
}]);
