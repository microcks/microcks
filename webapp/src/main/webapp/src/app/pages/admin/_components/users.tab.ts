/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import { Component, OnInit } from '@angular/core';

import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';
import { PaginationConfig, PaginationEvent } from 'patternfly-ng/pagination';
import { ToolbarConfig } from 'patternfly-ng/toolbar';
import { FilterConfig, FilterEvent, FilterField, FilterType } from 'patternfly-ng/filter';

import { User } from '../../../models/user.model';
import { IAuthenticationService } from '../../../services/auth.service';
import { UsersService } from '../../../services/users.service';

@Component({
  selector: 'users-tab',
  templateUrl: './users.tab.html',
  styleUrls: ['./users.tab.css']
})
export class UsersTabComponent implements OnInit {

  allowedToManageUsers: boolean = true;
  users: User[];
  usersCount: number;
  usersRoles: {};
  toolbarConfig: ToolbarConfig;
  filterConfig: FilterConfig;
  paginationConfig: PaginationConfig;
  filterTerm: string = null;
  filtersText: string = '';

  constructor(private usersSvc: UsersService, protected authService: IAuthenticationService, private notificationService: NotificationService) {}

  ngOnInit() {
    this.getUsers();
    this.countUsers();
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

  getUsers(page: number = 1): void {
    this.usersSvc.getUsers(page).subscribe(
      {
        next: results => {
          this.users = results;
          this.usersRoles = {};
        },
        error: err => {
          if (err.status == 403) {
            this.notificationService.message(NotificationType.DANGER,
              "Authorization Error", "Current user does not appear to have the **manage-users** role from **realm-management** client. Please contact your administrator to setup correct role.", false, null, null);
          } else {
            this.notificationService.message(NotificationType.WARNING,
              "Unknown Error", err.message, false, null, null);
          }
        }
      }
    );
  }
  filterUsers(filter: string): void {
    this.usersSvc.filterUsers(filter).subscribe(results => {
      this.users = results;
      this.usersRoles = {};
      this.filterConfig.resultsCount = results.length;
    });
  }

  countUsers(): void {
    this.usersSvc.countUsers().subscribe(results => {
      this.usersCount = results;
      this.paginationConfig.totalItems = this.usersCount;
    });
  }

  getUserRoles(userId: string): void {
    this.usersSvc.getUserRoles(userId).subscribe(
      {
        next: results => {
          this.usersRoles[userId] = results;
        },
        error: err => {
          if (err.status == 403) {
            this.notificationService.message(NotificationType.DANGER,
              "Authorization Error", "Current user does not appear to have the **manage-clients** role from **realm-management** client. Please contact your administrator to setup correct role.", false, null, null);
          } else {
            this.notificationService.message(NotificationType.WARNING,
              "Unknown Error", err.message, false, null, null);
          }
        }
      }
    );
  }
  userHasRole(userId: string, expectedRole: string): boolean {
    if (this.usersRoles[userId] === undefined) {
      return false;
    }
    for (let i = 0; i < this.usersRoles[userId].length; i++) {
      const role = this.usersRoles[userId][i];
      if (expectedRole === role.name) {
        return true;
      }
    }
    return false;
  }

  assignRoleToUser(userId: string, userName: string, role: string) {
    this.usersSvc.assignRoleToUser(userId, role).subscribe(
      {
        next: res => {
          this.notificationService.message(NotificationType.SUCCESS,
            userName, userName + " is now " + role, false, null, null);
          this.getUserRoles(userId);
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
            userName, userName + " cannot be made " + role + " (" + err.message + ")", false, null, null);
        },
        complete: () => console.log('Observer got a complete notification'),
      }
    );
  }
  removeRoleFromUser(userId: string, userName: string, role: string) {
    this.usersSvc.removeRoleFromUser(userId, role).subscribe(
      {
        next: res => {
          this.notificationService.message(NotificationType.SUCCESS,
            userName, userName + " is no more " + role, false, null, null);
          this.getUserRoles(userId);
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
            userName, userName + " cannot be downgraded " + role + " (" + err.message + ")", false, null, null);
        },
        complete: () => console.log('Observer got a complete notification'),
      }
    );
  }

  handlePageSize($event: PaginationEvent) {
    //this.updateItems();
  }

  handlePageNumber($event: PaginationEvent) {
    this.getUsers($event.pageNumber)
  }

  handleFilter($event: FilterEvent): void {
    this.filtersText = '';
    if ($event.appliedFilters.length == 0) {
      this.filterTerm = null;
      this.getUsers();
    } else {
      $event.appliedFilters.forEach((filter) => {
        this.filtersText += filter.field.title + ' : ' + filter.value + '\n';
        this.filterTerm = filter.value;
      });
      this.filterUsers(this.filterTerm);
    }
  }
}