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

angular.module('microcksApp.directives', [])
  .directive('messagesRow', function() {
    return {
      restrict: 'A',
      replace: false,
      templateUrl: 'directives/messages-row.html',
      link: function(scope, element, attrs) {
        scope.$watch(attrs.messages, function(messages) {
          if (messages != null){
            scope.pair = messages.find(function(item, index, array) {
              return item.request.name == attrs.request;
            });
          } else {
            scope.pair = null; 
          }
        });
      }
    }
  })
  .directive('testBarChart', function() {
    var baseWidth = 180;
    var baseHeight = 80;
  
    return {
      restrict: 'E',
      replace: true,
      scope: {
        dataPromise: '&data'
      },
      link: function(scope, element, attrs) {
        scope.dataPromise().then(function(testsData) {
          var maxval = 0;
          var minval = Number.MAX_VALUE;
          var chartData = testsData.slice(0, testsData.length).reverse()
            .map(function(item) {
              maxval = Math.max(maxval, item.elapsedTime);
              minval = Math.min(minval, item.elapsedTime);
              return {
                'id' : item.id,
                'success' : item.success,
                'testDate' : new Date(item.testDate),
                'elapsedTime' : item.elapsedTime
              };
            });
          if (maxval == 0){
            maxval = 1;
          }
          
          //var div = angular.element('<div id="'+attrs.chart+'"></div>');
          //element.prepend(div);
          
          var vis = d3.select('#'+attrs.chart).selectAll("div").data(chartData);
          vis.enter().append("div").attr("class", function(d) {
              if (d.success === true) {
                return "bar bar-success tooltipaware";
              } else {
                return "bar bar-failure tooltipaware";
              }
            });
          vis.exit().remove();

          vis.style("height", function(d) {
              if (d.elapsedTime == 0){
                d.elapsedTime = 1;
              }
              var h = d.elapsedTime * baseHeight / maxval;
              return h + "px";
          }).style("width", function(d) {
              var w = baseWidth / chartData.length;
              return w + "px";
          }).attr('data-placement', 'left').attr('title', function(d) {
              return "[" + d.testDate.toISOString() + "] : " + d.elapsedTime + " ms";
          }).on('click', function(d) {
              document.location.href = '#/test/' + d.id.toString();
          });
        }); 
      }
    };
});