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
import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, ParamMap, RouterLink } from '@angular/router';

import { Observable, Subscription, interval } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { CollapseModule } from 'ngx-bootstrap/collapse';
import { BsDropdownModule } from 'ngx-bootstrap/dropdown';
import { BsModalRef, BsModalService } from 'ngx-bootstrap/modal';
import { TooltipModule } from 'ngx-bootstrap/tooltip';

import {
  Notification,
  NotificationEvent,
  NotificationService,
  NotificationType,
  ToastNotificationListComponent,
} from '../../../components/patternfly-ng/notification';
import { ListConfig, ListModule } from '../../../components/patternfly-ng/list';

import { EditLabelsDialogComponent } from '../../../components/edit-labels-dialog/edit-labels-dialog.component';
import { CollapsibleLiveTracesComponent } from '../../../components/collapsible-live-traces/collapsible-live-traces.component';
import { GradeIndexComponent } from '../../../components/grade-index/grade-index.component';
import { LabelListComponent } from '../../../components/label-list/label-list.component';
import { TimeAgoPipe } from '../../../components/time-ago.pipe';

import { ExchangesTabsetComponent } from './_components/exchanges-tabset/exchanges-tabset.component';
import { GenerateSamplesDialogComponent } from './_components/generate-samples.dialog';
import { GenericResourcesDialogComponent } from './_components/generic-resources.dialog';
import { ManageSamplesDialogComponent } from './_components/manage-samples.dialog';

import {
  Operation,
  ServiceType,
  ServiceView,
  Contract,
  Parameter,
  ParameterConstraint,
  Exchange,
  UnidirectionalEvent,
  RequestResponsePair,
  EventMessage,
} from '../../../models/service.model';
import { TestConformanceMetric } from '../../../models/metric.model';
import { AICopilotService } from '../../../services/aicopilot.service';
import { IAuthenticationService } from '../../../services/auth.service';
import { ConfigService } from '../../../services/config.service';
import { ContractsService } from '../../../services/contracts.service';
import { MetricsService } from '../../../services/metrics.service';
import { ServicesService } from '../../../services/services.service';

@Component({
  selector: 'app-service-detail-page',
  templateUrl: './service-detail.page.html',
  styleUrls: ['./service-detail.page.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    CollapseModule,
    CollapsibleLiveTracesComponent,
    BsDropdownModule,
    ExchangesTabsetComponent,
    GradeIndexComponent,
    LabelListComponent,
    ListModule,
    RouterLink,
    TimeAgoPipe,
    ToastNotificationListComponent,
    TooltipModule,
  ],
})
export class ServiceDetailPageComponent implements OnInit {
  readonly hlLang: string[] = ['json', 'xml', 'yaml'];

  modalRef?: BsModalRef;
  serviceId!: string;
  serviceView: Observable<ServiceView> | null = null;
  resolvedServiceView!: ServiceView;
  contracts?: Observable<Contract[]>;
  serviceTestConformanceMetric?: Observable<TestConformanceMetric>;
  operations?: Operation[];
  selectedOperation?: Operation;
  operationsListConfig!: ListConfig;
  notifications: Notification[] = [];
  urlType: string = 'raw';
  // Deep-linking state from URL
  private qpOperationName: string | null = null;
  activeExchangeName: string | null = null;

  aiCopilotSamples: boolean = false;
  aiCopilotTaskId: string | null = null;
  aiPoller?: Subscription;

