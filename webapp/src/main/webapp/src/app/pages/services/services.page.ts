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
import { CommonModule, DatePipe } from '@angular/common';
import {
  Component,
  OnInit,
  OnDestroy,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';

import { BsDropdownModule } from 'ngx-bootstrap/dropdown';
import { BsModalRef, BsModalService } from 'ngx-bootstrap/modal';
import { TooltipModule } from 'ngx-bootstrap/tooltip';

import { PaginationConfig, PaginationEvent, PaginationModule } from '../../components/patternfly-ng/pagination';
import { ToolbarConfig, ToolbarModule } from '../../components/patternfly-ng/toolbar';
import {
  FilterConfig,
  FilterEvent,
  FilterField,
  FilterType,
  Filter,
  FilterQuery,
} from '../../components/patternfly-ng/filter';
import {
  Notification,
  NotificationEvent,
  NotificationService,
  NotificationType,
  ToastNotificationListComponent,
} from '../../components/patternfly-ng/notification';

import { ConfirmDeleteDialogComponent } from '../../components/confirm-delete/confirm-delete.component';
import { LabelListComponent } from '../../components/label-list/label-list.component';
import { Api, Service, ServiceType } from '../../models/service.model';
import { IAuthenticationService } from '../../services/auth.service';
import { ConfigService } from '../../services/config.service';
import { ServicesService } from '../../services/services.service';
import { UploaderDialogService } from '../../services/uploader-dialog.service';
import { DirectAPIWizardComponent } from './_components/direct-api.wizard';

@Component({
  selector: 'app-services-page',
  templateUrl: './services.page.html',
  styleUrls: ['./services.page.css'],
  imports: [
    CommonModule,
    ConfirmDeleteDialogComponent,
    LabelListComponent,
    BsDropdownModule,
    DatePipe,
    FormsModule,
    RouterLink,
    PaginationModule,
    ToolbarModule,
    TooltipModule,
    ToastNotificationListComponent,
  ],
})
export class ServicesPageComponent implements OnInit, OnDestroy {
  
  modalRef?: BsModalRef;
  services?: Service[];
  servicesCount: number = 0;
  servicesLabels?: Map<string, string[]>;
  toolbarConfig: ToolbarConfig = new ToolbarConfig;
  filterConfig: FilterConfig = new FilterConfig;
  paginationConfig: PaginationConfig = new PaginationConfig;
  nameFilterTerm: string | null = null;
  repositoryFilter: string | null = null;
  notifications: Notification[] = [];

  html = '';

  constructor(
    private servicesSvc: ServicesService,
    private modalService: BsModalService,
    private notificationService: NotificationService,
    protected authService: IAuthenticationService,
    private config: ConfigService,
    private route: ActivatedRoute,
    private router: Router,
    private uploaderDialogService: UploaderDialogService
  ) {}

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();

    // Register refresh callback for this page
    this.uploaderDialogService.registerPageRefreshCallback('/services', () => {
      this.getServices();
      this.countServices();
    });

    const filterFieldsConfig = [];
    if (this.hasRepositoryFilterFeatureEnabled()) {
      this.getServicesLabels();
      
      filterFieldsConfig.push({
        id: this.repositoryFilterFeatureLabelKey(),
        title: this.repositoryFilterFeatureLabelLabel(),
        placeholder:
          'Filter by ' + this.repositoryFilterFeatureLabelLabel() + '...',
        type: FilterType.SELECT,
        queries: [],
      });
    }
    
    filterFieldsConfig.push({
      id: 'name',
      title: 'Name',
      placeholder: 'Filter by Name...',
      type: FilterType.TEXT,
    });

    this.paginationConfig = {
      pageNumber: 1,
      pageSize: 20,
      pageSizeIncrements: [],
      totalItems: 20,
    } as PaginationConfig;
    
    this.filterConfig = {
      fields: filterFieldsConfig as FilterField[],
      resultsCount: 20,
      appliedFilters: [],
    } as FilterConfig;

    this.toolbarConfig = {
      actionConfig: undefined,
      filterConfig: this.filterConfig,
      sortConfig: undefined,
      views: [],
    } as ToolbarConfig;
    
    this.route.queryParams.subscribe((queryParams) => {
      // Look at query parameters to apply filters.
      this.filterConfig.appliedFilters = [];
      if (queryParams['name']) {
        this.nameFilterTerm = queryParams['name'];
        this.filterConfig.appliedFilters.push({
          field: { title: 'Name' } as FilterField,
          value: this.nameFilterTerm,
        } as Filter);
      }
      if (queryParams['labels.' + this.repositoryFilterFeatureLabelKey()]) {
        this.repositoryFilter =
          queryParams['labels.' + this.repositoryFilterFeatureLabelKey()];
        this.filterConfig.appliedFilters.push({
          field: {
            title: this.repositoryFilterFeatureLabelLabel(),
          } as FilterField,
          value: this.repositoryFilter,
        } as Filter);
      }
      if (this.nameFilterTerm != null || this.repositoryFilter != null) {
        this.filterServices(this.repositoryFilter!, this.nameFilterTerm!);
      } else {
        // Default - retrieve all the services
        this.getServices();
        this.countServices();
      }
    });
  }

  getServices(page: number = 1): void {
    this.servicesSvc
      .getServices(page)
      .subscribe((results) => (this.services = results));
    // Update browser URL to make the page bookmarkable.
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {} as Params,
    });
  }
  filterServices(repositoryFilter: string, nameFilterTerm: string): void {
    const labelsFilter = new Map<string, string>();
    if (repositoryFilter != null) {
      labelsFilter.set(
        this.repositoryFilterFeatureLabelKey(),
        repositoryFilter
      );
    }
    this.servicesSvc
      .filterServices(labelsFilter, nameFilterTerm)
      .subscribe((results) => {
        this.services = results;
        this.filterConfig.resultsCount = results.length;
      });
    // Update browser URL to make the page bookmarkable.
    const queryParams: any = { name: nameFilterTerm };
    for (const key of Array.from(labelsFilter.keys())) {
      queryParams['labels.' + key] = labelsFilter.get(key);
    }
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: queryParams as Params,
      queryParamsHandling: 'merge',
    });
  }

  countServices(): void {
    this.servicesSvc.countServices().subscribe((results) => {
      this.servicesCount = results.counter;
      this.paginationConfig.totalItems = this.servicesCount;
    });
  }

  getServicesLabels(): void {
    this.servicesSvc.getServicesLabels().subscribe((results) => {
      this.servicesLabels = results;
      const queries: any[] = [];
      // Get only the label values corresponding to key used for filtering, then transform them for Patternfly.
      if (
        this.servicesLabels && (this.servicesLabels as any)[this.repositoryFilterFeatureLabelKey()] != undefined
      ) {
        (this.servicesLabels as any)[this.repositoryFilterFeatureLabelKey()].map(
          (label: any) => queries.push({ id: label, value: label })
        );
      }
      if (queries.length === 0) {
        this.filterConfig.fields = this.filterConfig.fields.filter(
          (field) => field.id === 'name'
        );
      } else {
        this.filterConfig.fields[0].queries = queries as FilterQuery[];
      } 
    });
  }

  deleteService(service: Service) {
    //console.log('[deleteService]: ' + JSON.stringify(service));
    this.servicesSvc.deleteService(service).subscribe({
      next: (res) => {
        this.notificationService.message(
          NotificationType.SUCCESS,
          service.name, 
          'Service has been fully deleted',
          false
        );
        this.getServices();
        this.servicesCount--;
      },
      error: (err) => {
        this.notificationService.message(
          NotificationType.DANGER,
          service.name, 
          'Service cannot be deleted (' + err.message + ')',
          false
        );
      },
      complete: () => {}, //console.log('Observer got a complete notification'),
    });
  }

  selectService(service: Service) {
    this.html = '<ul>';
    service.operations.forEach((operation) => {
      this.html += '<li>' + operation.name + '</li>';
    });
    this.html += '</ul>';
  }

  handlePageSize($event: PaginationEvent) {
    // this.updateItems();
  }

  handlePageNumber($event: PaginationEvent) {
    this.getServices($event.pageNumber);
  }

  handleFilter($event: FilterEvent): void {
    if (!$event.appliedFilters || $event.appliedFilters.length == 0) {
      this.nameFilterTerm = null;
      this.repositoryFilter = null;
      this.getServices();
    } else {
      $event.appliedFilters.forEach((filter) => {
        if (
          this.hasRepositoryFilterFeatureEnabled() &&
          filter.field.id === this.repositoryFilterFeatureLabelKey()
        ) {
          this.repositoryFilter = filter.value;
        } else {
          this.nameFilterTerm = filter.value;
        }
      });
      this.filterServices(this.repositoryFilter!, this.nameFilterTerm!);
    }
  }

  openArtifactUploader(): void {
    this.uploaderDialogService.openArtifactUploader();
  }

  openCreateDirectAPI(): void {
    this.modalRef = this.modalService.show(DirectAPIWizardComponent, { class: 'modal-lg' });

    this.modalRef.content.saveDirectAPIAction.subscribe((api: Api) => {
      this.createDirectAPI(api.type, api);
    });
  }

  createDirectAPI(apiType: ServiceType, api: Api): void {
    switch (apiType) {
      case ServiceType.GENERIC_REST:
        this.servicesSvc.createDirectResourceAPI(api).subscribe({
          next: (res) => {
            this.notificationService.message(
              NotificationType.SUCCESS,
              api.name,
              'Direct REST API "' + api.name + '" has been created',
              false
            );
            this.getServices();
            this.countServices();
          },
          error: (err) => {
            this.notificationService.message(
              NotificationType.DANGER,
              api.name,
              'Service or API "' + api.name + '"already exists with version ' + api.version,
              false
            );
          },
          complete: () => {}, //console.log('Observer got a complete notification'),
        });
        break;
      case ServiceType.GENERIC_EVENT:
        this.servicesSvc.createDirectEventAPI(api).subscribe({
          next: (res) => {
            this.notificationService.message(
              NotificationType.SUCCESS,
              api.name,
              'Direct EVENT API "' + api.name + '" has been created',
              false
            );
            this.getServices();
            this.countServices();
          },
          error: (err) => {
            this.notificationService.message(
              NotificationType.DANGER,
              api.name,
              'Service or API "' +
                api.name +
                '"already exists with version ' +
                api.version,
              false
            );
          },
          complete: () => {}, //console.log('Observer got a complete notification'),
        });
        break;
      default:
    }
  }

  closeDirectAPIWizardModal($event: any): void {
    if (this.modalRef) {
      this.modalRef.hide();
    }
  }

  handleCloseNotification($event: NotificationEvent): void {
    this.notificationService.remove($event.notification);
  }

  public hasRole(role: string): boolean {
    return this.authService.hasRole(role);
  }

  public hasRoleForService(role: string, service: Service): boolean {
    if (this.hasRepositoryTenancyFeatureEnabled() && service.metadata.labels) {
      const tenant =
        service.metadata.labels[this.repositoryFilterFeatureLabelKey()];
      if (
        tenant !== undefined &&
        this.authService.hasRoleForResource(role, tenant)
      ) {
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

  ngOnDestroy(): void {
    // Unregister refresh callback to prevent memory leaks
    this.uploaderDialogService.unregisterPageRefreshCallback('/services');
  }
}
