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
          if (messages != null) {
            scope.pair = messages.filter(function(item, index, array) {
              return item.request.name == attrs.request;
            })[0];  // Took the first result only.
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
  })
  .directive('dayInvocationsBarChart', function() {
    var tip = d3.tip().attr('class', 'd3-tip')
        .html(function(d) { return '<span>' + d.total + '</span>' + ' invocations' })
        .offset([-12, 0]);
    
    var width = 460, height = 300,
        padt = 20, padr = 20, padb = 60, padl = 30,
        x = d3.scale.ordinal().rangeRoundBands([0, width - padl - padr], 0.1),
        y = d3.scale.linear().range([height, 0]),
        yAxis = d3.svg.axis().scale(y).orient('left').tickSize(-width + padl + padr),
        xAxis = d3.svg.axis().scale(x).orient('bottom');
  
    return {
      restrict: 'E',
      scope: {
        stats: '='
      },
      link: function(scope, element, attrs) {
        var vis = d3.select(element[0])
          .append('svg')
            .attr('width', width)
            .attr('height', height + padt + padb)
          .append('g')
            .attr('transform', 'translate(' + padl + ',' + padt + ')');
        
        scope.$watch('stats', function(newStatistics, oldStatistics) {
          // clear the elements inside of the directive
          vis.selectAll('*').remove();
          
          // if 'val' is undefined, exit
          if (!newStatistics) {
            return;
          }
          
          var max = d3.max(d3.values(newStatistics.hourlyCount));
          x.domain(d3.range(24));
          y.domain([0, max]);
          
          // transform hourly object into an array of object(k, v) ascending sorted.
          var hourlyData = d3.entries(newStatistics.hourlyCount).sort(function(a, b) { return d3.ascending(parseInt(a.key), parseInt(b.key)); })
          hourlyData  = $.map(hourlyData, function(d, i) { return {'total': d.value} })
          
          vis.call(tip);
          vis.append("g").attr("class", "y axis").call(yAxis);
    
          vis.append("g").attr("class", "x axis")
            .attr('transform', 'translate(0,' + height + ')').call(xAxis)
              .selectAll('.x.axis g')
                .style('display', function (d, i) { return i % 3 != 0 ? 'none' : 'block' });
          
          var bars = vis.selectAll('g.bar')
            .data(hourlyData)
               .enter().append('g').attr('class', 'bar').attr('transform', function (d, i) { return "translate(" + x(i) + ", 0)" });
    
          bars.append('rect')
            .attr('width', function() { return x.rangeBand() })
            .attr('height', function(d) { return height - y(d.total) })
            .attr('y', function(d) { return y(d.total) })
            .on('mouseover', tip.show)
            .on('mouseout', tip.hide)
        });
      }
    };
  })
  .directive('hourInvocationsBarChart', function() {
    var tip = d3.tip().attr('class', 'd3-tip')
        .html(function(d) { return '<span>' + d.total + '</span>' + ' invocations' })
        .offset([-12, 0]);
    
    var width = 460, height = 300,
        padt = 20, padr = 20, padb = 60, padl = 30,
        x = d3.scale.ordinal().rangeRoundBands([0, width - padl - padr], 0.1),
        y = d3.scale.linear().range([height, 0]),
        yAxis = d3.svg.axis().scale(y).orient('left').tickSize(-width + padl + padr),
        xAxis = d3.svg.axis().scale(x).orient('bottom');
  
    return {
      restrict: 'E',
      replace: true,
      scope: {
        stats: '=',
        hour: '='
      },
      link: function(scope, element, attrs) {
        var vis = d3.select(element[0])
          .append('svg')
            .attr('width', width)
            .attr('height', height + padt + padb)
          .append('g')
            .attr('transform', 'translate(' + padl + ',' + padt + ')');
        
        scope.$watch('stats', function(newStatistics, oldStatistics) {
          // clear the elements inside of the directive
          vis.selectAll('*').remove();
        });
        
        scope.$watch('hour', function(newHour, oldHour) {
          // clear the elements inside of the directive
          vis.selectAll('*').remove();
          
          // if 'val' is undefined, exit
          if (!newHour) {
            return;
          }
          
          // transform minute object into an array of object(k, v) ascending sorted.
	      var minuteData = d3.entries(scope.stats.minuteCount).sort(function(a, b) { return d3.ascending(parseInt(a.key), parseInt(b.key)); })
	      minuteData  = $.map(minuteData, function(d, i) { return {'total': d.value} }).slice(startIndex, endIndex)
          
          var max = d3.max(minuteData, function(d) { return d.total });
          x.domain(d3.range(60));
          y.domain([0, max]);
          
          vis.call(tip);
          vis.append("g").attr("class", "y axis").call(yAxis);
    
          vis.append("g").attr("class", "x axis")
            .attr('transform', 'translate(0,' + height + ')').call(xAxis)
              .selectAll('.x.axis g')
                .style('display', function (d, i) { return i % 3 != 0 ? 'none' : 'block' });
          
          var bars = vis.selectAll('g.bar')
            .data(minuteData)
               .enter().append('g').attr('class', 'bar').attr('transform', function (d, i) { return "translate(" + x(i) + ", 0)" });
    
          bars.append('rect')
            .attr('width', function() { return x.rangeBand() })
            .attr('height', function(d) { return height - y(d.total) })
            .attr('y', function(d) { return y(d.total) })
            .on('mouseover', tip.show)
            .on('mouseout', tip.hide)
        });
      }
    };
  })
  .directive('topInvocations', function() {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'directives/top-invocations.html',
      scope: {
        stats: '=',
      },
      link: function(scope, element, attrs) {
        scope.$watch('stats', function(topInvocations) {
          if (topInvocations != null) {
            scope.topInvocations = topInvocations;
          }
        });
      }
    }
});