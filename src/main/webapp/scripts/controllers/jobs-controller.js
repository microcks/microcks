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
  .controller('JobsController', function ($rootScope, $scope, $modal, $location, notify, Job) {
  
  $scope.page = 0;
  $scope.pageSize = 20;
  
  $scope.jobsCount = 100;
  
  $scope.listPage = function(page) {
    $scope.jobs = Job.query({page: page, pageSize: $scope.pageSize}); 
  }
  
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
      controller: modalController,
      resolve: {
        job: function () {
          return job;
        }
      }
    });
  }
  
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
});