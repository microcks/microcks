/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import { Component, OnInit, Input } from '@angular/core';
import { Router } from "@angular/router";
import { Observable } from 'rxjs';

import { TestResult } from '../../models/test.model';

import * as d3 from 'd3';

@Component({
  selector: 'test-bar-chart',
  styleUrls: ['./test-bar-chart.component.css'],
  template: `
    <div id="testBarChart"></div>
  `
})
export class TestBarChartComponent implements OnInit {
  @Input('data')
  data: Observable<TestResult[]>;

  baseWidth: number = 180;
  baseHeight: number = 80;

  constructor(private router: Router) {
  }

  ngOnInit() {
    this.data.subscribe(testsData => {
      var maxval = 0;
      var quinte = 0;
      var minval = Number.MAX_VALUE;
      var chartData = testsData.slice(0, testsData.length).reverse()
        .map(function(item) {
          maxval = Math.max(maxval, item.elapsedTime);
          quinte = Math.max(maxval / 5, item.elapsedTime);
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

      var vis = d3.select('#testBarChart').selectAll('div').data(chartData);
      vis.enter().append('div').attr('class', function(d) {
        if (d.success === true) {
          return "bar bar-success tooltipaware";
        } else {
          return "bar bar-failure tooltipaware";
        }
      }).style('height', function(d) {
        if (d.elapsedTime == 0){
          d.elapsedTime = 1;
        }
        var h = d.elapsedTime * 80 / maxval as number;
        // Enhanced display of lower value so that they're still visible.
        if (d.elapsedTime < quinte) {
          h = d.elapsedTime * 80 / quinte;
        }
        return h + 'px';
      })
      .style('width', function(d) {
        var w = 180 / chartData.length as number;
        return w + 'px';
      }).attr('data-placement', 'left').attr('title', function(d) {
        return "[" + d.testDate.toISOString() + "] : " + d.elapsedTime + " ms";
      }).on('click', function(d) {
        document.location.href = '/#/tests/' + d.id.toString();
        //this.navigateToTest(d.id.toString());
      });

      vis.exit().remove();

    });
  }

  public navigateToTest(testId: string): void {
    this.router.navigate(['/tests', testId]);
  }
}