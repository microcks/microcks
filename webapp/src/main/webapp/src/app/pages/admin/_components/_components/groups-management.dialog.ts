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
import { NotificationService, NotificationType } from '../../../../components/patternfly-ng/notification';

import { UsersService } from '../../../../services/users.service';

export type KeycloakUser = {
  id: string;
  username: string;
}

@Component({
  selector: 'app-groups-management-dialog',
  templateUrl: './groups-management.dialog.html',
  styleUrls: ['./groups-management.dialog.css'],
  imports: [
    CommonModule
  ]
})
export class GroupsManagementDialogComponent implements OnInit {
  
  closeBtnName?: string;
  
  //user: User;
  user!: KeycloakUser;
  userGroups!: any[];
  groups!: any[];
  availableGroups: any[] = [];

  memberSelected: any[] = [];
  availableSelected: any[] = [];

  constructor(public usersSvc: UsersService, public bsModalRef: BsModalRef, private notificationService: NotificationService) {}

  ngOnInit() {
    this.computeAvailableGroups();
  }

  computeAvailableGroups(): void {
    for (const group of this.groups) {
      if (this.userGroups.map(g => g.id).indexOf(group.id) == -1) {
        this.availableGroups.push(group);
      }
    }
  }

  isMemberGroupSelected(group: any): boolean {
    return this.memberSelected.indexOf(group) != -1;
  }
  isAvailableGroupSelected(group: any): boolean {
    return this.availableSelected.indexOf(group) != -1;
  }

  toggleMember(group: any): void {
    this.isMemberGroupSelected(group) ?
      this.memberSelected.splice(this.memberSelected.indexOf(group), 1) : this.memberSelected.push(group);
  }
  toggleAvailable(group: any): void {
    this.isAvailableGroupSelected(group) ?
      this.availableSelected.splice(this.availableSelected.indexOf(group), 1) : this.availableSelected.push(group);
  }

  leaveSelectedGroups(): void {
    for (const memberOfGroup of this.memberSelected) {
      this.usersSvc.removeUserFromGroup(this.user.id, memberOfGroup.id).subscribe(
        {
          next: res => {
          this.userGroups.splice(this.userGroups.indexOf(memberOfGroup), 1);
          this.availableGroups.push(memberOfGroup);
          this.notificationService.message(NotificationType.SUCCESS,
            this.user.username, this.user.username + ' is no longer member of ' + memberOfGroup.path, false);
          },
          error: err => {
          this.notificationService.message(NotificationType.DANGER,
            this.user.username, this.user.username + ' cannot leave ' + memberOfGroup.path + ' (' + err.message + ')', false);
          },
          complete: () => console.log('Observer got a complete notification'),
        }
      );
    }
    this.memberSelected = [];
  }
  joinSelectedGroups(): void {
    for (const availableGroup of this.availableSelected) {
      this.usersSvc.putUserInGroup(this.user.id, availableGroup.id).subscribe(
        {
          next: res => {
            this.availableGroups.splice(this.availableGroups.indexOf(availableGroup), 1);
            this.userGroups.push(availableGroup);
            this.notificationService.message(NotificationType.SUCCESS,
              this.user.username, this.user.username + ' is now member of ' + availableGroup.path, false);
          },
          error: err => {
            this.notificationService.message(NotificationType.DANGER,
              this.user.username, this.user.username + ' cannot join ' + availableGroup.path + ' (' + err.message + ')', false);
          },
          complete: () => {} //console.log('Observer got a complete notification'),
        }
      );
    }
    this.availableSelected = [];
  }
}
