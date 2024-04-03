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
import { Component, OnInit} from "@angular/core";
import { ActivatedRoute, Router, ParamMap } from "@angular/router";

import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';

import { ServicesService } from '../../../services/services.service';
import { TestsService } from '../../../services/tests.service';
import { SecretsService } from '../../../services/secrets.service';
import { Service } from '../../../models/service.model';
import { TestRunnerType, OAuth2ClientContext } from "../../../models/test.model";
import { Secret } from '../../../models/secret.model';

@Component({
  selector: "test-create-page",
  templateUrl: "test-create.page.html",
  styleUrls: ["test-create.page.css"]
})
export class TestCreatePageComponent implements OnInit {

  service: Observable<Service>;
  resolvedService: Service;
  serviceId: string;
  testEndpoint: string;
  runnerType: TestRunnerType;
  showAdvanced: boolean = false;
  submitEnabled: boolean = false;
  notifications: Notification[];
  timeout: number = 10000;
  secretId: string;
  secretName: string;
  operationsHeaders: any = {
    'globals': []
  };
  secrets: Secret[];
  oAuth2ClientContext: OAuth2ClientContext = new OAuth2ClientContext();

  filteredOperation: string;
  removedOperationsNames: string[] = [];

  constructor(private servicesSvc: ServicesService, public testsSvc: TestsService, private secretsSvc: SecretsService,
    private notificationService: NotificationService, private route: ActivatedRoute, private router: Router) {
  }

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
    let fromTestId = null;
    this.service = this.route.paramMap.pipe(
      switchMap((params: ParamMap) => {
        // (+) before `params.get()` turns the string into a number
        this.serviceId = params.get('serviceId');
        if (params.has('fromTest')) {
          fromTestId = params.get('fromTest');
        }
        return this.servicesSvc.getService(this.serviceId);
      })
    );
    this.service.subscribe( service => {
      this.resolvedService = service;
      if (fromTestId != null) {
        this.initializeFromPreviousTestResult(fromTestId);
      }
    });
  }

  getSecrets(page: number = 1): void {
    this.secretsSvc.getSecrets(page).subscribe(results => this.secrets = results);
  }

  initializeFromPreviousTestResult(testId: string): void {
    this.testsSvc.getTestResult(testId).subscribe(
      {
        next: res => {
          this.notificationService.message(NotificationType.SUCCESS,
              "New Test", "Test has been initialized from " + testId, false, null, null);
          this.testEndpoint = res.testedEndpoint;
          this.runnerType = res.runnerType;
          // Complete with optional properties.
          if (res.operationsHeaders) {
            this.operationsHeaders = res.operationsHeaders;
          }
          if (res.timeout) {
            this.timeout = res.timeout;
          }
          if (res.secretRef) {
            this.secretId = res.secretRef.secretId;
            this.secretName = res.secretRef.name;
          }
          if (res.authorizedClient) {
            this.oAuth2ClientContext.grantType = res.authorizedClient.grantType;
            this.oAuth2ClientContext.tokenUri = res.authorizedClient.tokenUri;
            if (res.authorizedClient.scopes && res.authorizedClient.scopes.length > 0) {
              this.oAuth2ClientContext.scopes = res.authorizedClient.scopes.replace('openid', ' ').trim();
            }
          }
          // Finalize with filtered operations.
          if (this.resolvedService.type === "EVENT" && res.testCaseResults.length == 1) {
            this.filteredOperation = res.testCaseResults[0].operationName;
          } else {
            for (var i=0; i<this.resolvedService.operations.length; i++) {
              var operation = this.resolvedService.operations[i];
              var foundOperation = res.testCaseResults.find(tc => tc.operationName === operation.name);
              if (foundOperation == undefined || foundOperation == null) {
                this.removedOperationsNames.push(operation.name);
              }
            }
          }
          this.checkForm();
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              "New Test", "Test cannot be initialized from " + testId, false, null, null);
        },
        complete: () => console.log('Observer got a complete notification'),
      }
    );
  }

  public showAdvancedPanel(show: boolean) {
    if (show && (this.secrets == undefined || this.secrets.length == 0)) {
      this.getSecrets();
    }
    this.showAdvanced = show;
  }
  public updateSecretProperties(event: any): void {
    var secretId = event.target.value;
    if ('undefined' != event.target.value) {
      for (var i=0; i<this.secrets.length; i++) {
        var secret = this.secrets[i];
        if (secretId === secret.id) {
          this.secretName = secret.name;
          break;
        }
      };
    } else {
      this.secretName = null;
    }
  }
  public updateGrantType(event: any): void {
      var secretId = event.target.value;
      if ('undefined' === event.target.value) {
        this.oAuth2ClientContext.grantType = undefined;
        this.checkForm();
      }
    }
  public filterOperation(operationName: string) : void {
    if (this.removedOperationsNames.includes(operationName)) {
      this.removedOperationsNames.splice(this.removedOperationsNames.indexOf(operationName), 1);
    } else {
      this.removedOperationsNames.push(operationName);
    }
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
    this.submitEnabled = (this.testEndpoint !== undefined && this.testEndpoint.length > 0 && this.runnerType !== undefined)
        && (this.resolvedService.type != "EVENT" || (this.filteredOperation !== undefined));
    // Check also the OAuth2 parameters.
    if (this.submitEnabled && this.oAuth2ClientContext.grantType !== undefined) {
      this.submitEnabled = (this.oAuth2ClientContext.tokenUri !== undefined && this.oAuth2ClientContext.tokenUri.length > 0
          && this.oAuth2ClientContext.clientId !== undefined && this.oAuth2ClientContext.clientId.length > 0
          && this.oAuth2ClientContext.clientSecret !== undefined && this.oAuth2ClientContext.clientSecret.length > 0);
    }
    console.log("[createTest] submitEnabled: " + this.submitEnabled);
  }

  public cancel(): void {
    this.router.navigate(['/services', this.serviceId]);
  }

  public createTest(): void {
    // Build filtered operations array first.
    let filteredOperations = [];
    if (this.filteredOperation !== undefined) {
      filteredOperations.push(this.filteredOperation)
    } else {
      if (this.removedOperationsNames.length > 0) {
        this.resolvedService.operations.forEach(op => {
          if (!this.removedOperationsNames.includes(op.name)) {
            filteredOperations.push(op.name)
          }
        });
      }
    }
    // Reset OAuth2 parameters if not set.
    if (this.oAuth2ClientContext.grantType === undefined) {
      this.oAuth2ClientContext = undefined;
    }
    // Then, create thee test invoking the API.
    var test = {serviceId: this.serviceId, testEndpoint: this.testEndpoint, runnerType: this.runnerType, 
        timeout: this.timeout, secretName: this.secretName,
        filteredOperations: filteredOperations, operationsHeaders: this.operationsHeaders,
        oAuth2Context: this.oAuth2ClientContext};
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