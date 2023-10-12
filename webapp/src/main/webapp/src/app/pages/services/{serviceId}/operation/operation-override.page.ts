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
import { ActivatedRoute, Router, ParamMap } from "@angular/router";

import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';

import { Operation, Service, ServiceType, ServiceView, OperationMutableProperties, ParameterConstraint, ParameterLocation } from '../../../../models/service.model';
import { ServicesService } from '../../../../services/services.service';
import { ConfigService } from '../../../../services/config.service';

@Component({
  selector: 'operation-override-page',
  templateUrl: './operation-override.page.html',
  styleUrls: ['./operation-override.page.css']
})
export class OperationOverridePageComponent implements OnInit {

  serviceId: string;
  operationName: string
  serviceView: Observable<ServiceView>;
  resolvedServiceView: ServiceView;
  operation: Operation;
  newOperation: Operation;
  notifications: Notification[];
  frequencies: string[];
  paramConstraints: any = {
    'header': [],
    'query': []
  }

  dispatchersByServiceType: any = {
    'REST': [ 
      {"value": "SEQUENCE", "label": "SEQUENCE"},
      {"value": "URI_PARAMS", "label": "URI PARAMS"}, 
      {"value": "URI_PARTS", "label": "URI PARTS"}, 
      {"value": "URI_ELEMENTS", "label": "URI ELEMENTS"}, 
      {"value": "SCRIPT", "label": "SCRIPT"}, 
      {"value": "JSON_BODY", "label": "JSON BODY"},
      {"value": "FALLBACK", "label": "FALLBACK"},
    ],
    'SOAP': [ 
      {"value": "QUERY_MATCH", "label": "QUERY MATCH"},
      {"value": "SCRIPT", "label": "SCRIPT"}, 
      {"value": "FALLBACK", "label": "FALLBACK"},
    ],
    'EVENT': [],
    'GRPC': [
      {"value": "JSON_BODY", "label": "JSON BODY"},
      {"value": "FALLBACK", "label": "FALLBACK"}
    ],
    'GRAPHQL': [
      {"value": "QUERY_ARGS", "label": "QUERY_ARGS"},
      {"value": "JSON_BODY", "label": "JSON BODY"},
      {"value": "SCRIPT", "label": "SCRIPT"}, 
      {"value": "FALLBACK", "label": "FALLBACK"}
    ]
  }


  fallback = `{
  "dispatcher": "URI_PARTS",
  "dispatcherRules": "name",
  "fallback": "John Doe"
}`;

  examplePayload =  `{
  "name": "Abbey Brune",
  "country": "Belgium",
  "type": "Brown ale",
  "rating": 4.2,
  "references": [
    { "referenceId": 1234 },
    { "referenceId": 5678 }
  ]
}`;

  equalsOperator = `{
  "exp": "/country",
  "operator": "equals",
  "cases": {
    "Belgium": "Accepted",
    "default": "Not accepted"
  }
}`;

  rangeOperator = `{
  "exp": "/rating",
  "operator": "range",
  "cases": {
    "[4.2;5.0]": "Top notch",
    "[3;4.2[": "Medium",
    "default": "Not accepted"
  }
}`;

  sizeOperator = `{
  "exp": "/references",
  "operator": "size",
  "cases": {
    "[2;100]": "Good references",
    "default": "Not enough references"
  }
}`;

  regexpOperator = `{
  "exp": "/type",
  "operator": "regexp",
  "cases": {
    ".*[Aa][Ll][Ee].*": "Ale beers",
    "default": "Not accepted"
  }
}`;

  presenceOperator = `{
  "exp": "/name",
  "operator": "presence",
  "cases": {
    "found": "Got a name",
    "default": "Missing a name"
  }
}`;

