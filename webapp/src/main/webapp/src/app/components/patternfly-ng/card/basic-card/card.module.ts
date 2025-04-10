import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';

import { CardActionModule } from '../card-action/card-action.module';
import { CardComponent } from '../basic-card/card.component';
import { CardFilterModule } from '../card-filter/card-filter.module';

/**
 * A module containing objects associated with basic card components
 */
@NgModule({
  imports: [
    CardActionModule,
    CardComponent,
    CardFilterModule,
    CommonModule,
    FormsModule
  ],
  //declarations: [CardComponent],
  exports: [CardComponent]
})
export class CardModule {}
