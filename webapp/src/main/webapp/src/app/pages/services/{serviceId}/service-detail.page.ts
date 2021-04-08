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
import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute, Router, ParamMap } from "@angular/router";

import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { BsModalService } from 'ngx-bootstrap/modal';
import { BsModalRef } from 'ngx-bootstrap/modal/bs-modal-ref.service';
import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';
import { ListConfig, ListEvent } from 'patternfly-ng/list';

import { EditLabelsDialogComponent } from '../../../components/edit-labels-dialog/edit-labels-dialog.component';
import { GenericResourcesDialogComponent } from './_components/generic-resources.dialog';
import { Operation, ServiceType, ServiceView, Contract, ParameterConstraint, Exchange, UnidirectionalEvent, RequestResponsePair, EventMessage } from '../../../models/service.model';
import { TestResult } from '../../../models/test.model';
import { IAuthenticationService } from "../../../services/auth.service";
import { ConfigService } from '../../../services/config.service';
import { ContractsService } from '../../../services/contracts.service';
import { ServicesService } from '../../../services/services.service';
import { TestsService } from '../../../services/tests.service';

@Component({
  selector: 'service-detail-page',
  templateUrl: './service-detail.page.html',
  styleUrls: ['./service-detail.page.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServiceDetailPageComponent implements OnInit {

  modalRef: BsModalRef;
  serviceId: string;
  serviceView: Observable<ServiceView>;
  resolvedServiceView: ServiceView;
  contracts: Observable<Contract[]>;
  serviceTests: Observable<TestResult[]>;
  operations: Operation[];
  selectedOperation: Operation;
  operationsListConfig: ListConfig;
  notifications: Notification[];

  constructor(private servicesSvc: ServicesService, private contractsSvc: ContractsService, 
      private testsSvc: TestsService, protected authService: IAuthenticationService, private config: ConfigService,
      private modalService: BsModalService, private notificationService: NotificationService,
      private route: ActivatedRoute, private router: Router, private ref: ChangeDetectorRef) {
  }

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
    this.serviceView = this.route.paramMap.pipe(
      switchMap((params: ParamMap) => 
        this.servicesSvc.getServiceView(params.get('serviceId')))
    );
    this.contracts = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.contractsSvc.listByServiceId(params.get('serviceId')))
    );
    this.serviceTests = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.testsSvc.listByServiceId(params.get('serviceId')))
    );
    this.serviceView.subscribe( view => {
      this.serviceId = view.service.id;
      this.resolvedServiceView = view;
      this.operations = view.service.operations;
      this.operations.sort((o1, o2) => {
        return this.sortOperations(o1, o2);
      });
    });

    this.operationsListConfig = {
      dblClick: false,
      emptyStateConfig: null,
      multiSelect: false,
      selectItems: false,
      selectionMatchProp: 'name',
      showCheckbox: false,
      showRadioButton: false,
      useExpandItems: true
    } as ListConfig;
  }

  private sortOperations(o1: Operation, o2: Operation): number {
    var name1 = this.removeVerbInUrl(o1.name);
    var name2 = this.removeVerbInUrl(o2.name);
    if (name1 > name2) {
      return 1;
    }
    if (name2 > name1) {
      return -1;
    }
    if (o1.name > o2.name) {
      return 1;
    }
    return -1;
  }

  public gotoCreateTest(): void {
    this.router.navigate(['/tests/create', { serviceId: this.serviceId }]);
  }

  public openEditLabels(): void {
    const initialState = {
      closeBtnName: 'Cancel',
      resourceName: this.resolvedServiceView.service.name + " - " + this.resolvedServiceView.service.version,
      resourceType: 'Service',
      labels: {}
    };
    if (this.resolvedServiceView.service.metadata.labels != undefined) {
      initialState.labels = JSON.parse(JSON.stringify(this.resolvedServiceView.service.metadata.labels));
    }
    this.modalRef = this.modalService.show(EditLabelsDialogComponent, {initialState});
    this.modalRef.content.saveLabelsAction.subscribe((labels) => {
      this.resolvedServiceView.service.metadata.labels = labels;
      this.servicesSvc.updateServiceMetadata(this.resolvedServiceView.service, this.resolvedServiceView.service.metadata).subscribe(
        {
          next: res => {
            // Because we're using the ChangeDetectionStrategy.OnPush, we have to explicitely
            // set a new value (and not only mutate) to serviceView to force async pipe evaluation later on.
            this.serviceView = new Observable<ServiceView>(observer => { observer.next(this.resolvedServiceView) });
            this.notificationService.message(NotificationType.SUCCESS,
              this.resolvedServiceView.service.name, "Labels have been updated", false, null, null);
            // Then trigger view reevaluation to update the label list component and the notifications toaster.
            this.ref.detectChanges();
          },
          error: err => {
            this.notificationService.message(NotificationType.DANGER,
              this.resolvedServiceView.service.name, "Labels cannot be updated (" + err.message + ")", false, null, null);
          },
          complete: () => console.log('Observer got a complete notification'),
        }
      );
    });
  }

  public openResources(): void {
    const initialState = {
      closeBtnName: 'Close',
      service: this.resolvedServiceView.service
    };
    this.modalRef = this.modalService.show(GenericResourcesDialogComponent, {initialState});
  }

  public getHeaderName(exchange: Exchange): string {
    if (this.resolvedServiceView.service.type === ServiceType.EVENT) {
      return (exchange as UnidirectionalEvent).eventMessage.name;
    } else {
      return (exchange as RequestResponsePair).request.name;
    }
  }

  public displayParameterConstraint(constraint: ParameterConstraint): string {
    var result = "Parameter ";
    if (constraint.required) {
      result += " is <code>required</code>";
    }
    if (constraint.recopy) {
      if (result != "Parameter ") { result += ", "}
      result += " will be <code>recopied</code> as response header"
    }
    if (constraint.mustMatchRegexp) {
      if (result != "Parameter ") { result += ", "}
      result += " must match the <code>" + constraint.mustMatchRegexp + "</code> regular expression"
    }
    return result;
  }

  public getBindingsList(operation: Operation): string {
    console.log("[ServiceDetailPageComponent.getBindingsList()]");
    if (operation.bindings != null) {
      var result = "";
      var bindings = Object.keys(operation.bindings);
      for (let i=0; i<bindings.length; i++) {
        var b = bindings[i];
        switch (b) {
          case 'KAFKA':
            result += 'Kafka';
            break;
          case 'MQTT':
            result += 'MQTT';
            break;
          case 'AMQP1':
            result += 'AMQP 1.0';
            break;
        }
        if (i+1 < bindings.length) {
          result += ", ";
        }
      }
      return result;
    }
    return null;
  }
  public hasBinding(operation: Operation, binding: string): boolean {
    if (operation.bindings != null) {
      return operation.bindings.hasOwnProperty(binding);
    }
    return false;
  }
  public getBindingProperty(operation: Operation, binding: string, property: string): string {
    console.log("[ServiceDetailPageComponent.getBindingProperty()]");
    if (operation.bindings != null) {
      var b = operation.bindings[binding];
      if (b.hasOwnProperty(property)) {
        return b[property];
      }
    }
    return null;
  }

  public formatMockUrl(operation: Operation, dispatchCriteria: string): string {
    console.log("[ServiceDetailPageComponent.formatMockUrl()]");
    var result = document.location.origin;

    if (this.resolvedServiceView.service.type === ServiceType.REST) {
      result += '/rest/';
      result += this.encodeUrl(this.resolvedServiceView.service.name) + '/' + this.resolvedServiceView.service.version;

      var parts = {};
      var params = {};
      var operationName = operation.name;
      
      if (dispatchCriteria != null) {
        var partsCriteria = (dispatchCriteria.indexOf('?') == -1 ? dispatchCriteria : dispatchCriteria.substring(0, dispatchCriteria.indexOf('?')));
        var paramsCriteria = (dispatchCriteria.indexOf('?') == -1 ? null : dispatchCriteria.substring(dispatchCriteria.indexOf('?') + 1));

        partsCriteria = this.encodeUrl(partsCriteria);
        partsCriteria.split('/').forEach(function(element, index, array) {
          if (element){
            parts[element.split('=')[0]] = element.split('=')[1];
          }
        });
      
        //operationName = operationName.replace(/{(\w+)}/g, function(match, p1, string) {
        operationName = operationName.replace(/{([a-zA-Z0-9-_]+)}/g, function(match, p1, string) {
          return parts[p1];
        });
        // Support also Postman syntax with /:part
        operationName = operationName.replace(/:([a-zA-Z0-9-_]+)/g, function(match, p1, string) {
          return parts[p1];
        });
        if (paramsCriteria != null) {
          operationName += '?' + paramsCriteria.replace(/\?/g, '&');
        }
      }

      // Remove leading VERB in Postman import case.
      operationName = this.removeVerbInUrl(operationName);
      result += operationName;
    } else if (this.resolvedServiceView.service.type === ServiceType.SOAP_HTTP) {
      result += '/soap/';
      result += this.encodeUrl(this.resolvedServiceView.service.name) + '/' + this.resolvedServiceView.service.version;
    } else if (this.resolvedServiceView.service.type === ServiceType.GENERIC_REST) {
      result += '/dynarest/';
      var resourceName = this.removeVerbInUrl(operation.name);
      result += this.encodeUrl(this.resolvedServiceView.service.name) + '/' + this.resolvedServiceView.service.version + resourceName;
    }
    
    return result;
  }
  public formatAsyncDestination(operation: Operation, eventMessage: EventMessage, binding: string): string {
    var serviceName = this.resolvedServiceView.service.name;
    var operationName = operation.name;

    // Remove ' ', '-' in service name.
    serviceName = serviceName.replace(/\s/g, '');
    serviceName = serviceName.replace(/-/g, '');

    // Remove verb and replace '/' by '-' in operation name.
    operationName = this.removeVerbInUrl(operationName);
    const parts = {};
    let partsCriteria = eventMessage.dispatchCriteria;
    if (partsCriteria != null) {

      partsCriteria = this.encodeUrl(partsCriteria);
      partsCriteria.split('/').forEach(function (element, index, array) {
        if (element) {
          parts[element.split('=')[0]] = element.split('=')[1];
        }
      });

      operationName = operationName.replace(/{([a-zA-Z0-9-_]+)}/g, function (match, p1, string) {
        return (parts[p1] != null) ? parts[p1] : match;
      });
    }

    if ('KAFKA' === binding) {
      operationName = operationName.replace(/\//g, '-');
    }

    return serviceName + "-" + this.resolvedServiceView.service.version + "-" + operationName;
  }


  public copyToClipboard(url: string): void {
    let selBox = document.createElement('textarea');
    selBox.style.position = 'fixed';
    selBox.style.left = '0';
    selBox.style.top = '0';
    selBox.style.opacity = '0';
    selBox.value = url;
    document.body.appendChild(selBox);
    selBox.focus();
    selBox.select();
    document.execCommand('copy');
    document.body.removeChild(selBox);
    this.notificationService.message(NotificationType.INFO,
      this.resolvedServiceView.service.name, "Mock URL has been copied to clipboard", false, null, null);
  }

  private removeVerbInUrl(operationName: string): string {
    if (operationName.startsWith("GET ") || operationName.startsWith("PUT ")
        || operationName.startsWith("POST ") || operationName.startsWith("DELETE ")
        || operationName.startsWith("OPTIONS ") || operationName.startsWith("PATCH ")
        || operationName.startsWith("HEAD ") || operationName.startsWith("TRACE ")
        || operationName.startsWith("SUBSCRIBE ") || operationName.startsWith("PUBLISH ")) {
      operationName = operationName.slice(operationName.indexOf(' ') + 1);
    } 
    return operationName;
  }
  private encodeUrl(url: string): string {
    return url.replace(/\s/g, '+');
  }

  handleCloseNotification($event: NotificationEvent): void {
    this.notificationService.remove($event.notification);
  }

  public hasRole(role: string): boolean {
    return this.authService.hasRole(role);
  }

  public allowOperationsPropertiesEdit(): boolean {
    return (this.hasRole('admin') || this.hasRole('manager')) 
        && (this.resolvedServiceView.service.type === 'REST' || (this.resolvedServiceView.service.type === 'EVENT' && this.asyncAPIFeatureEnabled()));
  }

  public asyncAPIFeatureEnabled(): boolean {
    return this.config.getFeatureProperty('async-api', 'enabled').toLowerCase() === 'true';
  }

  public asyncAPIFeatureEndpoint(binding: string): string {
    return this.config.getFeatureProperty('async-api', 'endpoint-' + binding);
  }

  public isAsyncMockEnabled(operation: Operation): boolean {
    return this.asyncAPIFeatureEnabled() && operation.defaultDelay != 0;
  }
}