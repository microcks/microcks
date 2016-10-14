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
  .controller('JobsController', ['$rootScope', '$scope', '$modal', '$location', 'notify', 'Job',
      function ($rootScope, $scope, $modal, $location, notify, Job) {

  $scope.page = 1;
  $scope.pageSize = 20;

  if (!$scope.filterTerm) {
    // We need to paginate...
    Job.count().$promise.then(function(result) {
      $scope.count = result.counter;
    });
  }

  $scope.listPage = function(page) {
    $scope.jobs = Job.query({page: page-1, size: $scope.pageSize});
  }

  $scope.filterJobs = function() {
    $scope.jobs = Job.query({name: $scope.filterTerm});
  }

  $scope.$watch('page', function(newValue, oldValue) {
    if (newValue != oldValue){
      $scope.jobs = Job.query({page: newValue-1, size: $scope.pageSize});
    }
  });

  $scope.addJob = function(job) {
    job = new Job({name: "", repositoryUrl: ""});
    var modalInstance = show(job, 'edit-job.html');
    modalInstance.result.then(function(result) {
      var job = result.job;
      job.$save(function(result) {
        notify({
          message: 'Job "' + job.name + '" has been created !',
          classes: 'alert-success'
        });
        $scope.page = 1;
        $scope.listPage(1);
      });
    });
  }

  $scope.editJob = function(job) {
    var modalInstance = show(job, 'edit-job.html');
    modalInstance.result.then(function(result) {
      var job = result.job;
      job.$save(function(result) {
        notify('Job "' + job.name + '" has been updated !');
      });
    });
  }

  $scope.activateJob = function(job) {
    var original = job;
    job.$activate(function(result) {
       notify('Job "' + job.name + '" has been activated !');
       original.active = true;
    });
  }

  $scope.startJob = function(job) {
    job.$start(function(result) {
       notify('Job "' + job.name + '" has been executed !');
    });
  }

  $scope.deleteJob = function(job) {
    job.$remove(function(result) {
      for (var i in $scope.jobs) {
        if ($scope.jobs[i] == job) {
          $scope.jobs.splice(i, 1);
        }
      }
      notify({
        message: 'Job "' + $scope.job.name + '" has been removed !',
        classes: 'alert-success'
      });
    });
  }

  function show(job, template) {
    return $modal.open({
      templateUrl: 'views/dialogs/' + template,
      //controller: modalController,
      controller: 'JobsModalController',
      resolve: {
        job: function () {
          return job;
        }
      }
    });
  }

  /*
  function modalController($scope, $modalInstance, job) {
    $scope.job = job;
    $scope.ok = function(job) {
      $modalInstance.close({
        job: job
      });
    };
    $scope.cancel = function() {
      $modalInstance.dismiss('cancel');
    };
  }
  */
}]);

angular.module('microcksApp')
  .controller('JobsModalController', ['$scope', '$modalInstance', 'job', function ($scope, $modalInstance, job) {

    $scope.job = job;
    $scope.ok = function(job) {
      $modalInstance.close({
        job: job
      });
    };
    $scope.cancel = function() {
      $modalInstance.dismiss('cancel');
    };
  }]);
