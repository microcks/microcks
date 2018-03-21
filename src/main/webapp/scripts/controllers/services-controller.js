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
  .controller('ServicesController', ['$rootScope', '$scope', '$modal', '$location', '$timeout', 'notify', 'services', 'Service',
      function ($rootScope, $scope, $modal, $location, $timeout, notify, services, Service) {

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

  $scope.addDynamicService = function(service) {
    service = new Service({name: "", version: "", resource: ""});
    var modalInstance = show(service, 'edit-genericservice.html');
    modalInstance.result.then(function(result) {
      var service = result.service;
      service.$createDynamic(function(result) {
          notify({
            message: 'Dynamic service "' + service.name + "' has been created !",
            classes: 'alert-success'
          });
        }, function(result) {
          if (result.status == 409) {
            notify({
              message: 'Service "' + service.name + "' already exists with version " + service.version,
              classes: 'alert-warning'
            });
          }
        });
    });
  };

  $scope.$watch('$viewContentLoaded', function(){
    // Here your view content is fully loaded !!
    if ($scope.term != undefined) {
      // Put hightlighting in the rendering queue using timeout.
      $timeout(function(){
        $('#services').highlight($scope.term);
      }, 100);
    }
  });

  function show(service, template) {
    return $modal.open({
      templateUrl: 'views/dialogs/' + template,
      controller: 'ServiceModalController',
      resolve: {
        service: function () {
          return service;
        }
      }
    });
  }

}]);

angular.module('microcksApp')
  .controller('ServiceModalController', ['$scope', '$modalInstance', 'service', function ($scope, $modalInstance, service) {

    $scope.service = service;
    $scope.ok = function(service) {
      $modalInstance.close({
        service: service
      });
    };
    $scope.cancel = function() {
      $modalInstance.dismiss('cancel');
    };
  }]);
