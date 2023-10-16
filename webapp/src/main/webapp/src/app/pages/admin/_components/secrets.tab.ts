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

import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';
import { PaginationConfig, PaginationEvent } from 'patternfly-ng/pagination';
import { ToolbarConfig } from 'patternfly-ng/toolbar';
import { FilterConfig, FilterEvent, FilterField, FilterType } from 'patternfly-ng/filter';

import { Secret } from '../../../models/secret.model';
import { SecretsService } from '../../../services/secrets.service';


@Component({
  selector: 'secrets-tab',
  templateUrl: './secrets.tab.html',
  styleUrls: ['./secrets.tab.css']
})
export class SecretsTabComponent implements OnInit {

  secrets: Secret[];
  secretsCount: number;
  toolbarConfig: ToolbarConfig;
  filterConfig: FilterConfig;
  paginationConfig: PaginationConfig;
  filterTerm: string = null;
  filtersText: string = '';

  secret: Secret = new Secret();
  createOrUpdateBtn: string = 'Create';
  authenticationType: string;

  constructor(private secretsSvc: SecretsService, private notificationService: NotificationService) {}

  ngOnInit() {
    this.getSecrets();
    this.countSecrets();
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

  getSecrets(page: number = 1): void {
    this.secretsSvc.getSecrets(page).subscribe(results => this.secrets = results);
  }
  filterSecrets(filter: string): void {
    this.secretsSvc.filterSecrets(filter).subscribe(results => {
      this.secrets = results;
      this.filterConfig.resultsCount = results.length;
    });
  }

  countSecrets(): void {
    this.secretsSvc.countSecrets().subscribe(results => {
      this.secretsCount = results.counter;
      this.paginationConfig.totalItems = this.secretsCount;
    });
  }

  handlePageSize($event: PaginationEvent) {
    //this.updateItems();
  }

  handlePageNumber($event: PaginationEvent) {
    this.getSecrets($event.pageNumber)
  }

  handleFilter($event: FilterEvent): void {
    this.filtersText = '';
    if ($event.appliedFilters.length == 0) {
      this.filterTerm = null;
      this.getSecrets();
    } else {
      $event.appliedFilters.forEach((filter) => {
        this.filtersText += filter.field.title + ' : ' + filter.value + '\n';
        this.filterTerm = filter.value;
      });
      this.filterSecrets(this.filterTerm);
    }
  }

  editSecret(secret: Secret): void {
    this.secret = secret;
    this.createOrUpdateBtn = 'Update';
    if (secret.username != null && secret.password != null) {
      this.authenticationType = 'basic';
    } else if (secret.token != null){
      this.authenticationType = 'token';
    }
  }
  resetEditedSecret(): void {
    this.secret = new Secret();
    this.createOrUpdateBtn = 'Create';
  }
  
  saveOrUpdateSecret(secret: Secret): void {
    if (secret.id) {
      this.secretsSvc.updateSecret(secret).subscribe(
        {
          next: res => {
            this.notificationService.message(NotificationType.SUCCESS,
              secret.name, "Secret has been updated", false, null, null);
          },
          error: err => {
            this.notificationService.message(NotificationType.DANGER,
              secret.name, "Secret cannot be updated (" + err.message + ")", false, null, null);
          },
          complete: () => console.log('Observer got a complete notification'),
        }
      );
    } else {
      this.secretsSvc.createSecret(secret).subscribe(
        {
          next: res => {
            this.notificationService.message(NotificationType.SUCCESS,
              secret.name, "Secret has been created", false, null, null);
            this.secret = new Secret();
            this.createOrUpdateBtn = 'Create';
            this.getSecrets();
          },
          error: err => {
            this.notificationService.message(NotificationType.DANGER,
              secret.name, "Secret cannot be created (" + err.message + ")", false, null, null);
          },
          complete: () => console.log('Observer got a complete notification'),
        }
      );
    }
  }

  deleteSecret(secret: Secret):void {
    this.secretsSvc.deleteSecret(secret).subscribe(
      {
        next: res => {
          this.notificationService.message(NotificationType.SUCCESS,
            secret.name, "Secret has been deleted", false, null, null);
          this.getSecrets();
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              secret.name, "Secret cannot be deleted (" + err.message + ")", false, null, null);
        },
        complete: () => console.log('Observer got a complete notification'),
      }
    );
  }

  updateSecretProperties() {
    if (this.authenticationType === 'basic') {
      this.secret.token = null;
      this.secret.tokenHeader = null;
    } else if (this.authenticationType === 'token') {
      this.secret.username = null;
      this.secret.password = null;
    }
  }
}