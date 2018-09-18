import { Component, OnInit } from '@angular/core';

import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';


@Component({
  selector: 'admin-page',
  templateUrl: './admin.page.html',
  styleUrls: ['./admin.page.css']
})
export class AdminPageComponent implements OnInit {

  notifications: Notification[];
  
  constructor(private notificationService: NotificationService) {}

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
  }

  handleCloseNotification($event: NotificationEvent): void {
    this.notificationService.remove($event.notification);
  }
}