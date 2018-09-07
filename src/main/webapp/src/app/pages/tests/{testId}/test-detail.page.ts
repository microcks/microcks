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