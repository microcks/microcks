import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { EmptyStateComponent } from './empty-state.component';

/**
 * A module containing objects associated with the empty state component
 */
@NgModule({
  imports: [
    CommonModule,
    EmptyStateComponent
  ],
  //declarations: [EmptyStateComponent],
  exports: [EmptyStateComponent]
})
export class EmptyStateModule {}
