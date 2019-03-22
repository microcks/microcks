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

import { BsModalService } from 'ngx-bootstrap/modal';
import { BsModalRef } from 'ngx-bootstrap/modal/bs-modal-ref.service';
import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';
import { ListConfig, ListEvent } from 'patternfly-ng/list';

import { GenericResourcesDialogComponent } from './_components/generic-resources.dialog';
import { Operation, Service, ServiceType, ServiceView, Contract } from '../../../models/service.model';
import { TestResult } from '../../../models/test.model';
import { IAuthenticationService } from "../../../services/auth.service";
import { ContractsService } from '../../../services/contracts.service';
import { ServicesService } from '../../../services/services.service';
import { TestsService } from '../../../services/tests.service';

@Component({
  selector: 'service-detail-page',
  templateUrl: './service-detail.page.html',
  styleUrls: ['./service-detail.page.css']
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
      private testsSvc: TestsService, protected authService: IAuthenticationService, private modalService: BsModalService,
      private notificationService: NotificationService, private route: ActivatedRoute, private router: Router) {
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

  public openResources(): void {
    const initialState = {
      closeBtnName: 'Close',
      service: this.resolvedServiceView.service
    };
    this.modalRef = this.modalService.show(GenericResourcesDialogComponent, {initialState});
  }

  public formatMockUrl(operation: Operation, dispatchCriteria: string): string {
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
      
        operationName = operationName.replace(/{(\w+)}/g, function(match, p1, string) {
          return parts[p1];
        });
        // Support also Postman syntax with /:part
        operationName = operationName.replace(/:(\w+)/g, function(match, p1, string) {
          return parts[p1];
        });
        if (paramsCriteria != null) {
          operationName += '?' + paramsCriteria.replace('?', '&');
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
    if (operationName.startsWith("GET ") || operationName.startsWith("PUT ")) {
      operationName = operationName.slice(4);
    } else if (operationName.startsWith("POST ")) {
      operationName = operationName.slice(5);
    } else if (operationName.startsWith("DELETE ")) {
      operationName = operationName.slice(7);
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
}