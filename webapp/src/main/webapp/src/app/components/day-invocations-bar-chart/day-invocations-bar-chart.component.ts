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
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

import { DailyInvocations } from '../../models/metric.model';

import * as d3 from 'd3';

@Component({
  selector: 'app-day-invocations-bar-chart',
  styleUrls: ['./day-invocations-bar-chart.component.css'],
  template: ` <div id="dayInvocationsBarChart"></div> `,
})
export class DayInvocationsBarChartComponent implements OnInit {
  @Input()
  data!: Observable<DailyInvocations>;

  @Output()
  hourChange: EventEmitter<any> = new EventEmitter<any>();

  constructor(private router: Router) {}

  ngOnInit() {
    const width = parseInt(d3.select('#dayInvocationsBarChart').style('width'));
    const height = 340;
    const padt = 20;
    const padr = 40;
    const padb = 60;
    const padl = 30;
    
    //const x = d3.scale.ordinal().rangeRoundBands([0, width - padl - padr], 0.1);
    //const y = d3.scale.linear().range([height, 0]);
    const x = d3.scaleBand().range([0, width - padl - padr]).padding(0.1);
    const y = d3.scaleLinear().range([height, 0]);

    /*
    const yAxis = d3.svg
      .axis()
      .scale(y)
      .orient('left')
      .tickSize(-width + padl + padr);
    const xAxis = d3.svg.axis().scale(x).orient('bottom');
    */
    const yAxis = d3.axisLeft(y).tickSize(-width + padl + padr);
    const xAxis = d3.axisBottom(x);

    this.data.subscribe((invocationsData) => {
      const vis = d3
        .select('#dayInvocationsBarChart')
          .append('svg')
            .attr('width', width)
            .attr('height', height + padt + padb)
          .append('g')
            .attr('transform', 'translate(' + padl + ',' + padt + ')');

      // Clear the elements inside of the div.
      vis.selectAll('*').remove();

      //const max = d3.max(d3.map(invocationsData.hourlyCount).values());
      const max = d3.max(Object.values(invocationsData.hourlyCount));
      x.domain([
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 
        '10', '11', '12', '13', '14', '15', '16', '17', '18', '19', 
        '20', '21', '22', '23',
      ]);
      // y.domain([0, max]);
      y.domain([0, max || 100]);

      // transform hourly object into an array of object(k, v) ascending sorted.
      let hourlyData = Object.entries(invocationsData.hourlyCount)
        .map(([key, value]) => ({ key, value, total: value }))
        .sort((a, b) => d3.ascending(parseInt(a.key), parseInt(b.key)));
      
      vis.append('g').attr('class', 'y axis').call(yAxis);

      vis.append('g')
          .attr('class', 'x axis')
          .attr('transform', 'translate(0,' + height + ')')
        .call(xAxis)
        .selectAll('.x.axis g')
          .style('display', (d, i) => (i % 3 != 0 ? 'none' : 'block'));

      const bars = vis.selectAll('g.bar')
        .data(hourlyData)
          .enter()
            .append('g')
              .attr('class', 'invocations-bar')
              .attr('transform', (d, i) => 'translate(' + x(i.toString()) + ', 0)');

      const tooltip = d3.select('body')
        .append('div')
          .style('position', 'absolute')
          .style('z-index', '10')
          .style('visibility', 'hidden')
          .style('padding', '0 6px')
          .style('color', '#fff')
          .style('background', '#292e34');

      bars.append('rect')
        .attr('width', function() { return x.bandwidth(); })
        .attr('height', function(this: SVGRectElement, d: { key: string; value: number; total?: number }) { return height - y(d.total || 0); })
        .attr('y', function(this: SVGRectElement, d: { key: string; value: number; total?: number }) { return y(d.total!); })
        .on('click', (e, d: { key: string; value: number; total?: number }) => this.hourChange.emit(parseInt(d.key)) )
        .on('mouseover', function(e, d: { key: string; value: any, total: any }) { 
          tooltip.text(d.total + ' hits'); 
          return tooltip.style('visibility', 'visible'); 
        })
        .on('mousemove', (event) => { 
          return tooltip.style('top', (event.pageY - 10) + 'px').style('left', (event.pageX + 10) + 'px'); 
        })
        .on('mouseout', function() { return tooltip.style('visibility', 'hidden'); });
    });
  }
}
