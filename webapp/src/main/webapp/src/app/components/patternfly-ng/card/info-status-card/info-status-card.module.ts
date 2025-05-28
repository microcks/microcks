import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';

import { InfoStatusCardComponent } from './info-status-card.component';

/**
 * A module containing objects associated with info status card components
 */
@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    InfoStatusCardComponent
  ],
  //declarations: [InfoStatusCardComponent],
  exports: [InfoStatusCardComponent]
})
export class InfoStatusCardModule {}
