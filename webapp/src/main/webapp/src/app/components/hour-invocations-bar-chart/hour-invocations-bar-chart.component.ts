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
import {
  Component,
  OnInit,
  Input,
  SimpleChanges,
  OnChanges,
} from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

import { DailyInvocations } from '../../models/metric.model';

import * as d3 from 'd3';

const height = 340;
const padt = 20;
const padr = 20;
const padb = 60;
const padl = 40;

@Component({
  selector: 'app-hour-invocations-bar-chart',
  styleUrls: ['./hour-invocations-bar-chart.component.css'],
  template: ` <div id="hourInvocationsBarChart"></div> `,
})
export class HourInvocationsBarChartComponent implements OnInit, OnChanges {
  @Input()
  data!: Observable<DailyInvocations>;

  @Input()
  hour!: number;

  resolvedData?: DailyInvocations;

  width: number = 100;
  vis: any;

  constructor(private router: Router) {}

  ngOnInit() {
    this.width = parseInt(d3.select('#hourInvocationsBarChart').style('width'));

    this.data.subscribe((invocationsData) => {
      this.resolvedData = invocationsData;
      this.vis = d3
        .select('#hourInvocationsBarChart')
          .append('svg')
            .attr('width', this.width)
            .attr('height', height + padt + padb)
          .append('g')
            .attr('transform', 'translate(' + padl + ',' + padt + ')');
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    // TODO verify if needed?
    if (changes['hour']) {
      this.updateChart(changes['hour'].currentValue);
    }
  }

  updateChart(newHour: number) {
    if (this.resolvedData) {
      // Clear the elements inside of the div.
      this.vis.selectAll('*').remove();

      //const x = d3.scale.ordinal().rangeRoundBands([0, this.width - padl - padr], 0.1);
      //const y = d3.scale.linear().range([height, 0]);
      const x = d3.scaleBand().range([0, this.width - padl - padr]).padding(0.1);
      const y = d3.scaleLinear().range([height, 0]);

      /*
      const yAxis = d3.svg
        .axis()
        .scale(y)
        .orient('left')
        .tickSize(-this.width + padl + padr);
      const xAxis = d3.svg.axis().scale(x).orient('bottom');
      */
      const yAxis = d3.axisLeft(y).tickSize(-this.width + padl + padr);
      const xAxis = d3.axisBottom(x);

      // compute index for extracting stats
      const startIndex = newHour * 60;
      const endIndex = (newHour + 1) * 60;

      // transform minute object into an array of object(k, v) ascending sorted.
      /*
      let minuteData = d3
        .entries(this.resolvedData.minuteCount)
        .sort((a, b) => d3.ascending(parseInt(a.key), parseInt(b.key)));
      minuteData = $.map(minuteData, (d, i) => ({ total: d.value })).slice(
        startIndex,
        endIndex
      );
      */
      let minuteData = Object.entries(this.resolvedData.minuteCount)
        .map(([key, value]) => ({ key, value, total: value }))
        .sort((a, b) => d3.ascending(parseInt(a.key), parseInt(b.key)))
        .slice(startIndex, endIndex);

      const max = d3.max(minuteData, (d: { key: string; value: any, total: any; }) => d.total);
      x.domain(d3.range(60).map((v) => v.toString()));
      y.domain([0, max || 100]);

      this.vis.append('g').attr('class', 'y axis').call(yAxis);

      this.vis
        .append('g')
          .attr('class', 'x axis')
          .attr('transform', 'translate(0,' + height + ')')
        .call(xAxis)
        .selectAll('.x.axis g')
        .style('display', (d: any, i: number) => (i % 3 != 0 ? 'none' : 'block'));

      const bars = this.vis.selectAll('g.bar')
        .data(minuteData)
          .enter()
            .append('g')
              .attr('class', 'invocations-bar')
              .attr('transform', (d: any, i: number) => 'translate(' + x(i.toString()) + ', 0)');

      const tooltip = d3.select('body')
        .append('div')
          .style('position', 'absolute')
          .style('z-index', '10')
          .style('visibility', 'hidden')
          .style('padding', '0 6px')
          .style('color', '#fff')
          .style('background', '#292e34');

      bars
        .append('rect')
        .attr('width', () => x.bandwidth())
        .attr('height', (d: { key: string; value: number; total?: number }) => height - y(d.total || 0))
        .attr('y', (d: { key: string; value: number; total?: number }) => y(d.total!))
        .on('mouseover', (_: any, d: { key: string; value: any, total: any }) => {
          tooltip.text(d.total + ' hits');
          return tooltip.style('visibility', 'visible');
        })
        .on('mousemove', (event: { pageY: number; pageX: number; }) => {
          tooltip
            .style('top', event.pageY - 10 + 'px')
            .style('left', event.pageX + 10 + 'px')
        })
        .on('mouseout', () => tooltip.style('visibility', 'hidden'));
    }
  }
}
