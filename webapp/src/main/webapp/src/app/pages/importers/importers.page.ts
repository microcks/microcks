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
  ChangeDetectorRef,
  Component,
  OnInit,
  OnDestroy,
  ViewEncapsulation
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Params, Router } from '@angular/router';

/*
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
*/

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

import { ImportJob, ServiceRef } from '../../models/importer.model';
import { IAuthenticationService } from '../../services/auth.service';
import { ConfigService } from '../../services/config.service';
import { ImportersService } from '../../services/importers.service';
import { ServicesService } from '../../services/services.service';
import { UploaderDialogService } from '../../services/uploader-dialog.service';
import { ImporterWizardComponent} from './_components/importer.wizard';
import { ServiceRefsDialogComponent } from './service-refs.dialog';

@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'app-importers-page',
  templateUrl: './importers.page.html',
  styleUrls: ['./importers.page.css'],
  imports: [
    CommonModule,
    ConfirmDeleteDialogComponent,
    LabelListComponent,
    BsDropdownModule,
    DatePipe,
    FormsModule,
    //MatButtonModule,
    //MatFormFieldModule,
    //MatInputModule,
    PaginationModule,
    ToolbarModule,
    ToastNotificationListComponent,
    TooltipModule
  ],
  providers: [ImportersPageComponent]
})
export class ImportersPageComponent implements OnInit {

  modalRef?: BsModalRef;
  importJobs?: ImportJob[];
  importJobsCount: number = 0;
  servicesLabels?: Map<string, string[]>;
  toolbarConfig: ToolbarConfig = new ToolbarConfig;
  filterConfig: FilterConfig = new FilterConfig;
  paginationConfig: PaginationConfig = new PaginationConfig;
  nameFilterTerm: string | null = null;
  repositoryFilter: string | null = null;
  filtersText = '';
  selectedJob?: ImportJob | null;
  notifications: Notification[] = [];

  constructor(private importersSvc: ImportersService, private servicesSvc: ServicesService,
              private modalService: BsModalService, private notificationService: NotificationService,
              protected authService: IAuthenticationService, private config: ConfigService,
              private route: ActivatedRoute, private router: Router, private ref: ChangeDetectorRef,
              private uploaderDialogService: UploaderDialogService) { }

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
    this.getImportJobs();
    this.countImportJobs();

