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
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, ParamMap, RouterLink } from '@angular/router';

import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { PaginationConfig, PaginationEvent, PaginationModule } from '../../components/patternfly-ng/pagination';

import { TestBarChartComponent } from '../../components/test-bar-chart/test-bar-chart.component';
import { TimeAgoPipe } from '../../components/time-ago.pipe';

import { ServicesService } from '../../services/services.service';
import { TestsService } from '../../services/tests.service';
import { Service } from '../../models/service.model';
import { TestResult } from '../../models/test.model';

@Component({
  selector: 'app-tests-page',
  templateUrl: './tests.page.html',
  styleUrls: ['./tests.page.css'],
  imports: [
    CommonModule,
    PaginationModule,
    RouterLink,
    TestBarChartComponent,
    TimeAgoPipe
  ]
})
export class TestsPageComponent implements OnInit {

  now!: number;
  service!: Service;
  testResults!: Observable<TestResult[]>;
  testResultsCount: number = 0;

  resolvedTestResults!: TestResult[];
  paginationConfig: PaginationConfig = new PaginationConfig;

  constructor(private servicesSvc: ServicesService, public testsSvc: TestsService, private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.now = Date.now();
    const serviceViewObs = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.servicesSvc.getService(params.get('serviceId')!))
    );
    serviceViewObs.subscribe(result => {
      this.service = result;
    });

    this.testResults = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.testsSvc.listByServiceId(params.get('serviceId')!))
    );
    const testResultsCountObs = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.testsSvc.countByServiceId(params.get('serviceId')!))
    );
    testResultsCountObs.subscribe(result => {
      this.testResultsCount = result.counter;
      this.paginationConfig.totalItems = this.testResultsCount;
    });

    this.testResults.subscribe( results => {
      this.resolvedTestResults = results;
    });

    this.paginationConfig = {
      pageNumber: 1,
      pageSize: 20,
      pageSizeIncrements: [],
      totalItems: 20
    } as PaginationConfig;
  }

  listByServiceId(page: number = 1): void {
    this.testResults = this.testsSvc.listByServiceId(this.service.id, page);
    this.testResults.subscribe( results => {
      this.resolvedTestResults = results;
    });
  }

  handlePageSize($event: PaginationEvent) {
    // this.updateItems();
  }

  handlePageNumber($event: PaginationEvent) {
    this.listByServiceId($event.pageNumber);
  }

  numberOfTestSteps(testResult: TestResult): number {
    return testResult.testCaseResults.map( tc => tc.testStepResults.length ).reduce( (acc, cur) => acc + cur);
  }

  public timedOut(test: TestResult): boolean {
    return (test.inProgress && this.now > (test.testDate + test.timeout));
  }

  public displayTestType(type: string): string {
    return type.replace(/_/g, ' ');
  }
}
