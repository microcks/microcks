import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';

import { BsDropdownConfig, BsDropdownModule } from 'ngx-bootstrap/dropdown';

import { ToastNotificationComponent } from './toast-notification.component';

/**
 * A module containing objects associated with toast notifications
 */
@NgModule({
  imports: [
    BsDropdownModule.forRoot(),
    CommonModule,
    FormsModule,
    ToastNotificationComponent
  ],
  //declarations: [ToastNotificationComponent],
  exports: [ToastNotificationComponent],
  providers: [
    BsDropdownConfig
  ]
})
export class ToastNotificationModule {}
