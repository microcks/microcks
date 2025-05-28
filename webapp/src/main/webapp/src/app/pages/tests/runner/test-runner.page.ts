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
import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';

import { Observable, Subscription, interval } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import {
  Notification,
  NotificationService,
  ToastNotificationListComponent,
} from '../../../components/patternfly-ng/notification';
import { ListConfig, ListModule } from '../../../components/patternfly-ng/list';

import { TimeAgoPipe } from '../../../components/time-ago.pipe';

import { Service } from '../../../models/service.model';
import { TestResult } from '../../../models/test.model';
import { ServicesService } from '../../../services/services.service';
import { TestsService } from '../../../services/tests.service';

@Component({
  selector: 'app-test-runner-page',
  templateUrl: 'test-runner.page.html',
  styleUrls: ['test-runner.page.css'],
  imports: [
    CommonModule,
    ListModule,
    RouterLink,
    TimeAgoPipe,
    ToastNotificationListComponent
  ]
})
export class TestRunnerPageComponent implements OnInit, OnDestroy {

  testId!: string;
  test!: Observable<TestResult>;
  service!: Observable<Service>;
  notifications: Notification[] = [];
  poller!: Subscription;
  resultsListConfig!: ListConfig;

  constructor(private servicesSvc: ServicesService, public testsSvc: TestsService, private notificationService: NotificationService,
              private route: ActivatedRoute, private router: Router) {
  }

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
    this.test = this.route.paramMap.pipe(
      switchMap((params: ParamMap) => {
        // (+) before `params.get()` turns the string into a number
        this.testId = params.get('testId')!;
        return this.testsSvc.getTestResult(this.testId);
      })
    );
    this.test.subscribe(res => {
      this.service = this.servicesSvc.getService(res.serviceId);
    });

    this.poller = interval(2000).pipe(
      switchMap(() => this.test = this.testsSvc.getTestResult(this.testId))
    ).subscribe(res => {
      if (!res.inProgress) {
        this.poller.unsubscribe();
      }
    });

    this.resultsListConfig = {
      dblClick: false,
      //emptyStateConfig: null,
      multiSelect: false,
      selectItems: false,
      selectionMatchProp: 'name',
      showCheckbox: false,
      showRadioButton: false,
      useExpandItems: true,
      hideClose: true
    } as ListConfig;
  }

  ngOnDestroy(): void {
    if (this.poller) {
      this.poller.unsubscribe();
    }
  }
}
