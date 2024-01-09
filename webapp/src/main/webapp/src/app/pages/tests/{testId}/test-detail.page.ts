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
import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, ParamMap } from "@angular/router";

import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { BsModalService } from 'ngx-bootstrap/modal';
import { BsModalRef } from 'ngx-bootstrap/modal/bs-modal-ref.service';
import { ListConfig } from 'patternfly-ng/list';

import { ServicesService } from '../../../services/services.service'
import { TestsService } from '../../../services/tests.service';
import { Service, ServiceType } from '../../../models/service.model';
import { TestResult } from '../../../models/test.model';
import { AddToCIDialogComponent } from './_components/add-to-ci.dialog';

@Component({
  selector: 'test-detail-page',
  templateUrl: './test-detail.page.html',
  styleUrls: ['./test-detail.page.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TestDetailPageComponent implements OnInit {

  readonly hlLang: string[] = ['json', 'xml', 'yaml'];

  now: number;
  test: Observable<TestResult>;
  service: Observable<Service>;
  testMessages: any = {};
  resultsListConfig: ListConfig;

  resolvedTest: TestResult;
  resolvedService: Service;
  modalRef: BsModalRef;

  constructor(private servicesSvc: ServicesService, private testsSvc: TestsService, 
    private modalService: BsModalService, private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.now = Date.now();
    this.test = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.testsSvc.getTestResult(params.get('testId')))
    );
    this.test.subscribe(res => {
      this.resolvedTest = res;
      this.service = this.servicesSvc.getService(res.serviceId);
      this.service.subscribe(svc => {
        this.resolvedService = svc;
        if (svc.type != ServiceType.EVENT) {
          res.testCaseResults.forEach(testCase => {
            var opName = this.encode(testCase.operationName);
            this.testsSvc.getMessages(res, testCase.operationName).subscribe(pairs => {
              this.testMessages[opName] = pairs;
            });
          });
        } else {
          res.testCaseResults.forEach(testCase => {
            var opName = this.encode(testCase.operationName);
            this.testsSvc.getEventMessages(res, testCase.operationName).subscribe(pairs => {
              this.testMessages[opName] = pairs;
            });
          });
        }
      });
    });

    this.resultsListConfig = {
      dblClick: false,
      emptyStateConfig: null,
      multiSelect: false,
      selectItems: false,
      selectionMatchProp: 'name',
      showCheckbox: false,
      showRadioButton: false,
      useExpandItems: true,
      hideClose: true
    } as ListConfig;
  }

  public openAddToCI(): void {
    const initialState = {
      closeBtnName: 'Close',
      test: this.resolvedTest,
      service: this.resolvedService
    };
    this.modalRef = this.modalService.show(AddToCIDialogComponent, {initialState});
  }

  public messagePairFor(operationName: string, requestName: string): any {
    var pairs = this.testMessages[this.encode(operationName)];
    if (pairs == undefined) {
      return undefined;
    }
    var result = pairs.filter(function(item, index, array) {
      return item.request.name == requestName; 
    })[0];
    return result;
  }
  public eventMessageFor(operationName: string, eventMessageName: string): any {
    var events = this.testMessages[this.encode(operationName)];
    if (events == undefined) {
      return undefined;
    }
    var result = events.filter(function(item, index, array) {
      return item.eventMessage.name == eventMessageName; 
    })[0];
    return result;
  }

  public encode(operation: string): string {
    operation = operation.replace(/\//g, '');
    operation = operation.replace(/\s/g, '');
    operation = operation.replace(/:/g, '');
    operation = operation.replace(/{/g, '');
    operation = operation.replace(/}/g, '');
    return encodeURIComponent(operation);
  }

  public formatErrorMessage(message: string): string {
    if (message != undefined) {
      var result = message.replace(/\\n/g, '\n');
      return result.replace(/<br\/>/g, '\n');
    }
    return "";
  }

  public timedOut(test: TestResult): boolean {
    return (test.inProgress && this.now > (test.testDate + test.timeout));
  }

  public displayTestType(type: string): string {
    return type.replace(/_/g, ' ');
  }
}