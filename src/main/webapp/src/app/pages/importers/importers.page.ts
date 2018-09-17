import { Component, OnInit, TemplateRef, ViewChild, ViewEncapsulation } from '@angular/core';

import { BsModalService } from 'ngx-bootstrap/modal';
import { BsModalRef } from 'ngx-bootstrap/modal/bs-modal-ref.service';
import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';
import { PaginationConfig, PaginationEvent } from 'patternfly-ng/pagination';
import { ToolbarConfig } from 'patternfly-ng/toolbar';
import { FilterConfig, FilterEvent, FilterField, FilterType } from 'patternfly-ng/filter';

import { ImportJob, ServiceRef } from '../../models/importer.model';
import { ImportersService } from '../../services/importers.service';


@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'importers-page',
  templateUrl: './importers.page.html',
  styleUrls: ['./importers.page.css']
})
export class ImportersPageComponent implements OnInit {
  @ViewChild('wizardTemplate') wizardTemplate: TemplateRef<any>;

  modalRef: BsModalRef;
  importJobs: ImportJob[];
  importJobsCount: number;
  toolbarConfig: ToolbarConfig;
  filterConfig: FilterConfig;
  paginationConfig: PaginationConfig;
  filterTerm: string = null;
  filtersText: string = '';
  selectedJob: ImportJob;
  notifications: Notification[];

  constructor(private importersSvc: ImportersService, private modalService: BsModalService, 
    private notificationService: NotificationService) { }

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
    this.getImportJobs();
    this.countImportJobs();
    this.paginationConfig = {
      pageNumber: 1,
      pageSize: 20,
      pageSizeIncrements: [],
      totalItems: 20
    } as PaginationConfig;

    this.filterConfig = {
      fields: [{
        id: 'name',
        title: 'Name',
        placeholder: 'Filter by Name...',
        type: FilterType.TEXT
      }] as FilterField[],
      resultsCount: 20,
      appliedFilters: []
    } as FilterConfig

