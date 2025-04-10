import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';

import { InlineNotificationComponent } from './inline-notification.component';

/**
 * A module containing objects associated with inline notifications
 */
@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    InlineNotificationComponent
  ],
  //declarations: [InlineNotificationComponent],
  exports: [
    InlineNotificationComponent
  ]
})
export class InlineNotificationModule {}
