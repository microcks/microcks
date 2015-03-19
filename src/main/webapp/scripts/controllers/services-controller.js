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
  .controller('ServicesController', function ($rootScope, $scope, $location, notify, services, Service) {
  
  $scope.page = 1;
  $scope.pageSize = 20;
  $scope.services = services;
  
  if (Object.keys($location.search()).indexOf('searchTerm') != -1 ) {
    $scope.term = $location.search().searchTerm;
  } else {
    // We need to paginate...
    Service.count().$promise.then(function(result) {
      $scope.count = result.counter;
    });  
  }
  
  $scope.$watch('page', function(newValue, oldValue) {
    if (newValue != oldValue){
      $scope.services = Service.query({page: newValue-1, size: $scope.pageSize}); 
    }
  });
});