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
    var params = {};
    dispatchCriteria.split('/').forEach(function(element, index, array) {
      if (element){
        params[element.split('=')[0]] = element.split('=')[1];
      }
    });
    operationName = operationName.replace(/{(\w+)}/g, function(match, p1, string) {
      return params[p1];
    });
    return operationName;
  }
}]);
