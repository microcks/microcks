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
import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { ActivatedRoute, Router, Params } from '@angular/router';

import { BsModalService } from 'ngx-bootstrap/modal';
import { BsModalRef } from 'ngx-bootstrap/modal/bs-modal-ref.service';
import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';
import { PaginationConfig, PaginationEvent } from 'patternfly-ng/pagination';
import { ToolbarConfig } from 'patternfly-ng/toolbar';
import { FilterConfig, FilterEvent, FilterField, FilterType, Filter } from 'patternfly-ng/filter';

import { Api, Service, ServiceType } from '../../models/service.model';
import { IAuthenticationService } from "../../services/auth.service";
import { ServicesService } from '../../services/services.service';
import { ConfigService } from '../../services/config.service';

@Component({
  selector: 'services-page',
  templateUrl: './services.page.html',
  styleUrls: ['./services.page.css']
})
export class ServicesPageComponent implements OnInit {
  @ViewChild('wizardTemplate', {static: true}) wizardTemplate: TemplateRef<any>;

  modalRef: BsModalRef;
  services: Service[];
  servicesCount: number;
  servicesLabels: Map<string, string[]>;
  toolbarConfig: ToolbarConfig;
  filterConfig: FilterConfig;
  paginationConfig: PaginationConfig;
  nameFilterTerm: string = null;
  repositoryFilter: string = null;
  notifications: Notification[];

  html:string = '';

  constructor(private servicesSvc: ServicesService, private modalService: BsModalService, 
    private notificationService: NotificationService, protected authService: IAuthenticationService, private config: ConfigService,
    private route: ActivatedRoute, private router: Router) { }

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
    
    var filterFieldsConfig = [];
    if (this.hasRepositoryFilterFeatureEnabled()) {
      this.getServicesLabels();
      filterFieldsConfig.push({
        id: this.repositoryFilterFeatureLabelKey(),
        title: this.repositoryFilterFeatureLabelLabel(),
        placeholder: 'Filter by ' + this.repositoryFilterFeatureLabelLabel() + '...',
        type: FilterType.SELECT,
        queries: []
      });
    }
    filterFieldsConfig.push({
      id: 'name',
      title: 'Name',
      placeholder: 'Filter by Name...',
      type: FilterType.TEXT
    });

    this.paginationConfig = {
      pageNumber: 1,
      pageSize: 20,
      pageSizeIncrements: [],
      totalItems: 20
    } as PaginationConfig;

    this.filterConfig = {
      fields: filterFieldsConfig as FilterField[],
      resultsCount: 20,
      appliedFilters: []
    } as FilterConfig

    this.toolbarConfig = {
      actionConfig: undefined,
      filterConfig: this.filterConfig,
      sortConfig: undefined,
      views: []
    } as ToolbarConfig;

