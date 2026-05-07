import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';

import { BsDropdownConfig, BsDropdownModule } from 'ngx-bootstrap/dropdown';

import { CardFilterComponent } from './card-filter.component';

/**
 * A module containing objects associated with card filter components
 */
@NgModule({
  imports: [
    BsDropdownModule.forRoot(),
    CommonModule,
    FormsModule,
    CardFilterComponent
  ],
  //declarations: [CardFilterComponent],
  exports: [CardFilterComponent],
  providers: [BsDropdownConfig]
})
export class CardFilterModule {}
