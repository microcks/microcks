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
import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, ParamMap, RouterLink } from '@angular/router';

import { Observable, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { BsDatepickerModule } from 'ngx-bootstrap/datepicker';

import { DayInvocationsBarChartComponent } from '../../../../components/day-invocations-bar-chart/day-invocations-bar-chart.component';
import { HourInvocationsBarChartComponent } from '../../../../components/hour-invocations-bar-chart/hour-invocations-bar-chart.component';

import { DailyInvocations } from '../../../..//models/metric.model';
import { MetricsService } from '../../../../services/metrics.service';


@Component({
  selector: 'app-invocations-service-page',
  templateUrl: './invocations-service.page.html',
  styleUrls: ['./invocations-service.page.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    RouterLink,
    BsDatepickerModule,
    DayInvocationsBarChartComponent,
    HourInvocationsBarChartComponent
  ]
})
export class InvocationsServicePageComponent implements OnInit {

  serviceName!: string;
  serviceVersion!: string;
  dailyInvocations?: Observable<DailyInvocations>;

  day: Date | null = null;
  hour: number = 0;
  serviceNameAndVersion!: string;

  constructor(private metricsSvc: MetricsService,
              private route: ActivatedRoute, private router: Router, private ref: ChangeDetectorRef) {
  }

  ngOnInit() {
    this.dailyInvocations = this.route.paramMap.pipe(
      switchMap((params: ParamMap) => {
        this.serviceName = params.get('serviceName')!;
        this.serviceVersion = params.get('serviceVersion')!;
        this.serviceNameAndVersion = this.serviceName + ':' + this.serviceVersion;
        return this.metricsSvc.getServiceInvocationStats(params.get('serviceName')!, params.get('serviceVersion')!, new Date());
      })
    );
  }

  updateServiceInvocationStats(): void {
    if (this.day != null) {
      this.dailyInvocations = this.metricsSvc.getServiceInvocationStats(this.serviceName, this.serviceVersion, this.day);
    }
  }

  changeDay(value: Date): void {
    this.day = value;
  }
  changeHour(value: number): void {
    this.hour = value;
  }
}
