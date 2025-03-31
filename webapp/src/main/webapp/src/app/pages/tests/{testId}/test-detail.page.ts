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
import { CommonModule } from '@angular/common';
import { ActivatedRoute, ParamMap, RouterLink } from '@angular/router';

import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { BsModalRef, BsModalService } from 'ngx-bootstrap/modal';
import { TabsModule } from 'ngx-bootstrap/tabs';
import { HighlightAuto } from 'ngx-highlightjs';

import { ListConfig, ListModule } from '../../../components/patternfly-ng/list';

import { ServicesService } from '../../../services/services.service';
import { TestsService } from '../../../services/tests.service';
import { Exchange, RequestResponsePair, Service, ServiceType, UnidirectionalEvent } from '../../../models/service.model';
import { TestResult } from '../../../models/test.model';
import { AddToCIDialogComponent } from './_components/add-to-ci.dialog';
import { TimeAgoPipe } from '../../../components/time-ago.pipe';

@Component({
  selector: 'app-test-detail-page',
  templateUrl: './test-detail.page.html',
  styleUrls: ['./test-detail.page.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    HighlightAuto,
    ListModule,
    RouterLink,
    TabsModule,
    TimeAgoPipe
  ]
})
export class TestDetailPageComponent implements OnInit {
  readonly hlLang: string[] = ['json', 'xml', 'yaml'];

  now!: number;
  test!: Observable<TestResult>;
  service!: Observable<Service>;
  testMessages: Record<string, Exchange[]> = {};
  resultsListConfig!: ListConfig;

  resolvedTest!: TestResult;
  resolvedService!: Service;
  modalRef?: BsModalRef;

  constructor(private servicesSvc: ServicesService, private testsSvc: TestsService,
              private modalService: BsModalService, private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.now = Date.now();
    this.test = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.testsSvc.getTestResult(params.get('testId')!))
    );
    this.test.subscribe(res => {
      this.resolvedTest = res;
      this.service = this.servicesSvc.getService(res.serviceId);
      this.service.subscribe(svc => {
        this.resolvedService = svc;
        if (svc.type != ServiceType.EVENT) {
          res.testCaseResults.forEach(testCase => {
            const opName = this.encode(testCase.operationName);
            this.testsSvc.getMessages(res, testCase.operationName).subscribe(pairs => {
              this.testMessages[opName] = pairs;
            });
          });
        } else {
          res.testCaseResults.forEach(testCase => {
            const opName = this.encode(testCase.operationName);
            this.testsSvc.getEventMessages(res, testCase.operationName).subscribe(pairs => {
              this.testMessages[opName] = pairs;
            });
          });
        }
      });
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

  public openAddToCI(): void {
    const initialState = {
      closeBtnName: 'Close',
      test: this.resolvedTest,
      service: this.resolvedService
    };
    this.modalRef = this.modalService.show(AddToCIDialogComponent, {initialState});
  }

  public messagePairFor(operationName: string, requestName: string): any {
    const pairs = this.testMessages[this.encode(operationName)];
    if (pairs == undefined) {
      return undefined;
    }
    const result = pairs.filter(function(item, index, array) {
      return (item as RequestResponsePair).request.name == requestName;
    })[0];
    return result;
  }
  public eventMessageFor(operationName: string, eventMessageName: string): any {
    const events = this.testMessages[this.encode(operationName)];
    if (events == undefined) {
      return undefined;
    }
    const result = events.filter(function(item, index, array) {
      return (item as UnidirectionalEvent).eventMessage.name == eventMessageName;
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
      const result = message.replace(/\\n/g, '\n');
      return result.replace(/<br\/>/g, '\n');
    }
    return '';
  }

  public timedOut(test: TestResult): boolean {
    return (test.inProgress && this.now > (test.testDate + test.timeout));
  }

  public displayTestType(type: string): string {
    return type.replace(/_/g, ' ');
  }
}
