/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { Component, OnInit, Input, SimpleChanges } from '@angular/core';
import { Router } from "@angular/router";
import { Observable } from 'rxjs';

import { DailyInvocations } from '../../models/metric.model';

import * as d3 from 'd3';
//import * as d3 from 'd3';

// Thanks to https://github.com/onokumus/metismenu/issues/110#issuecomment-317254128
//import * as $ from 'jquery';
declare let $: any;

const height = 340;
const padt = 20;
const padr = 20;
const padb = 60;
const padl = 40;

@Component({
  selector: 'hour-invocations-bar-chart',
  styleUrls: ['./hour-invocations-bar-chart.component.css'],
  template: `
    <div id="hourInvocationsBarChart"></div>
  `
})
export class HourInvocationsBarChartComponent implements OnInit {

  @Input('data')
  data: Observable<DailyInvocations>;

  @Input('hour')
  hour: number;

  resolvedData: DailyInvocations;

  width: number;
  vis = null;

  constructor(private router: Router) {
  }

  ngOnInit() {
    this.width = parseInt(d3.select('#hourInvocationsBarChart').style('width'));

    this.data.subscribe(invocationsData => {
      this.resolvedData = invocationsData;
      this.vis = d3.select('#hourInvocationsBarChart')
          .append('svg')
            .attr('width', this.width)
            .attr('height', height + padt + padb)
          .append('g')
            .attr('transform', 'translate(' + padl + ',' + padt + ')');

      
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['hour']) {
      this.updateChart(changes.hour.currentValue);
    }
  };

  updateChart(newHour: number) {
    if (this.resolvedData) {
      // Clear the elements inside of the div.
      this.vis.selectAll('*').remove();

      var x = d3.scale.ordinal().rangeRoundBands([0, this.width - padl - padr], 0.1);
      var y = d3.scale.linear().range([height, 0]);
      var yAxis = d3.svg.axis().scale(y).orient('left').tickSize(-this.width + padl + padr);
      var xAxis = d3.svg.axis().scale(x).orient('bottom');

      // compute index for extracting stats
      var startIndex = newHour * 60;
      var endIndex = ((newHour + 1) * 60) - 1;
        
      // transform minute object into an array of object(k, v) ascending sorted.
      var minuteData = d3.entries(this.resolvedData.minuteCount).sort(function(a, b) { return d3.ascending(parseInt(a.key), parseInt(b.key)); })
      minuteData  = $.map(minuteData, function(d, i) { return {'total': d.value} }).slice(startIndex, endIndex)

      var max = d3.max(minuteData, function(d) { return d['total'] });
      x.domain(d3.range(60).map(v => v.toString()));
      y.domain([0, max]);

      this.vis.append('g').attr('class', 'y axis').call(yAxis);
    
      this.vis.append('g').attr('class', "x axis")
        .attr('transform', 'translate(0,' + height + ')').call(xAxis)
          .selectAll('.x.axis g')
            .style('display', function (d, i) { return i % 3 != 0 ? 'none' : 'block' });

      var bars = this.vis.selectAll('g.bar')
        .data(minuteData)
          .enter().append('g').attr('class', 'invocations-bar').attr('transform', function (d, i) { return "translate(" + x(i.toString()) + ", 0)" });

      var tooltip = d3.select('body')
        .append('div')
          .style('position', 'absolute')
          .style('z-index', '10')
          .style('visibility', 'hidden')
          .style('padding', '0 6px')
          .style('color', '#fff')
          .style('background', '#292e34');

      bars.append('rect')
        .attr('width', function() { return x.rangeBand() })
        .attr('height', function(d) { return height - y(d['total']) })
        .attr('y', function(d) { return y(d['total']) })
        .on('mouseover', function(d) { tooltip.text(d['total'] + " hits"); return tooltip.style("visibility", "visible"); })
        .on('mousemove', function() { return tooltip.style("top", ((<any>d3.event).pageY - 10) + "px").style("left", ((<any>d3.event).pageX + 10) + "px"); })
        .on('mouseout', function() { return tooltip.style("visibility", "hidden" );});
    }
  }
}