    this.route.queryParams.subscribe(queryParams => {
      // Look at query parameters to apply filters.
      this.filterConfig.appliedFilters = [];
      if (queryParams['name']) {
        this.nameFilterTerm = queryParams['name'];
        this.filterConfig.appliedFilters.push({
          field: {title: 'Name'} as FilterField,
          value: this.nameFilterTerm
        } as Filter);
      }
      if (queryParams['labels.' + this.repositoryFilterFeatureLabelKey()]) {
        this.repositoryFilter = queryParams['labels.' + this.repositoryFilterFeatureLabelKey()];
        this.filterConfig.appliedFilters.push({
          field: {title: this.repositoryFilterFeatureLabelLabel()} as FilterField,
          value: this.repositoryFilter
        } as Filter);
      }
      if (this.nameFilterTerm != null || this.repositoryFilter != null) {
        this.filterServices(this.repositoryFilter, this.nameFilterTerm);
      } else {
        // Default - retrieve all the services
        this.getServices();
        this.countServices();
      }
    });
  }

  getServices(page: number = 1): void {
    this.servicesSvc.getServices(page).subscribe(results => this.services = results);
    // Update browser URL to make the page bookmarkable.
    this.router.navigate([], {relativeTo: this.route, queryParams: {} as Params});
  }
  filterServices(repositoryFilter: string, nameFilterTerm: string): void {
    var labelsFilter = new Map<string, string>();
    if (repositoryFilter != null) {
      labelsFilter.set(this.repositoryFilterFeatureLabelKey(), repositoryFilter);
    }
    this.servicesSvc.filterServices(labelsFilter, nameFilterTerm).subscribe(results => {
      this.services = results;
      this.filterConfig.resultsCount = results.length;
    });
    // Update browser URL to make the page bookmarkable.
    var queryParams = { name: nameFilterTerm };
    for (let key of Array.from( labelsFilter.keys() )) {
      queryParams['labels.' + key] = labelsFilter.get(key);
    }
    this.router.navigate([], {relativeTo: this.route, queryParams: queryParams as Params, queryParamsHandling: 'merge'});
  }

  countServices(): void {
    this.servicesSvc.countServices().subscribe(results => {
      this.servicesCount = results.counter;
      this.paginationConfig.totalItems = this.servicesCount;
    });
  }

  getServicesLabels(): void {
    this.servicesSvc.getServicesLabels().subscribe(results => {
      this.servicesLabels = results;
      var queries = [];
      // Get only the label values corresponding to key used for filtering, then transform them for Patternfly.
      if (this.servicesLabels[this.repositoryFilterFeatureLabelKey()] != undefined) {
        this.servicesLabels[this.repositoryFilterFeatureLabelKey()].map(label => queries.push({id: label, value: label}));
      }
      this.filterConfig.fields[0].queries = queries;
    });
  }

  deleteService(service: Service) {
    console.log("[deleteService]: " + JSON.stringify(service));
    this.servicesSvc.deleteService(service).subscribe(
      {
        next: res => {
          this.notificationService.message(NotificationType.SUCCESS,
              service.name, "Service has been fully deleted", false, null, null);
          this.getServices();
          this.servicesCount--;
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              service.name, "Service cannot be deleted (" + err.message + ")", false, null, null);
        },
        complete: () => console.log('Observer got a complete notification'),
      }
    );
  }

  selectService(service: Service) {
    this.html = '<ul>';
    service.operations.forEach(operation => {
      this.html += '<li>' + operation.name + '</li>';
    });
    this.html += '</ul>';
  }

  handlePageSize($event: PaginationEvent) {
    //this.updateItems();
  }

  handlePageNumber($event: PaginationEvent) {
    this.getServices($event.pageNumber)
  }

  handleFilter($event: FilterEvent): void {
    if ($event.appliedFilters.length == 0) {
      this.nameFilterTerm = null;
      this.repositoryFilter = null;
      this.getServices();
    } else {
      $event.appliedFilters.forEach((filter) => {
        if (this.hasRepositoryFilterFeatureEnabled() && filter.field.id === this.repositoryFilterFeatureLabelKey()) {
          this.repositoryFilter = filter.value;
        } else {
          this.nameFilterTerm = filter.value;
        }
      });
      this.filterServices(this.repositoryFilter, this.nameFilterTerm);
    }
  }

  openCreateDirectAPI(template: TemplateRef<any>): void {
    this.modalRef = this.modalService.show(template, { class: 'modal-lg' });
  }

  createDirectAPI(apiType: ServiceType, api: Api): void {
    switch (apiType) {
      case ServiceType.GENERIC_REST:
        this.servicesSvc.createDirectResourceAPI(api).subscribe(
          {
            next: res => {
              this.notificationService.message(NotificationType.SUCCESS,
                  api.name, 'Direct REST API "' + api.name + '" has been created', false, null, null);
              this.getServices();
              this.countServices();
            },
            error: err => {
              this.notificationService.message(NotificationType.DANGER,
                  api.name, 'Service or API "' + api.name + '"already exists with version ' + api.version, false, null, null);
            },
            complete: () => console.log('Observer got a complete notification'),
          }
        );
        break;
      case ServiceType.GENERIC_EVENT:
        this.servicesSvc.createDirectEventAPI(api).subscribe(
          {
            next: res => {
              this.notificationService.message(NotificationType.SUCCESS,
                  api.name, 'Direct EVENT API "' + api.name + '" has been created', false, null, null);
              this.getServices();
              this.countServices();
            },
            error: err => {
              this.notificationService.message(NotificationType.DANGER,
                  api.name, 'Service or API "' + api.name + '"already exists with version ' + api.version, false, null, null);
            },
            complete: () => console.log('Observer got a complete notification'),
          }
        );
        break;
      default:
    }
  }

  closeDirectAPIWizardModal($event: any): void {
    this.modalRef.hide();
  }

  handleCloseNotification($event: NotificationEvent): void {
    this.notificationService.remove($event.notification);
  }

  public hasRole(role: string): boolean {
    return this.authService.hasRole(role);
  }

  public hasRoleForService(role: string, service: Service): boolean {
    if (this.hasRepositoryTenancyFeatureEnabled() && service.metadata.labels) {
      let tenant = service.metadata.labels[this.repositoryFilterFeatureLabelKey()];
      if (tenant !== undefined && this.authService.hasRoleForResource(role, tenant)) {
        return true;
      }
    }
    return this.hasRole(role);
  }

  public hasRepositoryFilterFeatureEnabled(): boolean {
    return this.config.hasFeatureEnabled('repository-filter');
  }
  public hasRepositoryTenancyFeatureEnabled(): boolean {
    return this.config.hasFeatureEnabled('repository-tenancy');
  }

  public repositoryFilterFeatureLabelKey(): string {
    return this.config.getFeatureProperty('repository-filter', 'label-key');
  }
  public repositoryFilterFeatureLabelLabel(): string {
    return this.config.getFeatureProperty('repository-filter', 'label-label');
  }
  public repositoryFilterFeatureLabelList(): string {
    return this.config.getFeatureProperty('repository-filter', 'label-list');
  }
}
