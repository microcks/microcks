import { Component, OnInit } from '@angular/core';

import { BsModalRef } from 'ngx-bootstrap/modal/bs-modal-ref.service';
import { PaginationConfig, PaginationEvent } from 'patternfly-ng/pagination';

import { ServicesService } from '../../../../services/services.service';
import { Service, GenericResource } from '../../../../models/service.model';


@Component({
  selector: 'generic-resources-dialog',
  templateUrl: './generic-resources.dialog.html',
  styleUrls: ['./generic-resources.dialog.css']
})
export class GenericResourcesDialogComponent implements OnInit {

  title: string;
  closeBtnName: string;
  paginationConfig: PaginationConfig;
  service: Service;

  resources: GenericResource[];
  resourcesCount: number;
  
  constructor(private servicesSvc: ServicesService, public bsModalRef: BsModalRef) {}
 
  ngOnInit() {
    this.getGenericResources();
    this.countGenericResources();

    this.paginationConfig = {
      pageNumber: 1,
      pageSize: 20,
      pageSizeIncrements: [],
      totalItems: 20
    } as PaginationConfig;
  }

  getGenericResources(page: number = 1):void {
    this.servicesSvc.getGenericResources(this.service).subscribe(results => this.resources = results);
  }

  countGenericResources(): void {
    this.servicesSvc.countGenericResources(this.service).subscribe(results => {
      this.resourcesCount = results.counter;
      this.paginationConfig.totalItems = this.resourcesCount;
    });
  }

  handlePageSize($event: PaginationEvent) {
    //this.updateItems();
  }

  handlePageNumber($event: PaginationEvent) {
    this.getGenericResources($event.pageNumber)
  }

  public printPaylaod(payload: any): string {
    if (payload != undefined && payload != null) {
      return JSON.stringify(payload);
    }
    return "Cannot render payload as string !";
  }
}