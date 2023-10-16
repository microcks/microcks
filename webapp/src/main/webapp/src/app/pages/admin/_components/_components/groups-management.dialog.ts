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

import { BsModalRef } from 'ngx-bootstrap/modal';
import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';

import { User } from '../../../../models/user.model';
import { UsersService } from '../../../../services/users.service';

@Component({
  selector: 'groups-management-dialog',
  templateUrl: './groups-management.dialog.html',
  styleUrls: ['./groups-management.dialog.css']
})
export class GroupsManagementDialogComponent implements OnInit {
  title: string;
  closeBtnName: string;
  user: User;
  userGroups: any[];
  groups: any[];
  availableGroups: any[] = [];

  memberSelected: any[] = [];
  availableSelected: any[] = [];

  constructor(public usersSvc: UsersService, public bsModalRef: BsModalRef, private notificationService: NotificationService) {}

  ngOnInit() {
    this.computeAvailableGroups();
  }

  computeAvailableGroups(): void {
    for (let i=0; i<this.groups.length; i++) {
      if (this.userGroups.map(g => g.id).indexOf(this.groups[i].id) == -1) {
        this.availableGroups.push(this.groups[i]);
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
    for (let i=0; i<this.memberSelected.length; i++) {
      let memberOfGroup = this.memberSelected[i];
      this.usersSvc.removeUserFromGroup(this.user.id, memberOfGroup.id).subscribe(
        {
          next: res => {
            this.userGroups.splice(this.userGroups.indexOf(memberOfGroup), 1);
            this.availableGroups.push(memberOfGroup);
            this.notificationService.message(NotificationType.SUCCESS,
              this.user.username, this.user.username + " is no longer member of " + memberOfGroup.path, false, null, null);
          },
          error: err => {
            this.notificationService.message(NotificationType.DANGER,
              this.user.username, this.user.username + " cannot leave " + memberOfGroup.path + " (" + err.message + ")", false, null, null);
          },
          complete: () => console.log('Observer got a complete notification'),
        }
      );
    }
    this.memberSelected = [];
  }
  joinSelectedGroups(): void {
    for (let i=0; i<this.availableSelected.length; i++) {
      let availableGroup = this.availableSelected[i];
      this.usersSvc.putUserInGroup(this.user.id, availableGroup.id).subscribe(
        {
          next: res => {
            this.availableGroups.splice(this.availableGroups.indexOf(availableGroup), 1);
            this.userGroups.push(availableGroup);
            this.notificationService.message(NotificationType.SUCCESS,
              this.user.username, this.user.username + " is now member of " + availableGroup.path, false, null, null);
          },
          error: err => {
            this.notificationService.message(NotificationType.DANGER,
              this.user.username, this.user.username + " cannot join " + availableGroup.path + " (" + err.message + ")", false, null, null);
          },
          complete: () => console.log('Observer got a complete notification'),
        }
      );
    }
    this.availableSelected = [];
  }
}