    const filterFieldsConfig = [];
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
    } as FilterConfig;

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
        this.filterImportJobs(this.repositoryFilter!, this.nameFilterTerm!);
      } else {
        // Default - retrieve all the jobs
        this.getImportJobs();
        this.countImportJobs();
      }
    });
  }

  getImportJobs(page: number = 1): void {
    this.importersSvc.getImportJobs(page).subscribe(results => this.importJobs = results);
  }
  filterImportJobs(repositoryFilter: string, nameFilterTerm: string): void {
    const labelsFilter = new Map<string, string>();
    if (repositoryFilter != null) {
      labelsFilter.set(this.repositoryFilterFeatureLabelKey(), repositoryFilter);
    }
    this.importersSvc.filterImportJobs(labelsFilter, nameFilterTerm).subscribe(results => {
      this.importJobs = results;
      this.filterConfig.resultsCount = results.length;
    });
    // Update browser URL to make the page bookmarkable.
    const queryParams: any = { name: nameFilterTerm };
    for (const key of Array.from( labelsFilter.keys() )) {
      queryParams['labels.' + key] = labelsFilter.get(key);
    }
    this.router.navigate([], {relativeTo: this.route, queryParams: queryParams as Params, queryParamsHandling: 'merge'});
  }

  countImportJobs(): void {
    this.importersSvc.countImportJobs().subscribe(results => {
      this.importJobsCount = results.counter;
      this.paginationConfig.totalItems = this.importJobsCount;
    });
  }

  getServicesLabels(): void {
    this.servicesSvc.getServicesLabels().subscribe(results => {
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
      this.filterConfig.fields[0].queries = queries;
    });
  }

  handlePageSize($event: PaginationEvent) {
    // this.updateItems();
  }

  handlePageNumber($event: PaginationEvent) {
    this.getImportJobs($event.pageNumber);
  }

  handleFilter($event: FilterEvent): void {
    this.filtersText = '';
    if (!$event.appliedFilters || $event.appliedFilters.length == 0) {
      this.nameFilterTerm = null;
      this.repositoryFilter = null;
      this.getImportJobs();
    } else {
      $event.appliedFilters.forEach((filter) => {
        if (this.hasRepositoryFilterFeatureEnabled() && filter.field.id === this.repositoryFilterFeatureLabelKey()) {
          this.repositoryFilter = filter.value;
        } else {
          this.nameFilterTerm = filter.value;
        }
      });
      this.filterImportJobs(this.repositoryFilter!, this.nameFilterTerm!);
    }
  }

  openArtifactUploader(): void {
    this.uploaderDialogService.openArtifactUploader();
  }

  openServiceRefs(serviceRefs: ServiceRef[]): void {
    const initialState = {
      serviceRefs
    };
    this.modalRef = this.modalService.show(ServiceRefsDialogComponent, {initialState});
    this.modalRef.content.closeBtnName = 'Close';
  }

  createImportJob(): void {
    this.modalRef = this.modalService.show(ImporterWizardComponent, { class: 'modal-lg' });

    this.modalRef.content.saveImportJobAction.subscribe((job: ImportJob) => {
      this.saveOrUpdateImportJob(job);
    });
  }
  editImportJob(job: ImportJob): void {
    this.selectedJob = job;
    this.modalRef = this.modalService.show(ImporterWizardComponent, {
      class: 'modal-lg',
      initialState: {
        job: this.selectedJob
      }
    });

    this.modalRef.content.saveImportJobAction.subscribe((job: ImportJob) => {
      this.saveOrUpdateImportJob(job);
    });
  }

  saveOrUpdateImportJob(job: ImportJob): void {
    if (job.id) {
      this.importersSvc.updateImportJob(job).subscribe(
        {
          next: res => {
            this.notificationService.message(NotificationType.SUCCESS,
                job.name, 'Import job has been updated', false);
            // Trigger view reevaluation to update the label list component.
            this.importJobs = JSON.parse(JSON.stringify(this.importJobs));
            this.ref.detectChanges();
          },
          error: err => {
            this.notificationService.message(NotificationType.DANGER,
                job.name, 'Import job cannot be updated (' + err.message + ')', false);
          },
          complete: () => {}, //console.log('Observer got a complete notification'),
        }
      );
    } else {
      this.importersSvc.createImportJob(job).subscribe(
        {
          next: res => {
            this.notificationService.message(NotificationType.SUCCESS,
                job.name, 'Import job has been created', false);
            this.getImportJobs();
            // Retrieve job id before activating.
            job.id = res.id;
            this.activateImportJob(job);
          },
          error: err => {
            this.notificationService.message(NotificationType.DANGER,
                job.name, 'Import job cannot be created (' + err.message + ')', false);
          },
          complete: () => {}, //console.log('Observer got a complete notification'),
        }
      );
    }
  }

  deleteImportJob(job: ImportJob): void {
    this.importersSvc.deleteImportJob(job).subscribe(
      {
        next: res => {
          job.active = true;
          this.notificationService.message(NotificationType.SUCCESS,
              job.name, 'Import job has been deleted', false);
          this.getImportJobs();
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              job.name, 'Import job cannot be deleted (' + err.message + ')', false);
        },
        complete: () => {}, //console.log('Observer got a complete notification'),
      }
    );
  }

  activateImportJob(job: ImportJob): void {
    this.importersSvc.activateImportJob(job).subscribe(
      {
        next: res => {
          job.active = true;
          this.notificationService.message(NotificationType.SUCCESS,
              job.name, 'Import job has been started/activated', false);
          this.startImportJob(job);
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              job.name, 'Import job cannot be started/activated (' + err.message + ')', false);
        },
        complete: () => {}, //console.log('Observer got a complete notification'),
      }
    );
  }

  startImportJob(job: ImportJob): void {
    this.importersSvc.startImportJob(job).subscribe(
      {
        next: res => {
          this.notificationService.message(NotificationType.SUCCESS,
              job.name, 'Import job has been forced', false);
          console.log('ImportJobs in 2 secs');
          // TODO run this outsize NgZone using zone.runOutsideAngular() : https://angular.io/api/core/NgZone
          setTimeout(() => {
            this.getImportJobs();
          }, 2000);
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              job.name, 'Import job cannot be forced now', false);
        },
        complete: () => {}, //console.log('Observer got a complete notification'),
      }
    );
  }

  stopImportJob(job: ImportJob): void {
    this.importersSvc.stopImportJob(job).subscribe(
      {
        next: res => {
          job.active = false;
          this.notificationService.message(NotificationType.SUCCESS,
              job.name, 'Import job has been stopped/desactivated', false);
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              job.name, 'Import job cannot be stopped/desactivated (' + err.message + ')', false);
        },
        complete: () => {}, //console.log('Observer got a complete notification'),
      }
    );
  }

  handleCloseNotification($event: NotificationEvent): void {
    this.notificationService.remove($event.notification);
  }

  public canImportArtifact(): boolean {
    if (this.hasRepositoryTenancyFeatureEnabled()) {
      const rolesStr = this.config.getFeatureProperty('repository-tenancy', 'artifact-import-allowed-roles');
      if (rolesStr == undefined || rolesStr === '') {
        return true;
      }
      // If roles specified, check if any is endorsed.
      const roles = rolesStr.split(',');
      for (const role of roles) {
        if (this.hasRole(role)) {
          return true;
        }
        if (role === 'manager-any') {
          const managerOfAny = this.hasRoleForAny('manager');
          if (managerOfAny) {
             return true;
          }
        }
      }
      return false;
    }
    // Default is manager to keep coherent behaviour with multi-tenant feature.
    return this.hasRole('manager') || this.hasRole('admin');
  }

  public hasRole(role: string): boolean {
    return this.authService.hasRole(role);
  }
  public hasRoleForAny(role: string): boolean {
    return this.authService.hasRoleForAnyResource(role);
  }
  public hasRoleForJob(role: string, job: ImportJob): boolean {
    if (this.hasRepositoryTenancyFeatureEnabled() && job.metadata && job.metadata.labels) {
      const tenant = job.metadata.labels[this.repositoryFilterFeatureLabelKey()];
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
