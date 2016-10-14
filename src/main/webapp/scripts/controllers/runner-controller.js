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
  .controller('RunnerController', ['$rootScope', '$scope', '$interval', '$routeParams', 'notify', 'TestsService',
      function ($rootScope, $scope, $interval, $routeParams, notify, TestsService) {

  $scope.service = $rootScope.service;
  $scope.testStepResults = {};

  var stop = $interval(function() {
    TestsService.get($routeParams.id).then(function(result) {
      $scope.test = result;
      if (!$scope.test.inProgress) {
        $scope.stopPolling();
      }
    });
  }, 2000);

  $scope.loadTest = function() {
    TestsService.get($routeParams.id).then(function(result) {
      $scope.test = result;
    });
  }

  $scope.stopPolling = function() {
    if (angular.isDefined(stop)) {
      $interval.cancel(stop);
      stop = undefined;
    }
  };

  $scope.$on('$destroy', function() {
    // Make sure that the interval is destroyed too
    $scope.stopPolling();
  });
}]);
