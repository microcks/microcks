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
  .controller('AdminController', ['$rootScope', '$scope', '$routeParams', 'notify', 'FileUploader', 'Service', 'InvocationsService',
      function ($rootScope, $scope, $routeParams, notify, FileUploader, Service, InvocationsService) {

  $scope.day;
  $scope.hour = 0;
  $scope.serviceName = $routeParams.serviceName;
  $scope.serviceVersion = $routeParams.serviceVersion;

  $scope.invocationStats = null;
  $scope.selectedServices = { ids: {} };
  $scope.uploader = new FileUploader( {
    url: '/api/import'
  });

  $scope.getAllServices = function() {
    $scope.services = Service.query();
    $scope.services.$promise.then(function(result) {
      $scope.halfServices = $scope.services.slice(0, ($scope.services.length / 2) + 1);
      $scope.secondHalfServices = $scope.services.slice($scope.halfServices.length);
    })
  };

  $scope.openDatePicker = function($event) {
    $event.preventDefault();
    $event.stopPropagation();
    $scope.pickerOpened = true;
  };

  $scope.updateInvocationStats = function() {
    $scope.getInvocationStats($scope.day);
    $scope.getTopInvocations($scope.day);
  };

  $scope.getInvocationStats = function(day) {
    InvocationsService.getInvocationStats(day).then(function(result) {
      $scope.invocationStats = result;
    });
  };

  $scope.getTopInvocations = function(day) {
    InvocationsService.getTopInvocations(day).then(function(result) {
      $scope.topInvocations = result;
    })
  };

  $scope.updateServiceInvocationStats = function() {
    $scope.getServiceInvocationStats($scope.day);
  };

  $scope.getServiceInvocationStats = function(day) {
    console.log('$scope.service: ' + $scope.service);
    console.log('$scope.serviceName: ' + $scope.serviceName);
    console.log('day: ' + day);
    console.log('$scope.day: ' + $scope.day);
    InvocationsService.getServiceInvocationStats($scope.serviceName, $scope.serviceVersion, day).then(function(result) {
      $scope.invocationStats = result
    });
  };

  $scope.updateOperationDelay = function(service, operation) {
    var data = { operationName: operation.name, delay: operation.defaultDelay };
    service.$updateOperationDelay(data);
  };

  $scope.export = function() {
    var downloadPath = '/api/export?';
    Object.keys($scope.selectedServices.ids).forEach(function(element, index, array) {
      downloadPath += '&serviceIds=' + element;
    });
    window.open(downloadPath, '_blank', '');
  };

  $scope.import = function() {
    var fileName = $scope.uploader.queue[0].file.name;
    $scope.uploader.queue[0].upload();
    notify({
      message: 'File "' + fileName + '" has been imported !',
      classes: 'alert-success'
    });
    $scope.uploader.queue = [];
  };
}]);
