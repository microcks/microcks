
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
import { Component, OnInit } from '@angular/core';

import { DonutChartConfig } from 'patternfly-ng/chart';
import { CardAction, CardConfig, CardFilter, CardFilterPosition } from 'patternfly-ng/card';
import { SparklineChartConfig, SparklineChartData } from 'patternfly-ng/chart/sparkline-chart';

import { InvocationsService } from '../../services/invocations.service';
import { ServicesService } from '../../services/services.service';
import { DailyInvocations } from '../../models/metric.model';

@Component({
  selector: "dashboard-page",
  templateUrl: "dashboard.page.html",
  styleUrls: ["dashboard.page.css"]
})
export class DashboardPageComponent implements OnInit {

  aDayLong: number = (1000 * 60 * 60 * 24);
  today = new Date();
  todayStr: string = this.invocationsSvc.formatDayDate(new Date());

  chartCardConfig: CardConfig;
  topCardConfig: CardConfig;
  repositoryCardConfig: CardConfig;

  actionsText: string = '';
  chartDates: any[] = ['dates'];
  chartConfig: SparklineChartConfig = {
    chartId: 'invocationsSparkline',
    chartHeight: 150,
    tooltipType: 'default'
  };
  chartData: SparklineChartData = {
    dataAvailable: false,
    total: 100,
    xData: this.chartDates,
    yData: ['used']
  };

  donutChartData: any[] = [
    ['REST', 0],
    ['DIRECT', 0],
    ['SOAP', 0],
    ['EVENT', 0],
    ['GRAPH', 0],
    ['GRPC', 0]
  ];
  largeConfig: DonutChartConfig = {
    chartId: 'repositoryDonut',
    chartHeight: 220,
    colors: {
      REST: '#89bf04',
      DIRECT: '#9c27b0',
      SOAP: '#39a5dc',
      EVENT: '#ec7a08',
      GRAPH: "#e10098",
      GRPC: '#379c9c'
    },
    /*
    data: {
      onclick: (data: any, element: any) => {
        alert('You clicked on donut arc: ' + data.id);
      }
    },
    */
    donut: { title: 'APIs & Services' },
    legend: { show: true }
  };

  topInvocations: DailyInvocations[];


  constructor(private servicesSvc: ServicesService, private invocationsSvc: InvocationsService) { }

  ngOnInit() {
    this.getServicesMap();
    this.getTopInvocations();
    this.getInvocationsTrend();

    this.chartCardConfig = {
      action: {
        hypertext: 'View All Events',
        iconStyleClass: 'fa fa-flag'
      },
      filters: [{
        title: 'Last 50 Days',
        value: '50'
      }, {
        default: true,
        title: 'Last 20 Days',
        value: '20'
      }, {
        title: 'Last 10 Days',
        value: '10'
      }],
      title: 'APIs | Services Mocks Invocations',
    } as CardConfig;

    this.topCardConfig = {
      filters: [{
        default: true,
        title: 'Today',
        value: 'today'
      }, {
        title: 'Yesterday',
        value: 'yesterday'
      }],
      title: 'Most Used APIs | Services',
    } as CardConfig;

    this.repositoryCardConfig = {
      title: 'APIs | Services Repository',
    } as CardConfig;
  }

  getServicesMap(): void {
    this.servicesSvc.getServicesMap().subscribe(
      results => {
        this.donutChartData = [
          ['REST', 0],
          ['DIRECT', 0],
          ['SOAP', 0],
          ['EVENT', 0],
          ['GRPC', 0],
          ['GRAPH', 0]
        ];
        for (let key in results) {
          if (key === 'GENERIC_REST' || key === 'GENERIC_EVENT') {
            this.donutChartData.push(['DIRECT', results[key]]);
          } else if (key === 'SOAP_HTTP') {
            this.donutChartData.push(['SOAP', results[key]]);
          } else if (key === 'GRAPHQL') {
            this.donutChartData.push(['GRAPH', results[key]]);
          } else {
            this.donutChartData.push([key, results[key]]);
          }
        }
      }
    );
  }

  getTopInvocations(day: Date = this.today): void {
    this.invocationsSvc.getTopInvocations(day).subscribe(results => {
      this.topInvocations = results.slice(0, 3);
    });
  }

  getInvocationsTrend(limit: number = 20): void {
    this.invocationsSvc.getInvocationsStatsTrend(limit).subscribe(
      results => {
        this.chartData.dataAvailable = false;
        this.chartData.xData = ['dates'];
        this.chartData.yData = ['hits'];
        for (let i = limit - 1; i >= 0; i--) {
          var pastDate: Date = new Date(this.today.getTime() - (i * this.aDayLong));
          this.chartData.xData.push(pastDate);
          var pastDateStr = this.invocationsSvc.formatDayDate(pastDate);
          var result = results[pastDateStr];
          if (result == null || result == undefined) {
            this.chartData.yData.push(0);
          } else {
            this.chartData.yData.push(result);
          }
        }
        //console.log('yData: ' + JSON.stringify(this.chartData.yData));
        //console.log('xData: ' + JSON.stringify(this.chartData.xData));
        this.chartData.dataAvailable = true;
      });
  }

  handleChartFilterSelect($event: CardFilter): void {
    this.getInvocationsTrend(+$event.value)
  }

  handleTopFilterSelect($event: CardFilter): void {
    if ($event.value === 'yesterday') {
      this.getTopInvocations(new Date(this.today.getTime() - this.aDayLong));
    } else {
      this.getTopInvocations();
    }
  }
}