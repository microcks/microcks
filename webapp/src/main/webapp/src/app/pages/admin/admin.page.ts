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

import { TabsModule } from 'ngx-bootstrap/tabs';

import {
  Notification,
  NotificationEvent,
  NotificationService,
  ToastNotificationListComponent
} from '../../components/patternfly-ng/notification';

import { SecretsTabComponent } from './_components/secrets.tab';
import { UsersTabComponent } from './_components/users.tab';
import { SnapshotsTabComponent } from './_components/snapshots.tab';

@Component({
  selector: 'app-admin-page',
  templateUrl: './admin.page.html',
  styleUrls: ['./admin.page.css'],
  imports: [
    UsersTabComponent,
    SecretsTabComponent,
    SnapshotsTabComponent,
    TabsModule,
    ToastNotificationListComponent
  ]
})
export class AdminPageComponent implements OnInit {

  notifications: Notification[] = [];

  constructor(private notificationService: NotificationService) {}

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
  }

  handleCloseNotification($event: NotificationEvent): void {
    this.notificationService.remove($event.notification);
  }
}
