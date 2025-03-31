import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';

import { ToastNotificationListComponent } from './toast-notification-list.component';
import { ToastNotificationModule } from '../toast-notification';

/**
 * A module containing objects associated with toast notification lists
 */
@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    ToastNotificationListComponent,
    ToastNotificationModule
  ],
  //declarations: [ToastNotificationListComponent],
  exports: [
    ToastNotificationListComponent
  ]
})
export class ToastNotificationListModule {}
