import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';

import { CardActionComponent } from './card-action.component';

/**
 * A module containing objects associated with card action components
 */
@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    CardActionComponent
  ],
  //declarations: [CardActionComponent],
  exports: [CardActionComponent]
})
export class CardActionModule {}
