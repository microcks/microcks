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
import { forkJoin } from 'rxjs';

import { BsDropdownModule } from 'ngx-bootstrap/dropdown';
import { BsModalService, BsModalRef } from 'ngx-bootstrap/modal';

import { NotificationService, NotificationType } from '../../../components/patternfly-ng/notification';
import { PaginationConfig, PaginationModule, PaginationEvent } from '../../../components/patternfly-ng/pagination';
import { ToolbarConfig, ToolbarModule } from '../../../components/patternfly-ng/toolbar';
import {
  FilterConfig,
  FilterEvent,
  FilterField,
  FilterType,
} from '../../../components/patternfly-ng/filter';

import { User } from '../../../models/user.model';
import { IAuthenticationService } from '../../../services/auth.service';
import { ConfigService } from '../../../services/config.service';
import { ServicesService } from '../../../services/services.service';
import { UsersService } from '../../../services/users.service';
import { GroupsManagementDialogComponent } from './_components/groups-management.dialog';

type KeycloakUser = {
  id: string;
  username: string;
  firstName: string;
  lastName: string;
}

@Component({
  selector: 'app-users-tab',
  templateUrl: './users.tab.html',
  styleUrls: ['./users.tab.css'],
  imports: [
    CommonModule,
    BsDropdownModule,
    PaginationModule,
    ToolbarModule,
  ]
})
export class UsersTabComponent implements OnInit {
  
  modalRef?: BsModalRef;
  allowedToManageUsers = true;
  users?: User[];
  usersCount: number = 0;
  usersRoles: Record<string, any[]> = {};
  groups?: any[];
  managerGroup: any;
  tenants?: string[];
  toolbarConfig: ToolbarConfig = new ToolbarConfig;
  filterConfig: FilterConfig = new FilterConfig;
  paginationConfig: PaginationConfig = new PaginationConfig;
  filterTerm: string | null = null;
  filtersText = '';

  constructor(
    private usersSvc: UsersService,
    protected authService: IAuthenticationService,
    private config: ConfigService,
    private servicesSvc: ServicesService,
    private modalService: BsModalService,
    private notificationService: NotificationService
  ) {}

  ngOnInit() {
    if (this.hasRepositoryTenancyEnabled()) {
      this.getAndUpdateGroups();
    }
    this.getUsers();
    this.countUsers();
    this.paginationConfig = {
      pageNumber: 1,
      pageSize: 20,
      pageSizeIncrements: [],
      totalItems: 20,
    } as PaginationConfig;

    this.filterConfig = {
      fields: [
        {
          id: 'name',
          title: 'Name',
          placeholder: 'Filter by Name...',
          type: FilterType.TEXT,
        },
      ] as FilterField[],
      resultsCount: 20,
      appliedFilters: [],
    } as FilterConfig;

    this.toolbarConfig = {
      actionConfig: undefined,
      filterConfig: this.filterConfig,
      sortConfig: undefined,
      views: [],
    } as ToolbarConfig;
  }

  getAndUpdateGroups(): void {
    this.usersSvc.getGroups().subscribe({
      next: (results) => {
        // Flatten the groups.
        this.groups = results
          .filter((group) => group.path === '/microcks')
          .flatMap((group) => group.subGroups);
        // We may a direct array of subGroups because of using the new populateHierarchy=false flag.
        if (this.groups.length == 0 || (this.groups.length == 1 && this.groups[0].subGroups.length == 0)) {
          this.groups = results.filter((group) =>
            group.path.startsWith('/microcks/manager')
          );
        }
        this.groups.forEach((group) => {
          if (group.path === '/microcks/manager') {
            this.managerGroup = group;
          }
        });

        // Flatten once again if necessary.
        this.groups = this.groups.flatMap((group) => {
          if (group.subGroups.length > 0) {
            return group.subGroups;
          } else {
            return group;
          }
        });
        this.checkGroupsCompleteness();
      },
      error: (err) => {
        if (err.status == 403) {
          this.notificationService.message(
            NotificationType.DANGER,
            'Authorization Error',
            'Current user does not appear to have the **manage-groups** role from **realm-management** client. Please contact your administrator to setup correct role.',
            false
          );
        } else {
          this.notificationService.message(
            NotificationType.WARNING,
            'Unknown Error',
            err.message,
            false
          );
        }
      },
    });
  }
  checkGroupsCompleteness(): void {
    this.servicesSvc.getServicesLabels().subscribe((results) => {
      this.tenants = results[this.repositoryFilterFeatureLabelKey()];
      // Check that each tenant has correct groups, otherwise create them.
      this.tenants!.forEach((tenant) => {
        const mGroup = this.groups?.find(
          (g) => g.path === '/microcks/manager/' + tenant
        );
        if (mGroup == null) {
          this.usersSvc.createGroup(this.managerGroup.id, tenant).subscribe();
        }
      });
    });
  }

  kUser(user: User): KeycloakUser {
    return user as KeycloakUser;
  }