    this.toolbarConfig = {
      actionConfig: undefined,
      filterConfig: this.filterConfig,
      sortConfig: undefined,
      views: []
    } as ToolbarConfig;
  }

  ngAfterViewInit() {
  }

  getImportJobs(page: number = 1): void {
    this.importersSvc.getImportJobs(page).subscribe(results => this.importJobs = results);
  }
  filterImportJobs(filter: string): void {
    this.importersSvc.filterImportJobs(filter).subscribe(results => {
      this.importJobs = results;
      this.filterConfig.resultsCount = results.length;
    });
  }

  countImportJobs(): void {
    this.importersSvc.countImportJobs().subscribe(results => {
      this.importJobsCount = results.counter;
      this.paginationConfig.totalItems = this.importJobsCount;
    });
  }

  handlePageSize($event: PaginationEvent) {
    //this.updateItems();
  }

  handlePageNumber($event: PaginationEvent) {
    this.getImportJobs($event.pageNumber)
  }

  handleFilter($event: FilterEvent): void {
    this.filtersText = '';
    if ($event.appliedFilters.length == 0) {
      this.filterTerm = null;
      this.getImportJobs();
    } else {
      $event.appliedFilters.forEach((filter) => {
        this.filtersText += filter.field.title + ' : ' + filter.value + '\n';
        this.filterTerm = filter.value;
      });
      this.filterImportJobs(this.filterTerm);
    }
  }

  openServiceRefs(serviceRefs: ServiceRef[]):void {
    const initialState = {
      serviceRefs: serviceRefs
    };
    this.modalRef = this.modalService.show(ServiceRefsDialogComponent, {initialState});
    this.modalRef.content.closeBtnName = 'Close';
  }

  createImportJob(template: TemplateRef<any>): void {
    this.modalRef = this.modalService.show(template, { class: 'modal-lg' });
  }
  editImportJob(template: TemplateRef<any>, job: ImportJob):void {
    this.selectedJob = job;
    this.modalRef = this.modalService.show(template, { class: 'modal-lg' });
  }

  closeImportJobWizardModal($event: any): void {
    this.selectedJob = null;
    this.modalRef.hide();
  }

  saveOrUpdateImportJob(job: ImportJob): void {
    if (job.id) {
      this.importersSvc.updateImportJob(job).subscribe(
        {
          next: res => {
            this.notificationService.message(NotificationType.SUCCESS,
                job.name, "Import job has been updated", false, null, null);
          },
          error: err => {
            this.notificationService.message(NotificationType.DANGER,
                job.name, "Import job cannot be updated (" + err.message + ")", false, null, null);
          },
          complete: () => console.log('Observer got a complete notification'),
        }
      );
    } else {
      this.importersSvc.createImportJob(job).subscribe(
        {
          next: res => {
            this.notificationService.message(NotificationType.SUCCESS,
                job.name, "Import job has been created", false, null, null);
            this.getImportJobs();
          },
          error: err => {
            this.notificationService.message(NotificationType.DANGER,
                job.name, "Import job cannot be created (" + err.message + ")", false, null, null);
          },
          complete: () => console.log('Observer got a complete notification'),
        }
      );
    }
  }

  deleteImportJob(job: ImportJob):void {
    this.importersSvc.deleteImportJob(job).subscribe(
      {
        next: res => {
          job.active = true;
          this.notificationService.message(NotificationType.SUCCESS,
              job.name, "Import job has been deleted", false, null, null);
          this.getImportJobs();
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              job.name, "Import job cannot be deleted (" + err.message + ")", false, null, null);
        },
        complete: () => console.log('Observer got a complete notification'),
      }
    );
  }

  activateImportJob(job: ImportJob):void {
    this.importersSvc.activateImportJob(job).subscribe(
      {
        next: res => {
          job.active = true;
          this.notificationService.message(NotificationType.SUCCESS,
              job.name, "Import job has been started/activated", false, null, null);
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              job.name, "Import job cannot be started/activated (" + err.message + ")", false, null, null);
        },
        complete: () => console.log('Observer got a complete notification'),
      }
    );
  }

  startImportJob(job: ImportJob):void {
    this.importersSvc.startImportJob(job).subscribe(
      {
        next: res => {
          this.notificationService.message(NotificationType.SUCCESS,
              job.name, "Import job has been forced", false, null, null);
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              job.name, "Import job cannot be forced now", false, null, null);
        },
        complete: () => console.log('Observer got a complete notification'),
      }
    );
  }

  stopImportJob(job: ImportJob):void {
    this.importersSvc.stopImportJob(job).subscribe(
      {
        next: res => {
          job.active = false;
          this.notificationService.message(NotificationType.SUCCESS,
              job.name, "Import job has been stopped/desactivated", false, null, null);
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              job.name, "Import job cannot be stopped/desactivated (" + err.message + ")", false, null, null);
        },
        complete: () => console.log('Observer got a complete notification'),
      }
    );
  }

  handleCloseNotification($event: NotificationEvent): void {
    this.notificationService.remove($event.notification);
  }
}

@Component({
  selector: 'servicerefs-dialog',
  template: `
    <div class="modal-header">
      <h4 class="modal-title pull-left">Services</h4>
      <button type="button" class="close pull-right" aria-label="Close" (click)="bsModalRef.hide()">
        <span aria-hidden="true">&times;</span>
      </button>
    </div>
    <div class="modal-body">
      <ul *ngIf="serviceRefs.length">
        <li *ngFor="let serviceRef of serviceRefs">
        <a [routerLink]="['/services', serviceRef.serviceId]">{{ serviceRef.name }} - {{ serviceRef.version }}</a>
        </li>
      </ul>
    </div>
    <div class="modal-footer">
      <button type="button" class="btn btn-default" (click)="bsModalRef.hide()">{{closeBtnName}}</button>
    </div>
  `
})
export class ServiceRefsDialogComponent implements OnInit {
  title: string;
  closeBtnName: string;
  serviceRefs: ServiceRef[] = [];
 
  constructor(public bsModalRef: BsModalRef) {}
 
  ngOnInit() {
  }
}