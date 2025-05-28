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
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';

import {
  CardConfig,
  CardFilter,
  CardModule,
} from '../../components/patternfly-ng/card';
import { DonutChartConfig, DonutChartModule } from '../../components/patternfly-ng/chart';
import { SparklineChartData, SparklineChartConfig, SparklineChartModule } from '../../components/patternfly-ng/chart/sparkline-chart';

import { ScoreTreemapComponent } from '../../components/score-treemap/score-treemap.component';

import { ConfigService } from '../../services/config.service';
import { MetricsService } from '../../services/metrics.service';
import { ServicesService } from '../../services/services.service';

@Component({
  selector: 'app-dashboard-page',
  templateUrl: 'dashboard.page.html',
  styleUrls: ['dashboard.page.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    CardModule,
    DonutChartModule,
    ScoreTreemapComponent,
    SparklineChartModule
  ]
})
export class DashboardPageComponent implements OnInit {
  aDayLong: number = 1000 * 60 * 60 * 24;
  today = new Date();
  todayStr: string;

  servicesCount = 0;
  aggregatesCount = 0;

  chartCardConfig: CardConfig = {
    action: {
      hypertext: 'View All Events',
      iconStyleClass: 'fa fa-flag',
    },
    filters: [
      { title: 'Last 50 Days', value: '50' },
      { default: true, title: 'Last 20 Days', value: '20' },
      { title: 'Last 10 Days', value: '10' }
    ],
    title: 'APIs | Services Mocks Invocations',
  } as CardConfig;

  topCardConfig: CardConfig = {
    filters: [
      { default: true, title: 'Today', value: 'today' },
      { title: 'Yesterday', value: 'yesterday' }
    ],
    title: 'Most Used APIs | Services',
  } as CardConfig;

  repositoryCardConfig: CardConfig = {
    title: 'APIs | Services Repository',
  } as CardConfig;

  testConformanceCardConfig: CardConfig = {
    title: 'API | Services Conformance Risks',
  } as CardConfig;

  testResultsCardConfig: CardConfig = {
    filters: [
      { default: true, title: 'Last 7 Days', value: '7' },
      { title: 'Last 15 Days', value: '15', }
    ],
    title: 'API | Services Tests',
  } as CardConfig;

  actionsText = '';
  chartDates: any[] = ['dates'];
  chartConfig: SparklineChartConfig = {
    chartId: 'invocationsSparkline',
    chartHeight: 150,
    tooltipType: 'default',
  };
  chartData: SparklineChartData = {
    dataAvailable: false,
    total: 100,
    xData: this.chartDates,
    yData: ['used'],
  };

  repositoryDonutChartData: any[] = [
    ['REST', 0],
    ['DIRECT', 0],
    ['SOAP', 0],
    ['EVENT', 0],
    ['GRAPH', 0],
    ['GRPC', 0],
  ];
  repositoryDonutChartConfig: DonutChartConfig = {
    chartId: 'repositoryDonut',
    chartHeight: 220,
    colors: {
      REST: '#89bf04',
      DIRECT: '#9c27b0',
      SOAP: '#39a5dc',
      EVENT: '#ec7a08',
      GRAPH: '#e10098',
      GRPC: '#379c9c',
    },
    /*
    data: {
      onclick: (data: any, element: any) => {
        alert('You clicked on donut arc: ' + data.id);
      }
    },
    */
    donut: { title: 'APIs & Services' },
    legend: { show: true },
  };

  testResultsDonutChartData: any[] = [
    ['SUCCESS', 3],
    ['FAILURE', 5],
  ];
  testResultsDonutChartConfig: DonutChartConfig = {
    chartId: 'testsDonut',
    chartHeight: 220,
    colors: {
      SUCCESS: '#7bb33d',
      FAILURE: '#d1d1d1',
    },
    donut: { title: 'Tests' },
    legend: { show: true },
  };

  topInvocations: any; //DailyInvocations[];
  conformanceScores: any;

  constructor(
    private servicesSvc: ServicesService,
    private config: ConfigService,
    private metricsSvc: MetricsService,
    private ref: ChangeDetectorRef
  ) {
    this.todayStr = metricsSvc.formatDayDate(this.today);
  }

  ngOnInit() {
    this.getServicesMap();
    this.getTopInvocations();
    this.getInvocationsTrend();
    this.getAggregatedTestConformanceMetrics();
    this.getLatestTestsTrend();
  }

