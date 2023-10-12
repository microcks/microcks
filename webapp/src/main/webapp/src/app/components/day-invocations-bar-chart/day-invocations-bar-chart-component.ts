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
import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { Router } from "@angular/router";
import { Observable } from 'rxjs';

import { DailyInvocations } from '../../models/metric.model';

import * as d3 from 'd3';
//import * as d3 from 'd3';

// Thanks to https://github.com/onokumus/metismenu/issues/110#issuecomment-317254128
//import * as $ from 'jquery';
declare let $: any;

@Component({
  selector: 'day-invocations-bar-chart',
  styleUrls: ['./day-invocations-bar-chart.component.css'],
  template: `
    <div id="dayInvocationsBarChart"></div>
  `
})
export class DayInvocationsBarChartComponent implements OnInit {

  @Input('data')
  data: Observable<DailyInvocations>;

  @Output('onHourChange')
  onHourChange: EventEmitter<any> = new EventEmitter<any>();

  constructor(private router: Router) {
  }

  ngOnInit() {
    var width = parseInt(d3.select('#dayInvocationsBarChart').style('width')),
        height = 340,
        padt = 20, padr = 40, padb = 60, padl = 30,
        x = d3.scale.ordinal().rangeRoundBands([0, width - padl - padr], 0.1),
        y = d3.scale.linear().range([height, 0]),
        yAxis = d3.svg.axis().scale(y).orient('left').tickSize(-width + padl + padr),
        xAxis = d3.svg.axis().scale(x).orient('bottom');

    this.data.subscribe(invocationsData => {
      var vis = d3.select('#dayInvocationsBarChart')
        .append('svg')
          .attr('width', width)
          .attr('height', height + padt + padb)
        .append('g')
          .attr('transform', 'translate(' + padl + ',' + padt + ')');
      
      // Clear the elements inside of the div.
      vis.selectAll('*').remove();

      var max = d3.max(d3.map(invocationsData.hourlyCount).values());
      x.domain(['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12', '13', '14', '15', '16', '17', '18', '19', '20', '21', '22', '23']);
      y.domain([0, max]);
      
      // transform hourly object into an array of object(k, v) ascending sorted.
      var hourlyData = d3.entries(invocationsData.hourlyCount).sort(function(a, b) { return d3.ascending(parseInt(a.key), parseInt(b.key)); })
      hourlyData  = $.map(hourlyData, function(d, i) { return {'total': d.value} })

      vis.append('g').attr('class', 'y axis').call(yAxis);

      vis.append('g').attr('class', 'x axis')
        .attr('transform', 'translate(0,' + height + ')').call(xAxis)
          .selectAll('.x.axis g')
            .style('display', function (d, i) { return i % 3 != 0 ? 'none' : 'block' });
      
      
      var bars = vis.selectAll('g.bar')
        .data(hourlyData)
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
        .on('click', (d, i) => this.onHourChange.emit(i) )
        .on('mouseover', function(d) { tooltip.text(d['total'] + " hits"); return tooltip.style("visibility", "visible"); })
        .on('mousemove', function() { return tooltip.style("top", ((<any>d3.event).pageY - 10) + "px").style("left", ((<any>d3.event).pageX + 10) + "px"); })
        .on('mouseout', function() { return tooltip.style("visibility", "hidden"); });
    });
  }
}