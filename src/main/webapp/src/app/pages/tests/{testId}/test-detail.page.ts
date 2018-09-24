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
import { ActivatedRoute, Router, ParamMap } from "@angular/router";

import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { ListConfig, ListEvent } from 'patternfly-ng/list';

import { ServicesService } from '../../../services/services.service'
import { TestsService } from '../../../services/tests.service';
import { Service } from '../../../models/service.model';
import { TestResult, TestStepResult } from '../../../models/test.model';

@Component({
  selector: 'test-detail-page',
  templateUrl: './test-detail.page.html',
  styleUrls: ['./test-detail.page.css']
})
export class TestDetailPageComponent implements OnInit {

  test: Observable<TestResult>;
  service: Observable<Service>;
  testMessages: any = {};
  resultsListConfig: ListConfig;

  constructor(private servicesSvc: ServicesService, private testsSvc: TestsService, private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.test = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.testsSvc.getTestResult(params.get('testId')))
    );
    this.test.subscribe(res => {
      this.service = this.servicesSvc.getService(res.serviceId);
      res.testCaseResults.forEach(testCase => {
        var opName = this.encode(testCase.operationName);
        this.testsSvc.getMessages(res, testCase.operationName).subscribe(pairs => {
          this.testMessages[opName] = pairs;
        });
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

  public encode(operation: string): string {
    operation = operation.replace(/\//g, '');
    operation = operation.replace(/\s/g, '');
    operation = operation.replace(/:/g, '');
    operation = operation.replace(/{/g, '');
    operation = operation.replace(/}/g, '');
    return encodeURIComponent(operation);
  }
}