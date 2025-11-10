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
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, ParamMap, RouterLink } from '@angular/router';

import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { TooltipConfig, TooltipModule } from 'ngx-bootstrap/tooltip';
import { HighlightAuto } from 'ngx-highlightjs';

import {
  Notification,
  NotificationEvent,
  NotificationService,
  NotificationType,
  ToastNotificationListComponent,
} from '../../../../components/patternfly-ng/notification';

import {
  Operation,
  ServiceType,
  ServiceView,
  OperationMutableProperties,
  Exchange,
  UnidirectionalEvent,
  RequestResponsePair,
} from '../../../../models/service.model';
import { ServicesService } from '../../../../services/services.service';
import { ConfigService } from '../../../../services/config.service';
import { LiveTracesComponent } from '../../../../components/live-traces/live-traces.component';
import { formatMockUrl } from '../../../../utils/format-utils';

@Component({
  selector: 'app-operation-override-page',
  templateUrl: './operation-override.page.html',
  styleUrls: ['./operation-override.page.css'],
  imports: [
    CommonModule,
    FormsModule,
    HighlightAuto,
    RouterLink,
    ToastNotificationListComponent,
    TooltipModule,
    LiveTracesComponent
  ],
})
export class OperationOverridePageComponent implements OnInit {
  serviceId!: string;
  operationName!: string;
  serviceView: Observable<ServiceView> | null = null;
  resolvedServiceView!: ServiceView;
  operation?: Operation;
  newOperation?: Operation;
  notifications: Notification[] = [];
  frequencies: string[] = [];
  paramConstraints: any = {
    header: [],
    query: [],
  };

  dispatchersByServiceType: any = {
    REST: [
      { value: 'SEQUENCE', label: 'SEQUENCE' },
      { value: 'URI_PARAMS', label: 'URI PARAMS' },
      { value: 'URI_PARTS', label: 'URI PARTS' },
      { value: 'URI_ELEMENTS', label: 'URI ELEMENTS' },
      { value: 'QUERY_HEADER', label: 'QUERY HEADER' },
      { value: 'GROOVY', label: 'GROOVY' },
      { value: 'JS', label: 'JS' },
      { value: 'JSON_BODY', label: 'JSON BODY' },
      { value: 'PROXY', label: 'PROXY' },
      { value: 'FALLBACK', label: 'FALLBACK' },
      { value: 'PROXY_FALLBACK', label: 'PROXY FALLBACK' },
    ],
    SOAP: [
      { value: 'QUERY_MATCH', label: 'QUERY MATCH' },
      { value: 'GROOVY', label: 'GROOVY' },
      { value: 'JS', label: 'JS' },
      { value: 'PROXY', label: 'PROXY' },
      { value: 'FALLBACK', label: 'FALLBACK' },
      { value: 'PROXY_FALLBACK', label: 'PROXY FALLBACK' },
    ],
    EVENT: [],
    GRPC: [
      { value: '', label: '' },
      { value: 'QUERY_ARGS', label: 'QUERY_ARGS' },
      { value: 'JSON_BODY', label: 'JSON BODY' },
      { value: 'FALLBACK', label: 'FALLBACK' },
    ],
    GRAPHQL: [
      { value: '', label: '' },
      { value: 'QUERY_ARGS', label: 'QUERY_ARGS' },
      { value: 'JSON_BODY', label: 'JSON BODY' },
      { value: 'GROOVY', label: 'GROOVY' },
      { value: 'JS', label: 'JS' },
      { value: 'PROXY', label: 'PROXY' },
      { value: 'FALLBACK', label: 'FALLBACK' },
      { value: 'PROXY_FALLBACK', label: 'PROXY FALLBACK' },
    ],
  };

  fallback = `{
  "dispatcher": "URI_PARTS",
  "dispatcherRules": "name",
  "fallback": "John Doe"
}`;

