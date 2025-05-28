import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { BsDropdownConfig, BsDropdownModule } from 'ngx-bootstrap/dropdown';

import { ActionModule } from '../action/action.module';
import { FilterModule } from '../filter/filter.module';
import { SortModule } from '../sort/sort.module';
import { ToolbarComponent } from './toolbar.component';

/**
 * A module containing objects associated with the toolbar component
 */
@NgModule({
  imports: [
    ActionModule,
    BsDropdownModule.forRoot(),
    CommonModule,
    FilterModule,
    SortModule,
    ToolbarComponent
  ],
  //declarations: [ToolbarComponent],
  exports: [ToolbarComponent],
  providers: [BsDropdownConfig]
})
export class ToolbarModule {}
