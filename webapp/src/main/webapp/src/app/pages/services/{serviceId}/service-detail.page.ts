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
import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute, Router, ParamMap } from "@angular/router";

import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { BsModalService } from 'ngx-bootstrap/modal';
import { BsModalRef } from 'ngx-bootstrap/modal/bs-modal-ref.service';
import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';
import { ListConfig, ListEvent } from 'patternfly-ng/list';

import { EditLabelsDialogComponent } from '../../../components/edit-labels-dialog/edit-labels-dialog.component';
import { GenerateSamplesDialogComponent } from './_components/generate-samples.dialog';
import { GenericResourcesDialogComponent } from './_components/generic-resources.dialog';
import { Operation, ServiceType, ServiceView, Contract, ParameterConstraint, Exchange, UnidirectionalEvent, RequestResponsePair, EventMessage } from '../../../models/service.model';
import { TestConformanceMetric } from 'src/app/models/metric.model';
import { AICopilotService } from '../../../services/aicopilot.service';
import { IAuthenticationService } from "../../../services/auth.service";
import { ConfigService } from '../../../services/config.service';
import { ContractsService } from '../../../services/contracts.service';
import { MetricsService } from 'src/app/services/metrics.service';
import { ServicesService } from '../../../services/services.service';

@Component({
  selector: 'service-detail-page',
  templateUrl: './service-detail.page.html',
  styleUrls: ['./service-detail.page.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServiceDetailPageComponent implements OnInit {

  readonly hlLang: string[] = ['json', 'xml', 'yaml'];

  modalRef: BsModalRef;
  serviceId: string;
  serviceView: Observable<ServiceView>;
  resolvedServiceView: ServiceView;
  contracts: Observable<Contract[]>;
  serviceTestConformanceMetric: Observable<TestConformanceMetric>;
  operations: Operation[];
  selectedOperation: Operation;
  operationsListConfig: ListConfig;
  notifications: Notification[];

  constructor(private servicesSvc: ServicesService, private contractsSvc: ContractsService, 
      private metricsSvc: MetricsService, private authService: IAuthenticationService, private config: ConfigService,
      private copilotSvc: AICopilotService, private modalService: BsModalService, private notificationService: NotificationService,
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
    this.serviceTestConformanceMetric = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.metricsSvc.getServiceTestConformanceMetric(params.get('serviceId')))
    );
    this.serviceView.subscribe( view => {
      this.serviceId = view.service.id;
      this.resolvedServiceView = view;
      this.operations = view.service.operations;
      this.operations.sort((o1, o2) => {
        return this.sortOperations(o1, o2);
      });
    });

    // Fallback
    this.route.paramMap.subscribe((params) => {
      // In case the <service_name>:<service_version> was used, contracts, tests and
      // conformance metrics fail because they can just resolve the technical identifier
      // of service. Relaunch everything here.
      const idParam = params.get('serviceId');
      if (idParam.includes(':')) {
        this.serviceView.subscribe( view => {
          console.log('Got serviceId for ' + idParam + ': ' + view.service.id);
          this.contracts = this.contractsSvc.listByServiceId(view.service.id);
          this.serviceTestConformanceMetric = this.metricsSvc.getServiceTestConformanceMetric(view.service.id);
        })
      }
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

  public isEventTypeService(): boolean {
    return this.resolvedServiceView.service.type === ServiceType.EVENT || this.resolvedServiceView.service.type === ServiceType.GENERIC_EVENT;
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
            // When done multiple times, serviceView re-assignation is not detected... So we have to force
            // null, redetect and then re-assign a new Observable...
            this.serviceView = null;
            this.ref.detectChanges();
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

  public openGenerateSamples(operationName: string): void {
    const initialState = {
      closeBtnName: 'Cancel',
      service: this.resolvedServiceView.service,
      operationName: operationName
    };
    this.modalRef = this.modalService.show(GenerateSamplesDialogComponent, {initialState});
    this.modalRef.setClass('modal-lg');
    this.modalRef.content.saveSamplesAction.subscribe((exchanges) => {
      this.copilotSvc.addSamplesSuggestions(this.resolvedServiceView.service, operationName, exchanges).subscribe(
        {
          next: res => {
            // Because we're using the ChangeDetectionStrategy.OnPush, we have to explicitely
            // set a new value (and not only mutate) to serviceView to force async pipe evaluation later on.
            // When done multiple times, serviceView re-assignation is not detected... So we have to force
            // null, redetect and then re-assign a new Observable...
            this.serviceView = null;
            this.ref.detectChanges();
            this.serviceView = this.servicesSvc.getServiceView(this.serviceId);
            this.notificationService.message(NotificationType.SUCCESS,
              this.resolvedServiceView.service.name, "Samples have been added to " + operationName, false, null, null);
            // Then trigger view reevaluation to update the samples list and the notifications toaster.
            this.ref.detectChanges();
          },
          error: err => {
            this.notificationService.message(NotificationType.DANGER,
              this.resolvedServiceView.service.name, "Samples cannot be added (" + err.message + ")", false, null, null);
          },
          complete: () => console.log('Observer got a complete notification'),
        }
      );
    });
  }

  public getExchangeName(exchange: Exchange): string {
    if (this.isEventTypeService()) {
      return (exchange as UnidirectionalEvent).eventMessage.name;
    } else {
      return (exchange as RequestResponsePair).request.name;
    }
  }
  public getExchangeSourceArtifact(exchange: Exchange): string {
    if (this.isEventTypeService()) {
      return (exchange as UnidirectionalEvent).eventMessage.sourceArtifact;
    } else {
      return (exchange as RequestResponsePair).request.sourceArtifact;
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
    //console.log("[ServiceDetailPageComponent.getBindingsList()]");
    if (operation.bindings != null) {
      var result = "";
      var bindings = Object.keys(operation.bindings);
      for (let i=0; i<bindings.length; i++) {
        var b = bindings[i];
        switch (b) {
          case 'KAFKA':
            result += 'Kafka';
            break;
          case 'NATS':
            result += 'NATS';
            break;
          case 'MQTT':
            result += 'MQTT';
            break;
          case 'WS':
            result += 'WebSocket';
            break;
          case 'AMQP':
            result += 'AMQP';
            break;
          case 'AMQP1':
            result += 'AMQP 1.0';
            break;
          case 'GOOGLEPUBSUB':
            result += 'Google PubSub';
            break;
          case 'SNS':
            result += 'Amazon SNS';
            break;
          case 'SQS':
            result += 'Amazon SQS';
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
    //console.log("[ServiceDetailPageComponent.getBindingProperty()]");
    if (operation.bindings != null) {
      var b = operation.bindings[binding];
      if (b.hasOwnProperty(property)) {
        return b[property];
      }
    }
    return null;
  }

  public formatMockUrl(operation: Operation, dispatchCriteria: string): string {
    //console.log("[ServiceDetailPageComponent.formatMockUrl()]");
    var result = document.location.origin;
    
    // Manage dev mode.
    if (result.endsWith("localhost:4200")) {
      result = "http://localhost:8080";
    }

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
    } else if (this.resolvedServiceView.service.type === ServiceType.GRAPHQL) {
      result += '/graphql/';
      result += this.encodeUrl(this.resolvedServiceView.service.name) + '/' + this.resolvedServiceView.service.version;
    } else if (this.resolvedServiceView.service.type === ServiceType.SOAP_HTTP) {
      result += '/soap/';
      result += this.encodeUrl(this.resolvedServiceView.service.name) + '/' + this.resolvedServiceView.service.version;
    } else if (this.resolvedServiceView.service.type === ServiceType.GENERIC_REST) {
      result += '/dynarest/';
      var resourceName = this.removeVerbInUrl(operation.name);
      result += this.encodeUrl(this.resolvedServiceView.service.name) + '/' + this.resolvedServiceView.service.version + resourceName;
    } else if (this.resolvedServiceView.service.type === ServiceType.GRPC) {
      // Change port in Dev mode of add '-grpc' service name suffix.
      if (result === "http://localhost:8080") {
        result = "http://localhost:9090";
      } else {
        result = result.replace(/^([^.-]+)(.*)/, '$1-grpc$2');
      }
    }
    
    return result;
  }
  
  public formatAsyncDestination(operation: Operation, eventMessage: EventMessage, binding: string): string {
    var serviceName = this.resolvedServiceView.service.name;
    var versionName = this.resolvedServiceView.service.version;
    var operationName = operation.name;

    if (binding === "WS") {
      // Specific encoding for urls.
      serviceName = serviceName.replace(/\s/g, '+');
      versionName = versionName.replace(/\s/g, '+');

      // Remove verb and templatized part if any.
      operationName = this.getDestinationOperationPart(operation, eventMessage);

      return this.asyncAPIFeatureEndpoint('WS') + "/api/ws/" + serviceName + "/" + versionName + "/" + operationName;
    }

    // Remove ' ', '-' in service name.
    serviceName = serviceName.replace(/\s/g, '');
    serviceName = serviceName.replace(/-/g, '');

    // Remove verb and templatized part if any.
    operationName = this.getDestinationOperationPart(operation, eventMessage);

    // Sanitize operation name depending on protocol.
    if ('KAFKA' === binding || 'GOOGLEPUBSUB' === binding || 'SQS' === binding || 'SNS' === binding) {
      operationName = operationName.replace(/\//g, '-');
    }
    if ('SQS' === binding || 'SNS' === binding) {
      versionName = versionName.replace(/\./g, '');
    }

    return serviceName + "-" + versionName + "-" + operationName;
  }

  private getDestinationOperationPart(operation: Operation, eventMessage: EventMessage): string {
    // In AsyncAPI v2, channel address is directly the operation name.
    var operationPart = this.removeVerbInUrl(operation.name);

    // Take care of templatized address for URI_PART dispatcher style.
    if (operation.dispatcher === 'URI_PARTS') {
      // In AsyncAPI v3, operation is different from channel and channel templatized address may be in resourcePaths.
      for (let i=0; i<operation.resourcePaths.length; i++) {
        let resourcePath = operation.resourcePaths[i];
        if (resourcePath.indexOf('{') != -1) {
          operationPart = resourcePath;
          break;
        }
      }

      // No replace the part placeholders with their values.
      if (eventMessage.dispatchCriteria != null) {
        var parts = {};
        let partsCriteria = this.encodeUrl(eventMessage.dispatchCriteria);
        partsCriteria.split('/').forEach(function (element, index, array) {
          if (element) {
            parts[element.split('=')[0]] = element.split('=')[1];
          }
        });
        operationPart = operationPart.replace(/{([a-zA-Z0-9-_]+)}/g, function (match, p1, string) {
          return (parts[p1] != null) ? parts[p1] : match;
        });
      } 
    }
    return operationPart;
  }

  public formatRequestContent(requestContent: string): string {
    if (this.resolvedServiceView.service.type === ServiceType.GRAPHQL) {
      try {
        let request = JSON.parse(requestContent);
        return request.query;
      } catch (error) {
        console.log("Error while parsing GraphQL request content: " + error.message);
        return requestContent;
      }
    }
    return this.prettyPrintIfJSON(requestContent);
  }
  public formatGraphQLVariables(requestContent: string): string {
    try {
      let request = JSON.parse(requestContent);
      if (request.variables) {
        return JSON.stringify(request.variables, null, 2);
      }
    } catch (error) {
      console.log("Error while parsing GraphQL request content: " + error.message);
    }
    return "";
  }
  public prettyPrintIfJSON(content: string): string {
    if ((content.startsWith('[') || content.startsWith('{')) && content.indexOf('\n') == -1) {
      let jsonContent = JSON.parse(content);
      return JSON.stringify(jsonContent, null, 2);
    }
    return content;
  }

  public formatCurlCmd(operation: Operation, exchange: RequestResponsePair): string {
    let mockUrl = this.formatMockUrl(operation, exchange.response.dispatchCriteria);

    let verb = operation.method.toUpperCase();
    if (this.resolvedServiceView.service.type === ServiceType.GRAPHQL) {
      verb = "POST";
    }

    let cmd = "curl -X " + verb + " '" + mockUrl + "'";
    if (exchange.request.content != null && exchange.request.content != undefined) {
      cmd += " -d '" + exchange.request.content.replace(/\n/g, '') + "'";
    }
    if (exchange.request.headers != null) {
      for (let i=0; i < exchange.request.headers.length; i++) {
        let header = exchange.request.headers[i];
        cmd += " -H '" + header.name + ": " + header.values.join(', ') + "'";
      }
    }

    // Add a content-type header if missing and obvious we need one.
    if (exchange.request.content != null && !cmd.toLowerCase().includes("-h 'content-type:")) {
      if (exchange.request.content.startsWith('[') || exchange.request.content.startsWith('{')) {
        cmd += " -H 'Content-Type: application/json'";
      }
    }

    return cmd;
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
        || operationName.startsWith("POST ") || operationName.startsWith("DELETE ")
        || operationName.startsWith("OPTIONS ") || operationName.startsWith("PATCH ")
        || operationName.startsWith("HEAD ") || operationName.startsWith("TRACE ")
        || operationName.startsWith("SUBSCRIBE ") || operationName.startsWith("PUBLISH ")
        || operationName.startsWith("SEND ") || operationName.startsWith("RECEIVE ")) {
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

  public hasRoleForService(role: string): boolean {
    if (this.hasRepositoryTenancyFeatureEnabled() && this.resolvedServiceView.service.metadata.labels) {
      console.log("hasRepositoryTenancyFeatureEnabled");
      let tenant = this.resolvedServiceView.service.metadata.labels[this.repositoryTenantLabel()];
      if (tenant !== undefined) {
        return this.authService.hasRoleForResource(role, tenant);
      }
    }
    return this.hasRole(role);
  }

  public allowOperationsPropertiesEdit(): boolean {
    return (this.hasRoleForService('manager') || this.hasRole('admin'))
        && (this.resolvedServiceView.service.type === 'REST' 
            || this.resolvedServiceView.service.type === 'GRPC'
            || this.resolvedServiceView.service.type === 'GRAPHQL'
            || ((this.resolvedServiceView.service.type === 'EVENT' || this.resolvedServiceView.service.type === 'GENERIC_EVENT') 
                && this.hasAsyncAPIFeatureEnabled()));
  }

  public allowAICopilotOnSamples(): boolean {
    return this.hasAICopilotEnabled() 
        && (this.resolvedServiceView.service.type === 'REST' 
            || this.resolvedServiceView.service.type === 'GRAPHQL'
            || this.resolvedServiceView.service.type === 'EVENT'
            || this.resolvedServiceView.service.type === 'GRPC');
  }

  public hasRepositoryTenancyFeatureEnabled(): boolean {
    return this.config.hasFeatureEnabled('repository-tenancy');
  }

  public repositoryTenantLabel(): string {
    return this.config.getFeatureProperty('repository-filter', 'label-key').toLowerCase();
  }

  public hasAsyncAPIFeatureEnabled(): boolean {
    return this.config.hasFeatureEnabled('async-api');
  }

  public hasAICopilotEnabled(): boolean {
    return this.config.hasFeatureEnabled('ai-copilot');
  }

  public asyncAPIFeatureEndpoint(binding: string): string {
    return this.config.getFeatureProperty('async-api', 'endpoint-' + binding);
  }

  public isAsyncMockEnabled(operation: Operation): boolean {
    return this.hasAsyncAPIFeatureEnabled() && operation.defaultDelay != 0;
  }
}