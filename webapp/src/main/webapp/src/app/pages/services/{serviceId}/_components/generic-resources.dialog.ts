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
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

import { BsModalRef } from 'ngx-bootstrap/modal';

import { PaginationConfig, PaginationEvent, PaginationModule } from '../../../../components/patternfly-ng/pagination';

import { ServicesService } from '../../../../services/services.service';
import { Service, GenericResource } from '../../../../models/service.model';

@Component({
  selector: 'app-generic-resources-dialog',
  templateUrl: './generic-resources.dialog.html',
  styleUrls: ['./generic-resources.dialog.css'],
  imports: [
    CommonModule,
    PaginationModule
  ]
})
export class GenericResourcesDialogComponent implements OnInit {

  title?: string;
  closeBtnName!: string;
  paginationConfig!: PaginationConfig;
  service!: Service;

  resources!: GenericResource[];
  resourcesCount!: number;

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

  getGenericResources(page: number = 1): void {
    this.servicesSvc.getGenericResources(this.service).subscribe(results => this.resources = results);
  }

  countGenericResources(): void {
    this.servicesSvc.countGenericResources(this.service).subscribe(results => {
      this.resourcesCount = results.counter;
      this.paginationConfig.totalItems = this.resourcesCount;
    });
  }

  handlePageSize($event: PaginationEvent) {
    // this.updateItems();
  }

  handlePageNumber($event: PaginationEvent) {
    this.getGenericResources($event.pageNumber);
  }

  public printPaylaod(payload: any): string {
    if (payload != undefined && payload != null) {
      return JSON.stringify(payload);
    }
    return 'Cannot render payload as string !';
  }
}
