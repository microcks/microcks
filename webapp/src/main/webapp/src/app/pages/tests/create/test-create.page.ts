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
import { Component, OnInit} from "@angular/core";
import { ActivatedRoute, Router, ParamMap } from "@angular/router";

import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';

import { ServicesService } from '../../../services/services.service';
import { TestsService } from '../../../services/tests.service';
import { Service } from '../../../models/service.model';
import { TestRunnerType } from "../../../models/test.model";

@Component({
  selector: "test-create-page",
  templateUrl: "test-create.page.html",
  styleUrls: ["test-create.page.css"]
})
export class TestCreatePageComponent implements OnInit {

  service: Observable<Service>;
  serviceId: string;
  testEndpoint: string;
  runnerType: TestRunnerType;
  showAdvanced: boolean = false;
  submitEnabled: boolean = false;
  notifications: Notification[];
  operationsHeaders: any = {
    'globals': []
  };

  constructor(private servicesSvc: ServicesService, public testsSvc: TestsService, private notificationService: NotificationService,
    private route: ActivatedRoute, private router: Router) {
  }

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
    this.service = this.route.paramMap.pipe(
      switchMap((params: ParamMap) => {
        // (+) before `params.get()` turns the string into a number
        this.serviceId = params.get('serviceId');
        return this.servicesSvc.getService(this.serviceId);
      })
    );
  }

  public addHeaderValue(operationName: string) {
    var operationHeaders = this.operationsHeaders[operationName];
    if (operationHeaders == null) {
      this.operationsHeaders[operationName] = [
        { 'name': "", 'values': "" }
      ];
    } else {
      this.operationsHeaders[operationName].push({ 'name': "", 'values': "" });
    }
  }

  public removeHeaderValue(operationName: string, headerIndex: number) {
    var operationHeaders = this.operationsHeaders[operationName];
    if (operationHeaders != null) {
      operationHeaders.splice(headerIndex, 1);
    }
  }

  public checkForm(): void {
    this.submitEnabled = (this.testEndpoint !== undefined && this.testEndpoint.length > 0 && this.runnerType !== undefined);
    console.log("submitEnabled: " + this.submitEnabled);
  }

  public cancel(): void {
    this.router.navigate(['/services', this.serviceId]);
  }

  public createTest(): void {
    var test = {serviceId: this.serviceId, testEndpoint: this.testEndpoint, runnerType: this.runnerType, operationsHeaders: this.operationsHeaders};
    console.log("[createTest] test: " + JSON.stringify(test));
    this.testsSvc.create(test).subscribe(
      {
        next: res => {
          this.notificationService.message(NotificationType.SUCCESS,
              String(res.id), "Test #" + res.id + " has been launched", false, null, null);
          this.router.navigate(['/tests/runner', res.id]);
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              "New test", "New test cannot be launched (" + err.message + ")", false, null, null);
        },
        complete: () => console.log('Observer got a complete notification')
      }
    );
  }
}