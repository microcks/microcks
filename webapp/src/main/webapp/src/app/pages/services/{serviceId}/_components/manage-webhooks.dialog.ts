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
import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { BsDropdownModule } from 'ngx-bootstrap/dropdown';
import { BsModalRef } from 'ngx-bootstrap/modal';

import { ListConfig, ListModule } from '../../../../components/patternfly-ng/list';
import { PaginationConfig, PaginationEvent, PaginationModule } from '../../../../components/patternfly-ng/pagination';

import { Operation } from '../../../../models/service.model';
import { WebhooksService } from '../../../../services/webhook.service';
import { WebhookRegistration, WebhookRegistrationRequest } from '../../../../models/webhook.model';


@Component({
  selector: 'app-manage-webhooks-dialog',
  templateUrl: './manage-webhooks.dialog.html',
  styleUrls: ['./manage-webhooks.dialog.css'],
  imports: [
    CommonModule,
    BsDropdownModule,
    FormsModule,
    ListModule,
    PaginationModule
  ]
})
export class ManageWebhooksDialogComponent implements OnInit {

  closeBtnName!: string;
  operation!: Operation;
  operationId!: string;
  registrationsListConfig!: ListConfig;
  paginationConfig: PaginationConfig = new PaginationConfig;

  frequencies: number[] = [3000, 10_000, 30_000]; // in milliseconds
  durations: number[] = [1, 2, 3, 10]; // in days

  displayCreationForm: boolean = false;
  webhookRegistrations: WebhookRegistration[] = [];
  newRegistration: WebhookRegistrationRequest = {
    operationId: this.operationId,
    targetUrl: '',
    frequency: 3000,
    expiresAt: Date.now() + 2 * 24 * 3600 * 1000, // Two days from now
    errorCountThreshold: 5
  };
  expiresIn: number = 2;

  selectedRegistrations: Record<string, boolean> = {};

  constructor(
    private webhooksService: WebhooksService,
    public bsModalRef: BsModalRef
  ) {}

  ngOnInit() {
    this.getWebhookRegistrions();
    this.newRegistration.operationId = this.operationId;
    
    this.paginationConfig = {
      pageNumber: 1,
      pageSize: 10,
      pageSizeIncrements: [],
      totalItems: 10
    } as PaginationConfig;
  }

  getWebhookRegistrions(pageNumber: number = 1): void {
    this.webhooksService.listByOperationId(this.operationId, pageNumber, this.paginationConfig.pageSize).subscribe(registrations => {
      this.webhookRegistrations = registrations;
    });
  }

  public selectAllRegistrations(): void {
    this.webhookRegistrations.forEach(registration => {
      this.selectedRegistrations[registration.id] = true;
    });
  }
  public unselectAllRegistrations(): void {
    this.webhookRegistrations.forEach(registration => {
      this.selectedRegistrations[registration.id] = false;
    });
  }

  public openCreationForm(): void {
    this.displayCreationForm = true;
  }
  public addWebhookRegistration(): void {
    this.newRegistration.expiresAt = Date.now() + this.expiresIn * 24 * 3600 * 1000;
    this.webhooksService.create(this.newRegistration!).subscribe( createdRegistration => {
        this.getWebhookRegistrions();
      });
    this.displayCreationForm = false;
    this.newRegistration = {
      operationId: this.operationId,
      targetUrl: '',
      frequency: 3000,
      expiresAt: Date.now() + this.expiresIn * 24 * 3600 * 1000, // Two days from now
      errorCountThreshold: 5
    };
  }
  public cancelCreation(): void {
    this.displayCreationForm = false;
    this.newRegistration = {
      operationId: this.operationId,
      targetUrl: '',
      frequency: 3000,
      expiresAt: Date.now() + this.expiresIn * 24 * 3600 * 1000, // Two days from now
      errorCountThreshold: 5
    };
  }

  public deleteEnabled(): boolean {
    let enabled = false;
    Object.keys(this.selectedRegistrations).forEach( registrationId => {
      if (this.selectedRegistrations[registrationId] === true) {
        enabled = true;
        return;
      }
    });
    return enabled;
  }
  public deleteRegistrations(): void {
    const registrationsToDelete = Object.keys(this.selectedRegistrations).filter( registrationId => this.selectedRegistrations[registrationId] === true);
    registrationsToDelete.forEach( registrationId => {
      this.webhooksService.delete(registrationId).subscribe( () => {    
        delete this.selectedRegistrations[registrationId];
        this.getWebhookRegistrions();
      });
    });
  }

  handlePageSize($event: PaginationEvent) {
      // this.updateItems();
  }
  handlePageNumber($event: PaginationEvent) {
    this.getWebhookRegistrions($event.pageNumber);
  }
}