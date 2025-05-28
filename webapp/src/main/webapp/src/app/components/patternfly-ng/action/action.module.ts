import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';

import { BsDropdownConfig, BsDropdownModule } from 'ngx-bootstrap/dropdown';

import { ActionComponent } from './action.component';

/**
 * A module containing objects associated with action components
 */
@NgModule({
  imports: [
    ActionComponent,
    BsDropdownModule.forRoot(),
    CommonModule,
    FormsModule,
  ],
  //declarations: [ActionComponent],
  exports: [ActionComponent],
  providers: [BsDropdownConfig]
})
export class ActionModule {}