  proxyFallback = `{
  "dispatcher": "URI_PARTS",
  "dispatcherRules": "name",
  "proxyUrl": "http://external.net/"
}`;

  examplePayload = `{
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

  constructor(
    private servicesSvc: ServicesService,
    private config: ConfigService,
    private notificationService: NotificationService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
    this.operationName = this.route.snapshot.paramMap.get('name')!;
    this.serviceView = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.servicesSvc.getServiceView(params.get('serviceId')!)
      )
    );
    this.serviceView.subscribe((view) => {
      this.serviceId = view.service.id;
      this.resolvedServiceView = view;
      for (const operation of this.resolvedServiceView.service.operations) {
        if (this.operationName === operation.name) {
          this.operation = operation;
          // Clone mutable properties from operation.
          this.newOperation = {} as Operation;
          this.newOperation.name = this.operation.name;
          this.newOperation.defaultDelay = this.operation.defaultDelay || 0;
          this.newOperation.defaultDelayStrategy = this.operation.defaultDelayStrategy || 'fixed';
          this.newOperation.dispatcher = this.operation?.dispatcher ?? '';
          this.newOperation.dispatcherRules = this.operation.dispatcherRules;
          this.newOperation.parameterConstraints = this.operation.parameterConstraints;
          if (this.newOperation.parameterConstraints) {
            for (const constraint of this.newOperation.parameterConstraints) {
              this.paramConstraints[constraint.in].push(constraint);
            }
          }
          break;
        }
      }
    });
    this.frequencies = this.config
      .getFeatureProperty('async-api', 'frequencies')
      .split(',');
  }

  getReqRespPair(exchange: Exchange): RequestResponsePair {
    return exchange as RequestResponsePair;
  }
  getUnidirEvent(exchange: Exchange): UnidirectionalEvent {
    return exchange as UnidirectionalEvent;
  }

  public tryRequest(pair: RequestResponsePair):void {
    // Build the URL to use for this request.
    const url = formatMockUrl(this.resolvedServiceView, this.newOperation!,
        'raw', pair.response.dispatchCriteria, pair.request.queryParameters);

    // Build a base request object.    
    let request = {} as any;

    // Compute method depending on service type.
    if (this.resolvedServiceView.service.type === ServiceType.REST) {
      request.method = this.newOperation!.method;
      if (request.content) {
        if (request.content.startsWith('[') || request.content.startsWith('{')) {
          request.headers = { 'Content-Type': 'application/json' };
        } else if (request.content.startsWith('<')) {
          request.headers = { 'Content-Type': 'application/xml' };
        }
      }
    } else if (this.resolvedServiceView.service.type === ServiceType.SOAP_HTTP) {
      request.method = 'POST';
      request.headers = { 'Content-Type': 'application/soap+xml' };
    } else if (this.resolvedServiceView.service.type === ServiceType.GRAPHQL) {
      request.method = 'POST';
      request.headers = { 'Content-Type': 'application/json' };
    }

    // Add body if any.
    if (pair.request.content != null && pair.request.content.length > 0) {
      request.body = pair.request.content;
    }

    // Add request headers if any.
    if (pair.request.headers != null) {
      let headers: any = {};
      for (const header of pair.request.headers) {
        headers[header.name] = header.values.join(', ');
      }
      request.headers = headers;
    }
    
    // Now just fetch the body.
    fetch(url, request).then(res => {
      this.notificationService.message(
        NotificationType.SUCCESS,
        'Request executed',
        `Response status is ${res.status} ${res.statusText}`,
        false
      );
    }).catch(err => {
      if (err instanceof TypeError) {
        // This is likely a CORS issue.
        err.message = err.message + '. This may be a CORS issue.';
      }
      this.notificationService.message(
        NotificationType.DANGER,
        'Request failed',
        'Error was: ' + err.message,
        false
      );
    });
  }

  public resetOperationProperties() {
    this.newOperation = {} as Operation;
    if (this.operation) {
      this.newOperation.name = this.operation.name;
      this.newOperation.defaultDelay = this.operation?.defaultDelay ?? 0;
      this.newOperation.defaultDelayStrategy = this.operation?.defaultDelayStrategy ?? 'fixed';
      this.newOperation.dispatcher = this.operation?.dispatcher ?? '';
      this.newOperation.dispatcherRules = this.operation.dispatcherRules;
    }
  }
  public saveOperationProperties() {
    const operationProperties = {} as OperationMutableProperties;
    if (this.newOperation) {
      operationProperties.defaultDelay = this.newOperation.defaultDelay;
      operationProperties.defaultDelayStrategy = this.newOperation.defaultDelayStrategy;
      operationProperties.dispatcher = this.newOperation.dispatcher;
      operationProperties.dispatcherRules = this.newOperation.dispatcherRules;
    }
    operationProperties.parameterConstraints = [];
    // Now recopy parameter constraints.
    for (let i = 0; i < this.paramConstraints.header.length; i++) {
      operationProperties.parameterConstraints.push(this.paramConstraints.header[i]);
    }
    for (let i = 0; i < this.paramConstraints.query.length; i++) {
      operationProperties.parameterConstraints.push(this.paramConstraints.query[i]);
    }

//     console.log(
//       "[saveOperationProperties] operationProperties: " +
//         JSON.stringify(operationProperties)
//     );
    this.servicesSvc
      .updateServiceOperationProperties(
        this.resolvedServiceView.service,
        this.operationName,
        operationProperties
      )
      .subscribe({
        next: (res) => {
          this.notificationService.message(
            NotificationType.SUCCESS,
            this.operationName,
            'Dispatch properties have been updated',
            false
          );
        },
        error: (err) => {
          this.notificationService.message(
            NotificationType.DANGER,
            this.operationName,
            'Dispatch properties cannot be updated (' + err.message + ')',
            false
          );
        },
        complete: () => console.log('Observer got a complete notification'),
      });
  }

  public copyDispatcherRules(operator: string): void {
    if (this.newOperation) {
      this.newOperation.dispatcherRules = operator;
    }
  }

  public addParameterConstraint(location: string): void {
    const parameterConstraints = this.paramConstraints[location];
    if (parameterConstraints == null) {
      this.paramConstraints[location] = [
        {
          name: 'my-header',
          in: location,
          required: false,
          recopy: false,
          mustMatchRegexp: null,
        },
      ];
    } else {
      this.paramConstraints[location].push({
        name: 'my-header',
        in: location,
        required: false,
        recopy: false,
        mustMatchRegexp: null,
      });
    }
  }

  public removeParameterConstraint(location: string, index: number): void {
    const parameterConstraints = this.paramConstraints[location];
    if (parameterConstraints != null) {
      parameterConstraints.splice(index, 1);
    }
  }

  public isEventTypeService(): boolean {
    return (
      this.resolvedServiceView.service.type === ServiceType.EVENT ||
      this.resolvedServiceView.service.type === ServiceType.GENERIC_EVENT
    );
  }
  public isRequestTriable(): boolean {
    return (
      this.resolvedServiceView.service.type === ServiceType.REST ||
      this.resolvedServiceView.service.type === ServiceType.SOAP_HTTP ||
      this.resolvedServiceView.service.type === ServiceType.GRAPHQL
    );
  }

  public isAsyncMockEnabled(): boolean {
    return (
      this.config.getFeatureProperty('async-api', 'enabled').toLowerCase() ===
        'true' && this.newOperation?.defaultDelay != 0
    );
  }
  public disableAsyncMock(): void {
    if (this.newOperation) {
      this.newOperation.defaultDelay = 0;
    }
  }
  public enableAsyncMock(): void {
    if (this.newOperation) {
      this.newOperation.defaultDelay = parseInt(this.frequencies[0]);
    }
  }

  handleCloseNotification($event: NotificationEvent): void {
    this.notificationService.remove($event.notification);
  }
}