  constructor(
    private servicesSvc: ServicesService,
    private contractsSvc: ContractsService,
    private metricsSvc: MetricsService,
    private authService: IAuthenticationService,
    protected config: ConfigService,
    private copilotSvc: AICopilotService,
    private modalService: BsModalService,
    protected notificationService: NotificationService,
    private route: ActivatedRoute,
    private router: Router,
    private ref: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
    this.serviceView = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.servicesSvc.getServiceView(params.get('serviceId')!)
      )
    );
    this.contracts = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.contractsSvc.listByServiceId(params.get('serviceId')!)
      )
    );
    this.serviceTestConformanceMetric = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.metricsSvc.getServiceTestConformanceMetric(params.get('serviceId')!)
      )
    );
    this.serviceView.subscribe((view) => {
      this.serviceId = view.service.id;
      this.resolvedServiceView = view;
      this.operations = view.service.operations;
      this.operations.sort((o1, o2) => {
        return this.sortOperations(o1, o2);
      });
      this.updateAICopilotSamplesFlag(view);
      // If deep-link operation present, expand it now that operations are loaded
      if (this.qpOperationName) {
        this.expandOperation(this.qpOperationName);
      }
    });

    // Fallback
    this.route.paramMap.subscribe((params) => {
      // In case the <service_name>:<service_version> was used, contracts, tests and
      // conformance metrics fail because they can just resolve the technical identifier
      // of service. Relaunch everything here.
      const idParam = params.get('serviceId')!;
      if (idParam.includes(':') && this.serviceView != null) {
        this.serviceView.subscribe((view) => {
          console.log('Got serviceId for ' + idParam + ': ' + view.service.id);
          this.contracts = this.contractsSvc.listByServiceId(view.service.id);
          this.serviceTestConformanceMetric =
            this.metricsSvc.getServiceTestConformanceMetric(view.service.id);
        });
      }
    });

    this.operationsListConfig = {
      dblClick: false,
      //emptyStateConfig: null,
      multiSelect: false,
      selectItems: false,
      selectionMatchProp: 'name',
      showCheckbox: false,
      showRadioButton: false,
      useExpandItems: true,
    } as ListConfig;

    // Listen to query params for deep-link selection
    this.route.queryParamMap.subscribe((qp) => {
      const op = qp.get('operation');
      const ex = qp.get('exchange');
      this.qpOperationName = op;
      this.activeExchangeName = ex;
      // Try to expand the matching operation if view already loaded
      if (op && this.operations) {
        this.expandOperation(op);
      }
    });
  }

  private refreshServiceView(): void {
    // Because we're using the ChangeDetectionStrategy.OnPush, we have to explicitely
    // set a new value (and not only mutate) to serviceView to force async pipe evaluation later on.
    // When done multiple times, serviceView re-assignation is not detected... So we have to force
    // null, redetect and then re-assign a new Observable...
    this.serviceView = null;
    this.ref.detectChanges();
    this.serviceView = this.servicesSvc.getServiceView(this.serviceId);
    this.serviceView.subscribe((view) => {
      this.resolvedServiceView = view;
      this.updateAICopilotSamplesFlag(view);
    });
    // Then trigger view reevaluation to update the samples list and the notifications toaster.
    this.ref.detectChanges();
  }

  // Expand operation row in the list if names match
  private expandOperation(operationName: string): void {
    if (!this.operations) return;
    const item = this.operations.find((o) => o.name === operationName);
    if (item) {
      // Mark as expanded; ListComponent uses this flag
      (item as any).expanded = true;
      // Trigger change detection so child gets rendered
      this.ref.detectChanges();
    }
  }

  private updateAICopilotSamplesFlag(view: ServiceView): void {
    this.aiCopilotSamples = false;
    this.operations!.forEach((operation) => {
      view.messagesMap[operation.name].forEach((exchange) => {
        let anyExchange = exchange as any;
        if (
          (anyExchange.request != undefined && anyExchange.request.sourceArtifact === 'AI Copilot')
            || (anyExchange.eventMessage != undefined && anyExchange.eventMessage.sourceArtifact === 'AI Copilot')
          ) {
          this.aiCopilotSamples = true;
          return;
        }
      });
    });
  }

  private sortOperations(o1: Operation, o2: Operation): number {
    const name1 = this.removeVerbInUrl(o1.name);
    const name2 = this.removeVerbInUrl(o2.name);
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
    return (
      this.resolvedServiceView.service.type === ServiceType.EVENT ||
      this.resolvedServiceView.service.type === ServiceType.GENERIC_EVENT
    );
  }

  public gotoCreateTest(): void {
    this.router.navigate(['/tests/create', { serviceId: this.serviceId }]);
  }

  public openEditLabels(): void {
    const initialState = {
      closeBtnName: 'Cancel',
      resourceName: this.resolvedServiceView.service.name + ' - ' + this.resolvedServiceView.service.version,
      resourceType: 'Service',
      labels: new Map<string, string>(),
    };
    if (this.resolvedServiceView.service.metadata.labels != undefined) {
      initialState.labels = JSON.parse(
        JSON.stringify(this.resolvedServiceView.service.metadata.labels)
      );
    }
    this.modalRef = this.modalService.show(EditLabelsDialogComponent, {
      initialState,
    });
    this.modalRef.content.saveLabelsAction.subscribe((labels: Map<string, string>) => {
      this.resolvedServiceView.service.metadata.labels = labels;
      this.servicesSvc
        .updateServiceMetadata(
          this.resolvedServiceView.service,
          this.resolvedServiceView.service.metadata
        )
        .subscribe({
          next: (res) => {
            // Because we're using the ChangeDetectionStrategy.OnPush, we have to explicitely
            // set a new value (and not only mutate) to serviceView to force async pipe evaluation later on.
            // When done multiple times, serviceView re-assignation is not detected... So we have to force
            // null, redetect and then re-assign a new Observable...
            this.serviceView = null;
            this.ref.detectChanges();
            this.serviceView = new Observable<ServiceView>((observer) => {
              observer.next(this.resolvedServiceView);
            });
            this.notificationService.message(
              NotificationType.SUCCESS,
              this.resolvedServiceView.service.name,
              'Labels have been updated',
              false
            );
            // Then trigger view reevaluation to update the label list component and the notifications toaster.
            this.ref.detectChanges();
          },
          error: (err) => {
            this.notificationService.message(
              NotificationType.DANGER,
              this.resolvedServiceView.service.name,
              'Labels cannot be updated (' + err.message + ')',
              false
            );
          },
          complete: () => {
            //console.log('Observer got a complete notification')
          },
        });
    });
  }

  public getContractLink(contract: Contract): string {
    if (contract.mainArtifact) {
      return "/api/resources/" + contract.name;
    }
    return "/api/resources/id/" + contract.id;
  }
  public getContractDocumentationLink(contract: Contract): string {
    if (contract.mainArtifact) {
      return "/api/documentation/" + contract.name + "/" + contract.type;
    }
    return "/api/documentation/id/" + contract.id + "/" + contract.type;
  }

  public enrichWithAICopilot(): void {
    this.copilotSvc.launchSamplesGeneration(this.resolvedServiceView.service).subscribe((res) => {
      this.aiCopilotTaskId = res.taskId;
      console.log('AI Copilot task id: ' + this.aiCopilotTaskId);
      this.notificationService.message( 
        NotificationType.INFO,
        this.resolvedServiceView.service.name,
        'AI Copilot Samples generation started...',
        false
      );
      // Then trigger view reevaluation to update the spinner, the button and the notifications toaster.
      this.ref.detectChanges();

      // Then start polling for the task status and update the view accordingly.
      console.log('Starting polling for AI Copilot task status...');
      this.aiPoller = interval(5000).pipe(
        switchMap(() => this.copilotSvc.getGenerationTaskStatus(this.aiCopilotTaskId!))
      ).subscribe((res) => {
          console.log("Response: " + JSON.stringify(res));
          if (res.status === 'SUCCESS') {
            this.notificationService.message(
              NotificationType.SUCCESS,
              this.resolvedServiceView.service.name,
              'AI Copilot Samples generation finished!',
              false
            );
            this.aiCopilotTaskId = null;
            this.aiPoller!.unsubscribe();
          } else if (res.status === 'FAILURE') {
            this.notificationService.message(
              NotificationType.DANGER,
              this.resolvedServiceView.service.name,
              'AI Copilot Samples generation failed',
              false
            );
            this.aiCopilotTaskId = null;
            this.aiPoller!.unsubscribe();
          }
          // Refresh the view to update the spinner and the notifications toaster.
          this.refreshServiceView();
        });
    });
  }
  public isAIEnrichInProgress(): boolean {
    return this.aiCopilotTaskId != null;
  }

  public openResources(): void {
    const initialState = {
      closeBtnName: 'Close',
      service: this.resolvedServiceView.service,
    };
    this.modalRef = this.modalService.show(GenericResourcesDialogComponent, {
      initialState,
    });
  }

  public openGenerateSamples(operationName: string): void {
    const initialState = {
      closeBtnName: 'Cancel',
      service: this.resolvedServiceView.service,
      operationName,
    };
    this.modalRef = this.modalService.show(GenerateSamplesDialogComponent, {
      initialState,
    });
    this.modalRef.setClass('modal-lg');
    this.modalRef.content.saveSamplesAction.subscribe((exchanges: Exchange[]) => {
      this.copilotSvc
        .addSamplesSuggestions(
          this.resolvedServiceView.service,
          operationName,
          exchanges
        )
        .subscribe({
          next: (res) => {
            this.notificationService.message(
              NotificationType.SUCCESS,
              this.resolvedServiceView.service.name,
              'Samples have been added to ' + operationName,
              false
            );
            // Then trigger view reevaluation to update the samples list and the notifications toaster.
            this.refreshServiceView();
          },
          error: (err) => {
            this.notificationService.message(
              NotificationType.DANGER,
              this.resolvedServiceView.service.name,
              'Samples cannot be added (' + err.message + ')',
              false
            );
          },
          complete: () => {
            //console.log('Observer got a complete notification')
          },
        });
    });
  }

  public openManageSamples(): void {
    const initialState = {
      closeBtnName: 'Cancel',
      serviceView: this.resolvedServiceView,
      samplesMode: 'ALL',
    };
    this.modalRef = this.modalService.show(ManageSamplesDialogComponent, {
      initialState,
    });
    this.modalRef.setClass('modal-lg');
  }

  public openManageAISamples(): void {
    const initialState = {
      closeBtnName: 'Cancel',
      serviceView: this.resolvedServiceView,
    };
    this.modalRef = this.modalService.show(ManageSamplesDialogComponent, {
      initialState,
    });
    this.modalRef.setClass('modal-lg');
    this.modalRef.content.cleanupSelectionAction.subscribe((selectedExchanges: Record<string, Record<string, boolean>>) => {
      let exchangeSelection: { serviceId: string; exchanges: Record<string, string[]> } = {
        serviceId: this.resolvedServiceView.service.id,
        exchanges: {}
      };
      Object.keys(selectedExchanges).forEach((operationName) => {
        exchangeSelection.exchanges[operationName] = [];
        Object.keys(selectedExchanges[operationName]).forEach((exchangeName) => {
          exchangeSelection.exchanges[operationName]!.push(exchangeName);
        });
      });
      this.copilotSvc
        .removeExchanges(this.resolvedServiceView.service, exchangeSelection)
        .subscribe({
          next: (res) => {
            this.notificationService.message(
              NotificationType.SUCCESS,
              this.resolvedServiceView.service.name,
              'AI Copilot Samples have been removed from Service',
              false
            );
            // Then trigger view reevaluation to update the samples list and the notifications toaster.
            this.refreshServiceView();
          },
          error: (err) => {
            this.notificationService.message(
              NotificationType.DANGER,
              this.resolvedServiceView.service.name,
              'Selected Samples cannot be removed (' + err.message + ')',
              false
            );
          },
          complete: () => {
            //console.log('Observer got a complete notification')
          },
        });
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
    let result = 'Parameter ';
    if (constraint.required) {
      result += ' is <code>required</code>';
    }
    if (constraint.recopy) {
      if (result != 'Parameter ') {
        result += ', ';
      }
      result += ' will be <code>recopied</code> as response header';
    }
    if (constraint.mustMatchRegexp) {
      if (result != 'Parameter ') {
        result += ', ';
      }
      result +=
        ' must match the <code>' +
        constraint.mustMatchRegexp +
        '</code> regular expression';
    }
    return result;
  }

  public getBindingsList(operation: Operation): string | null {
    // console.log("[ServiceDetailPageComponent.getBindingsList()]");
    if (operation.bindings != null) {
      let result = '';
      const bindings = Object.keys(operation.bindings);
      for (let i = 0; i < bindings.length; i++) {
        const b = bindings[i];
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
        if (i + 1 < bindings.length) {
          result += ', ';
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
  public getBindingProperty(
    operation: Operation,
    binding: string,
    property: string
  ): string | null {
    // console.log("[ServiceDetailPageComponent.getBindingProperty()]");
    if (operation.bindings != null) {
      const b = operation.bindings[binding];
      if (b.hasOwnProperty(property)) {
        return (b as any)[property];
      }
    }
    return null;
  }

  public isMCPAvailable(): boolean {
    return this.resolvedServiceView.service.type === ServiceType.REST
        || this.resolvedServiceView.service.type === ServiceType.GRPC
        || this.resolvedServiceView.service.type === ServiceType.GRAPHQL
  }
  public formatMCPUrl(suffix: string = ''): string {
    let result = document.location.origin;

    // Manage dev mode.
    if (result.endsWith('localhost:4200')) {
      result = 'http://localhost:8080';
    }

    result += '/mcp/';
    result += this.encodeUrl(this.resolvedServiceView.service.name) + '/';
    result += this.resolvedServiceView.service.version;
    result += suffix;

    return result;
  }

  public formatMockUrl(operation: Operation, dispatchCriteria: string | null, queryParameters: Parameter[] | null): string {
    // console.log("[ServiceDetailPageComponent.formatMockUrl()]");
    let result = document.location.origin;

    // Manage dev mode.
    if (result.endsWith('localhost:4200')) {
      result = 'http://localhost:8080';
    }

    if (this.resolvedServiceView.service.type === ServiceType.REST) {
      if (this.urlType === 'raw') {
        result += '/rest/';
      } else {
        result += '/rest-valid/';
      }
      result +=
        this.encodeUrl(this.resolvedServiceView.service.name) +
        '/' +
        this.resolvedServiceView.service.version;

      const parts: Record<string, string> = {};
      const params = {};
      let operationName = operation.name;

      if (dispatchCriteria != null) {
        let partsCriteria =
          dispatchCriteria.indexOf('?') == -1
            ? dispatchCriteria
            : dispatchCriteria.substring(0, dispatchCriteria.indexOf('?'));
        const paramsCriteria =
          dispatchCriteria.indexOf('?') == -1
            ? null
            : dispatchCriteria.substring(dispatchCriteria.indexOf('?') + 1);

        partsCriteria = this.encodeUrl(partsCriteria);
        partsCriteria.split('/').forEach((element) => {
          if (element) {
            parts[element.split('=')[0]] = element.split('=')[1];
          }
        });

        // operationName = operationName.replace(/{(\w+)}/g, function(match, p1, string) {
        operationName = operationName.replace(
          /{([a-zA-Z0-9-_]+)}/g,
          (_, p1) => {
            return parts[p1];
          }
        );
        // Support also Postman syntax with /:part
        operationName = operationName.replace(/:([a-zA-Z0-9-_]+)/g, (_, p1) => {
          return parts[p1];
        });
        if (paramsCriteria != null && operation.dispatcher != 'QUERY_HEADER') {
          operationName += '?' + paramsCriteria.replace(/\?/g, '&');
        }
      }

      // Remove leading VERB in Postman import case.
      operationName = this.removeVerbInUrl(operationName);
      result += operationName;

      // Result may still contain {} if no dispatchCriteria (because of SCRIPT)
      if (result.indexOf('{') != -1 && queryParameters != null) {
        //console.log('queryParameters: ' + queryParameters);
        queryParameters.forEach((param) => {
          result = result.replace('{' + param.name + '}', param.value);
        });
      }

    } else if (this.resolvedServiceView.service.type === ServiceType.GRAPHQL) {
      result += '/graphql/';
      result +=
        this.encodeUrl(this.resolvedServiceView.service.name) +
        '/' +
        this.resolvedServiceView.service.version;
    } else if (
      this.resolvedServiceView.service.type === ServiceType.SOAP_HTTP
    ) {
      result += '/soap/';
      result +=
        this.encodeUrl(this.resolvedServiceView.service.name) +
        '/' +
        this.resolvedServiceView.service.version;
      if (this.urlType === 'valid') {
        result += '?validate=true';
      }
    } else if (
      this.resolvedServiceView.service.type === ServiceType.GENERIC_REST
    ) {
      result += '/dynarest/';
      const resourceName = this.removeVerbInUrl(operation.name);
      result +=
        this.encodeUrl(this.resolvedServiceView.service.name) +
        '/' +
        this.resolvedServiceView.service.version +
        resourceName;
    } else if (this.resolvedServiceView.service.type === ServiceType.GRPC) {
      // Change port in Dev mode of add '-grpc' service name suffix.
      if (result === 'http://localhost:8080') {
        result = 'http://localhost:9090';
      } else {
        result = result.replace(/^([^.-]+)(.*)/, '$1-grpc$2');
      }
    }

    return result;
  }

  public formatAsyncDestination(
    operation: Operation,
    eventMessage: EventMessage,
    binding: string
  ): string {
    let serviceName = this.resolvedServiceView.service.name;
    let versionName = this.resolvedServiceView.service.version;
    let operationName = operation.name;

    if (binding === 'WS') {
      // Specific encoding for urls.
      serviceName = serviceName.replace(/\s/g, '+');
      versionName = versionName.replace(/\s/g, '+');

      // Remove verb and templatized part if any.
      operationName = this.getDestinationOperationPart(operation, eventMessage);

      return (
        this.asyncAPIFeatureEndpoint('WS') +
        '/api/ws/' +
        serviceName +
        '/' +
        versionName +
        '/' +
        operationName
      );
    }

    // Remove ' ', '-' in service name.
    serviceName = serviceName.replace(/\s/g, '');
    serviceName = serviceName.replace(/-/g, '');

    // Remove verb and templatized part if any.
    operationName = this.getDestinationOperationPart(operation, eventMessage);

    // Sanitize operation name depending on protocol.
    if (
      'KAFKA' === binding ||
      'GOOGLEPUBSUB' === binding ||
      'SQS' === binding ||
      'SNS' === binding
    ) {
      operationName = operationName.replace(/\//g, '-');
    }
    if ('SQS' === binding || 'SNS' === binding) {
      versionName = versionName.replace(/\./g, '');
    }

    return serviceName + '-' + versionName + '-' + operationName;
  }

  protected getDestinationOperationPart(
    operation: Operation,
    eventMessage: EventMessage
  ): string {
    // In AsyncAPI v2, channel address is directly the operation name.
    let operationPart = this.removeVerbInUrl(operation.name);

    // Take care of templatized address for URI_PART dispatcher style.
    if (operation.dispatcher === 'URI_PARTS') {
      // In AsyncAPI v3, operation is different from channel and channel templatized address may be in resourcePaths.
      for (const resourcePath of operation.resourcePaths) {
        if (resourcePath.indexOf('{') != -1) {
          operationPart = resourcePath;
          break;
        }
      }

      // No replace the part placeholders with their values.
      if (eventMessage.dispatchCriteria != null) {
        const parts: Record<string, string> = {};
        const partsCriteria = this.encodeUrl(eventMessage.dispatchCriteria);
        partsCriteria.split('/').forEach((element) => {
          if (element) {
            parts[element.split('=')[0]] = element.split('=')[1];
          }
        });
        operationPart = operationPart.replace(
          /{([a-zA-Z0-9-_]+)}/g,
          (match, p1) => {
            return parts[p1] != null ? parts[p1] : match;
          }
        );
      }
    }
    return operationPart;
  }

  public formatRequestContent(requestContent: string): string {
    if (this.resolvedServiceView.service.type === ServiceType.GRAPHQL) {
      try {
        const request = JSON.parse(requestContent);
        return request.query;
      } catch (error) {
        console.log(
          'Error while parsing GraphQL request content: ' + (error! as any)['message']
        );
        return requestContent;
      }
    }
    return this.prettyPrintIfJSON(requestContent);
  }
  public formatGraphQLVariables(requestContent: string): string {
    try {
      const request = JSON.parse(requestContent);
      if (request.variables) {
        return JSON.stringify(request.variables, null, 2);
      }
    } catch (error) {
      console.log(
        'Error while parsing GraphQL request content: ' + (error! as any)['message']
      );
    }
    return '';
  }
  public prettyPrintIfJSON(content: string): string {
    if (
      (content.startsWith('[') || content.startsWith('{')) &&
      content.indexOf('\n') == -1
    ) {
      try {
        const jsonContent = JSON.parse(content);
        return JSON.stringify(jsonContent, null, 2);
      } catch (error) {
        return content;
      }
    }
    return content;
  }

  public formatCurlCmd(
    operation: Operation,
    exchange: RequestResponsePair
  ): string {
    let mockUrl = this.formatMockUrl(
      operation,
      exchange.response.dispatchCriteria,
      exchange.request.queryParameters
    );

    let cmd;

    if (this.resolvedServiceView.service.type != ServiceType.GRPC) {
      let verb = operation.method != undefined ? operation.method.toUpperCase() : 'POST';
      if (this.resolvedServiceView.service.type === ServiceType.GRAPHQL) {
        verb = 'POST';
      }

      cmd = 'curl -X ' + verb + ' \'' + mockUrl + '\'';

      // Add request headers if any.
      if (exchange.request.headers != null) {
        for (const header of exchange.request.headers) {
          cmd += ` -H '${header.name}: ${header.values.join(', ')}'`;
        }
      }

      // Add a content-type header if missing and obvious we need one.
      if (
        exchange.request.content != null &&
        !cmd.toLowerCase().includes('-h \'content-type:')
      ) {
        if (
          exchange.request.content.startsWith('[') ||
          exchange.request.content.startsWith('{')
        ) {
          cmd += ' -H \'Content-Type: application/json\'';
        } else if (exchange.request.content.startsWith('<')) {
          cmd += ' -H \'Content-Type: application/xml\'';
        }
      }
    } else {
      cmd = 'grpcurl -plaintext';
    }

    if (exchange.request.content != null
        && exchange.request.content != undefined
         && exchange.request.content != '') {
      cmd += ' -d \'' + exchange.request.content.replace(/\n/g, '') + '\'';
    }

    if (this.resolvedServiceView.service.type === ServiceType.GRPC) {
      // Add empty request body.
      if (exchange.request.content == null
          || exchange.request.content == undefined
          || exchange.request.content == '') {
        cmd += ' -d \'{}\'';
      }
      if (mockUrl.indexOf('://') != -1) {
        mockUrl = mockUrl.substring(mockUrl.indexOf('://') + 3);
      }
      cmd += ' ' + mockUrl + ' ' + this.resolvedServiceView.service.name + '/' + operation.name;
    }

    return cmd;
  }

  public copyToClipboard(url: string, what: string = 'Mock URL'): void {
    const selBox = document.createElement('textarea');
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
    this.notificationService.message(
      NotificationType.INFO,
      this.resolvedServiceView.service.name,
      what + ' has been copied to clipboard',
      false
    );
  }

  protected removeVerbInUrl(operationName: string): string {
    if (
      operationName.startsWith('GET ') ||
      operationName.startsWith('PUT ') ||
      operationName.startsWith('POST ') ||
      operationName.startsWith('DELETE ') ||
      operationName.startsWith('OPTIONS ') ||
      operationName.startsWith('PATCH ') ||
      operationName.startsWith('HEAD ') ||
      operationName.startsWith('TRACE ') ||
      operationName.startsWith('SUBSCRIBE ') ||
      operationName.startsWith('PUBLISH ') ||
      operationName.startsWith('SEND ') ||
      operationName.startsWith('RECEIVE ')
    ) {
      operationName = operationName.slice(operationName.indexOf(' ') + 1);
    }
    return operationName;
  }
  protected encodeUrl(url: string): string {
    return url.replace(/\s/g, '+');
  }

  handleCloseNotification($event: NotificationEvent): void {
    this.notificationService.remove($event.notification);
  }

  public hasRole(role: string): boolean {
    return this.authService.hasRole(role);
  }

  public hasRoleForService(role: string): boolean {
    if (
      this.hasRepositoryTenancyFeatureEnabled() &&
      this.resolvedServiceView.service.metadata.labels
    ) {
      //console.log('hasRepositoryTenancyFeatureEnabled');
      const tenant =
        this.resolvedServiceView.service.metadata.labels[
          this.repositoryTenantLabel()
        ];
      if (tenant !== undefined) {
        return this.authService.hasRoleForResource(role, tenant);
      }
    }
    return this.hasRole(role);
  }

  public allowOperationsPropertiesEdit(): boolean {
    return (
      (this.hasRoleForService('manager') || this.hasRole('admin')) &&
      (this.resolvedServiceView.service.type === 'REST' ||
        this.resolvedServiceView.service.type === 'GRPC' ||
        this.resolvedServiceView.service.type === 'GRAPHQL' ||
        this.resolvedServiceView.service.type === 'SOAP_HTTP' ||
        ((this.resolvedServiceView.service.type === 'EVENT' ||
          this.resolvedServiceView.service.type === 'GENERIC_EVENT') &&
          this.hasAsyncAPIFeatureEnabled()))
    );
  }

  public allowAICopilotOnSamples(): boolean {
    return (
      this.hasAICopilotEnabled() &&
      (this.resolvedServiceView.service.type === 'REST' ||
        this.resolvedServiceView.service.type === 'GRAPHQL' ||
        this.resolvedServiceView.service.type === 'EVENT' ||
        this.resolvedServiceView.service.type === 'GRPC')
    );
  }
  public hasAICopilotSamples(): boolean {
    return (
      this.hasAICopilotEnabled() && this.aiCopilotSamples
    );
  }

  public hasRepositoryTenancyFeatureEnabled(): boolean {
    return this.config.hasFeatureEnabled('repository-tenancy');
  }

  public repositoryTenantLabel(): string {
    return this.config
      .getFeatureProperty('repository-filter', 'label-key')
      .toLowerCase();
  }

  public hasAsyncAPIFeatureEnabled(): boolean {
    return this.config.hasFeatureEnabled('async-api');
  }

  public hasAICopilotEnabled(): boolean {
    return this.hasRole('manager') && this.config.hasFeatureEnabled('ai-copilot');
  }

  public asyncAPIFeatureEndpoint(binding: string): string {
    return this.config.getFeatureProperty('async-api', 'endpoint-' + binding);
  }

  public isAsyncMockEnabled(operation: Operation): boolean {
    return this.hasAsyncAPIFeatureEnabled() && operation.defaultDelay != 0;
  }
}