  getUsers(page: number = 1): void {
    this.usersSvc.getUsers(page).subscribe({
      next: (results) => {
        this.users = results;
        this.usersRoles = {};
      },
      error: (err) => {
        if (err.status == 403) {
          this.notificationService.message(
            NotificationType.DANGER,
            'Authorization Error',
            'Current user does not appear to have the **manage-users** role from **realm-management** client. Please contact your administrator to setup correct role.',
            false
          );
        } else {
          this.notificationService.message(
            NotificationType.WARNING,
            'Unknown Error',
            err.message,
            false
          );
        }
      },
    });
  }
  filterUsers(filter: string): void {
    this.usersSvc.filterUsers(filter).subscribe((results) => {
      this.users = results;
      this.usersRoles = {};
      this.filterConfig.resultsCount = results.length;
    });
  }

  countUsers(): void {
    this.usersSvc.countUsers().subscribe((results) => {
      this.usersCount = results;
      this.paginationConfig.totalItems = this.usersCount;
    });
  }

  getUserRoles(userId: string): void {
    const userRoles = this.usersSvc.getUserRoles(userId);
    const userRealmRoles = this.usersSvc.getUserRealmRoles(userId);

    forkJoin([userRoles, userRealmRoles]).subscribe({
      next: (results) => {
        this.usersRoles[userId] = results[0];
        this.usersRoles[userId].push(...results[1]);
        console.log(JSON.stringify(this.usersRoles[userId]));
      },
      error: (err) => {
        if (err.status == 403) {
          this.notificationService.message(
            NotificationType.DANGER,
            'Authorization Error',
            'Current user does not appear to have the **manage-clients** role from **realm-management** client. Please contact your administrator to setup correct role.',
            false
          );
        } else {
          this.notificationService.message(
            NotificationType.WARNING,
            'Unknown Error', err.message,
            false
          );
        }
      },
    });
  }
  userHasRole(userId: string, expectedRole: string): boolean {
    if (expectedRole === 'user') {
      expectedRole = 'default-roles-' + this.usersSvc.getRealmName();
    }
    if (this.usersRoles[userId] === undefined) {
      return false;
    }
    for (const role of this.usersRoles[userId]) {
      if (expectedRole === role.name) {
        return true;
      }
    }
    return false;
  }

  assignRoleToUser(userId: string, userName: string, role: string) {
    this.usersSvc.assignRoleToUser(userId, role).subscribe({
      next: (res) => {
        this.notificationService.message(
          NotificationType.SUCCESS,
          userName, userName + ' is now ' + role,
          false
        );
        this.getUserRoles(userId);
      },
      error: (err) => {
        this.notificationService.message(
          NotificationType.DANGER,
          userName, userName + ' cannot be made ' + role + ' (' + err.message + ')',
          false
        );
      },
      complete: () => {} //console.log('Observer got a complete notification'),
    });
  }
  removeRoleFromUser(userId: string, userName: string, role: string) {
    this.usersSvc.removeRoleFromUser(userId, role).subscribe({
      next: (res) => {
        this.notificationService.message(
          NotificationType.SUCCESS,
          userName, userName + ' is no more ' + role,
          false
        );
        this.getUserRoles(userId);
      },
      error: (err) => {
        this.notificationService.message(
          NotificationType.DANGER,
          userName, userName + ' cannot be downgraded ' + role + ' (' + err.message + ')',
          false
        );
      },
      complete: () => {} //console.log('Observer got a complete notification'),
    });
  }

  openGroupsManagementDialog(user: User): void {
    this.usersSvc.getUserGroups((user as any).id).subscribe((userGroups) => {
      const initialState = {
        user: {
          id: (user as any).id,
          username: (user as any).username,
        },
        userGroups: userGroups.filter((group) =>
          group.path.startsWith('/microcks/manager/')
        ),
        groups: this.groups,
      };
      this.modalRef = this.modalService.show(GroupsManagementDialogComponent, {
        initialState,
      });
      this.modalRef.content.closeBtnName = 'Close';
    });
  }

  handlePageSize($event: PaginationEvent) {
    // this.updateItems();
  }

  handlePageNumber($event: PaginationEvent) {
    this.getUsers($event.pageNumber);
  }

  handleFilter($event: FilterEvent): void {
    this.filtersText = '';
    if (!$event.appliedFilters || $event.appliedFilters.length == 0) {
      this.filterTerm = null;
      this.getUsers();
    } else {
      $event.appliedFilters.forEach((filter) => {
        this.filtersText += filter.field.title + ' : ' + filter.value + '\n';
        this.filterTerm = filter.value;
      });
      this.filterUsers(this.filterTerm!);
    }
  }

  public hasRepositoryTenancyEnabled(): boolean {
    return this.config.hasFeatureEnabled('repository-filter');
  }
  public repositoryTenantLabel(): string {
    return this.config
      .getFeatureProperty('repository-filter', 'label-key')
      .toLowerCase();
  }

  public repositoryFilterFeatureLabelKey(): string {
    return this.config.getFeatureProperty('repository-filter', 'label-key');
  }
}
