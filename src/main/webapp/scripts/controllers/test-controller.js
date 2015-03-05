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
  .controller('TestController', function ($rootScope, $scope, $location, notify, TestsService) {
  
  $scope.service = $rootScope.service;
  $scope.testEndpoint;
  $scope.runnerType;
  
  $scope.cancel = function() {
    $location.path('/service/' + $scope.service.id);
  }
  
  $scope.createTest = function() {
    var test = {serviceId: $scope.service.id, 
                testEndpoint: $scope.testEndpoint, 
                runnerType: $scope.runnerType};
    TestsService.create(test).then(function(result) {
      notify({
        message: 'Test for "' + $scope.testEndpoint + '" has been created !',
        classes: 'alert-success'
      });
    });
    $location.path('/service/' + $scope.service.id);
  }
});