  isRepositoryPanelDisplayed(): boolean {
    return this.servicesCount > 1;
  }
  isTestsPanelDisplayed(): boolean {
    return this.aggregatesCount > 1;
  }

  getServicesMap(): void {
    this.servicesSvc.getServicesMap().subscribe((results) => {
      this.servicesCount = Object.keys(results).length;
      this.repositoryDonutChartData = [
        ['REST', 0],
        ['DIRECT', 0],
        ['SOAP', 0],
        ['EVENT', 0],
        ['GRPC', 0],
        ['GRAPH', 0],
      ];
      let directCount = 0;
      for (const key in results) {
        if (key === 'GENERIC_REST' || key === 'GENERIC_EVENT') {
          directCount += results[key];
          this.addServiceCountToDonutTuple('DIRECT', results[key]);
        } else if (key === 'SOAP_HTTP') {
          this.addServiceCountToDonutTuple('SOAP', results[key]);
        } else if (key === 'GRAPHQL') {
          this.addServiceCountToDonutTuple('GRAPH', results[key]);
        } else {
          this.addServiceCountToDonutTuple(key, results[key]);
        }
      }
      this.ref.detectChanges();
    });
  }

  private addServiceCountToDonutTuple(tupleName: string, results: number): void {
    let tuple = this.repositoryDonutChartData.find((tuple) => tuple[0] === tupleName);
    if (tuple) {
      tuple[1] += results;
    }
  }

  getTopInvocations(day: Date = this.today): void {
    this.metricsSvc.getTopInvocations(day).subscribe((results) => {
      this.topInvocations = results.slice(0, 3);
      this.ref.detectChanges();
    });
  }

  getInvocationsTrend(limit: number = 20): void {
    this.metricsSvc.getInvocationsStatsTrend(limit).subscribe((results) => {
      this.chartData.dataAvailable = false;
      this.chartData.xData = ['dates'];
      this.chartData.yData = ['hits'];
      for (let i = limit - 1; i >= 0; i--) {
        const pastDate: Date = new Date(
          this.today.getTime() - i * this.aDayLong
        );
        this.chartData.xData.push(pastDate);
        const pastDateStr = this.metricsSvc.formatDayDate(pastDate);
        const result = results[pastDateStr];
        if (result == null || result == undefined) {
          this.chartData.yData.push(0);
        } else {
          this.chartData.yData.push(result);
        }
      }
      this.chartData.dataAvailable = true;
      this.ref.detectChanges();
    });
  }

  getAggregatedTestConformanceMetrics(): void {
    this.metricsSvc
      .getAggregatedTestConformanceMetrics()
      .subscribe((results) => {
        this.aggregatesCount = results.length;
        const children = results.map(function(metric) {
          return {
            name: metric.name,
            value: metric.weight,
            score: metric.value,
          };
        });
        this.conformanceScores = {
          name: 'root',
          children: [{ name: 'domains', children, score: 1 }],
        };
        this.ref.detectChanges();
      });
  }

  getLatestTestsTrend(limit: number = 7): void {
    this.metricsSvc.getLatestTestsTrend(limit).subscribe((results) => {
      let successCount = 0;
      let failureCount = 0;
      results.forEach((result) => {
        result.success ? successCount++ : failureCount++;
      });
      const ratio = successCount / results.length;
      if (ratio > 0.66) {
        this.testResultsDonutChartConfig.colors.SUCCESS = '#7bb33d';
      } else if (ratio < 0.33) {
        this.testResultsDonutChartConfig.colors.SUCCESS = '#dd1f26';
      } else {
        this.testResultsDonutChartConfig.colors.SUCCESS = '#efaa00';
      }
      this.testResultsDonutChartData = [
        ['SUCCESS', successCount],
        ['FAILURE', failureCount],
      ];
      this.ref.detectChanges();
    });
  }

  handleChartFilterSelect($event: CardFilter): void {
    this.getInvocationsTrend(+$event.value);
  }

  handleTopFilterSelect($event: CardFilter): void {
    if ($event.value === 'yesterday') {
      this.getTopInvocations(new Date(this.today.getTime() - this.aDayLong));
    } else {
      this.getTopInvocations();
    }
  }

  handleTestsFilterSelect($event: CardFilter): void {
    this.getLatestTestsTrend(+$event.value);
  }
  
  repositoryFilterFeatureLabelKey(): string {
    return this.config.getFeatureProperty('repository-filter', 'label-key');
  }
}
