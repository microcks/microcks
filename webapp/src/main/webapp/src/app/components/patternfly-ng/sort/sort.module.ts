import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { BsDropdownConfig, BsDropdownModule } from 'ngx-bootstrap/dropdown';

import { SortComponent } from './sort.component';

/**
 * A module containing objects associated with the sort component
 */
@NgModule({
  imports: [CommonModule, BsDropdownModule.forRoot(), SortComponent],
  declarations: [
    //SortComponent
  ],
  exports: [SortComponent],
  providers: [BsDropdownConfig]
})
export class SortModule {}
