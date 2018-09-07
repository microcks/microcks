import { Component, OnInit } from '@angular/core';

import { BsModalService } from 'ngx-bootstrap/modal';
import { BsModalRef } from 'ngx-bootstrap/modal/bs-modal-ref.service';
import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';
import { PaginationConfig, PaginationEvent } from 'patternfly-ng/pagination';
import { ToolbarConfig } from 'patternfly-ng/toolbar';
import { FilterConfig, FilterEvent, FilterField, FilterType } from 'patternfly-ng/filter';

import { DynamicAPIDialogComponent } from './_components/dynamic-api.dialog';
import { Service, Api } from '../../models/service.model';
import { ServicesService } from '../../services/services.service';

@Component({
  selector: 'services-page',
  templateUrl: './services.page.html',
  styleUrls: ['./services.page.css']
})
export class ServicesPageComponent implements OnInit {

  modalRef: BsModalRef;
  services: Service[];
  servicesCount: number;
  toolbarConfig: ToolbarConfig;
  filterConfig: FilterConfig;
  paginationConfig: PaginationConfig;
  filterTerm: string = null;
  filtersText: string = '';
  notifications: Notification[];

  html:string = '';

  constructor(private servicesSvc: ServicesService, private modalService: BsModalService, 
    private notificationService: NotificationService) { }

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
    this.getServices();
    this.countServices();

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

  getServices(page: number = 1):void {
    this.servicesSvc.getServices().subscribe(results => this.services = results);
  }
  filterServices(filter: string): void {
    this.servicesSvc.filterServices(filter).subscribe(results => {
      this.services = results;
      this.filterConfig.resultsCount = results.length;
    });
  }

  countServices(): void {
    this.servicesSvc.countServices().subscribe(results => {
      this.servicesCount = results.counter;
      this.paginationConfig.totalItems = this.servicesCount;
    });
  }

  selectService(service: Service) {
    console.log("[selectService]");
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
    this.filtersText = '';
    if ($event.appliedFilters.length == 0) {
      this.filterTerm = null;
      this.getServices();
    } else {
      $event.appliedFilters.forEach((filter) => {
        this.filtersText += filter.field.title + ' : ' + filter.value + '\n';
        this.filterTerm = filter.value;
      });
      this.filterServices(this.filterTerm);
    }
  }

  openCreateDynamicAPI(): void {
    const initialState = {};
    this.modalRef = this.modalService.show(DynamicAPIDialogComponent, {initialState});
    this.modalRef.content.closeBtnName = 'Cancel';
    this.modalRef.content.createAction.subscribe((api) => {
      this.createDynamicAPI(api);
    });
  }

  createDynamicAPI(api: Api): void {
    this.servicesSvc.createDynamicAPI(api).subscribe(
      {
        next: res => {
          console.log("next");
          this.notificationService.message(NotificationType.SUCCESS,
              api.name, 'Dynamic API "' + api.name + '" has been created', false, null, null);
          this.getServices();
          this.countServices();
        },
        error: err => {
          console.log("error - " + JSON.stringify(err));
          this.notificationService.message(NotificationType.DANGER,
              api.name, 'Service or API "' + api.name + '"already exists with version ' + api.version, false, null, null);
        },
        complete: () => console.log('Observer got a complete notification'),
      }
    );
  }

  handleCloseNotification($event: NotificationEvent): void {
    this.notificationService.remove($event.notification);
  }
}
