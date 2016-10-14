'use strict';

/**
 * @ngdoc function
 * @name microcksApp.controller:MainCtrl
 * @description
 * # MainCtrl
 * Controller of the microcksApp
 */
angular.module('microcksApp')
  .controller('MainCtrl', ['$scope', function ($scope) {
    $scope.awesomeThings = [
      'HTML5 Boilerplate',
      'AngularJS',
      'Karma'
    ];
  }]);