  constructor(private servicesSvc: ServicesService, private config: ConfigService, private notificationService: NotificationService,
    private route: ActivatedRoute, private router: Router) {}

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
    this.operationName = this.route.snapshot.paramMap.get('name');
    this.serviceView = this.route.paramMap.pipe(
      switchMap((params: ParamMap) => 
        this.servicesSvc.getServiceView(params.get('serviceId')))
    );
    this.serviceView.subscribe( view => {
      this.serviceId = view.service.id;
      this.resolvedServiceView = view;
      for (var i=0; i<this.resolvedServiceView.service.operations.length; i++) {
        if (this.operationName === this.resolvedServiceView.service.operations[i].name) {
          this.operation = this.resolvedServiceView.service.operations[i];
          // Clone mutable properties from operation.
          this.newOperation = new Operation();
          this.newOperation.defaultDelay = this.operation.defaultDelay || 0;
          this.newOperation.dispatcher = this.operation.dispatcher;
          this.newOperation.dispatcherRules = this.operation.dispatcherRules;
          this.newOperation.parameterConstraints = this.operation.parameterConstraints;
          if (this.newOperation.parameterConstraints) {
            for (var j=0; j<this.newOperation.parameterConstraints.length; j++) {
              this.paramConstraints[this.newOperation.parameterConstraints[j].in].push(this.newOperation.parameterConstraints[j]);
            }
          }
          break;
        }
      }
    });
    this.frequencies = this.config.getFeatureProperty('async-api', 'frequencies').split(",");
  }

  public resetOperationProperties() {
    this.newOperation = new Operation();
    this.newOperation.defaultDelay = this.operation.defaultDelay;
    this.newOperation.dispatcher = this.operation.dispatcher;
    this.newOperation.dispatcherRules = this.operation.dispatcherRules;
  }
  public saveOperationProperties() {
    var operationProperties = new OperationMutableProperties();
    operationProperties.defaultDelay = this.newOperation.defaultDelay
    operationProperties.dispatcher = this.newOperation.dispatcher;
    operationProperties.dispatcherRules = this.newOperation.dispatcherRules;
    operationProperties.parameterConstraints = [];
    // Now recopy parameter constraints.
    for (var i=0; i<this.paramConstraints.header.length; i++) {
      operationProperties.parameterConstraints.push(this.paramConstraints.header[i]);
    }
    for (var i=0; i<this.paramConstraints.query.length; i++) {
      operationProperties.parameterConstraints.push(this.paramConstraints.query[i]);
    }
    
    console.log("[saveOperationProperties] operationProperties: " + JSON.stringify(operationProperties));
    this.servicesSvc.updateServiceOperationProperties(this.resolvedServiceView.service,
      this.operationName, operationProperties).subscribe(
        {
          next: res => {
            this.notificationService.message(NotificationType.SUCCESS,
              this.operationName, "Dispatch properies have been updated", false, null, null);
          },
          error: err => {
            this.notificationService.message(NotificationType.DANGER,
              this.operationName, "Dispatch properties cannot be updated (" + err.message + ")", false, null, null);
          },
          complete: () => console.log('Observer got a complete notification'),
        }
      );
  }

  public copyDispatcherRules(operator: string): void {
    this.newOperation.dispatcherRules = operator;
  }

  public addParameterConstraint(location: string): void {
    var parameterConstraints = this.paramConstraints[location];
    if (parameterConstraints == null) {
      this.paramConstraints[location] = [
        { 'name': "my-header", 'in': location, 'required': false, 'recopy': false, 'mustMatchRegexp': null}
      ];
    } else {
      this.paramConstraints[location].push({ 'name': "my-header", 'in': location, 'required': false, 'recopy': false, 'mustMatchRegexp': null});
    }
  }

  public removeParameterConstraint(location: string, index: number): void {
    var parameterConstraints = this.paramConstraints[location];
    if (parameterConstraints != null) {
      parameterConstraints.splice(index, 1);
    }
  }

  public isEventTypeService(): boolean {
    return this.resolvedServiceView.service.type === ServiceType.EVENT || this.resolvedServiceView.service.type === ServiceType.GENERIC_EVENT;
  }

  public isAsyncMockEnabled(): boolean {
    return this.config.getFeatureProperty('async-api', 'enabled').toLowerCase() === 'true' && this.newOperation.defaultDelay != 0;
  }
  public disableAsyncMock(): void {
    this.newOperation.defaultDelay = 0;
  }
  public enableAsyncMock(): void {
    this.newOperation.defaultDelay = parseInt(this.frequencies[0]);
  }

  handleCloseNotification($event: NotificationEvent): void {
    this.notificationService.remove($event.notification);
  }  
}