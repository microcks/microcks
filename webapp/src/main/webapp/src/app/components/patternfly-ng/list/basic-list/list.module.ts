import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';

import { EmptyStateModule } from '../../empty-state/empty-state.module';
import { ListComponent } from './list.component';
//import { ListExpandToggleComponent } from './list-expand-toggle.component';
import { SortArrayPipeModule } from '../../pipe/sort-array/sort-array.pipe.module';

/**
 * A module containing objects associated with basic list components
 */
@NgModule({
  imports: [
    CommonModule,
    EmptyStateModule,
    FormsModule,
    ListComponent,
    //ListExpandToggleComponent,
    SortArrayPipeModule
  ],
  //declarations: [ListComponent, ListExpandToggleComponent],
  exports: [
    ListComponent, 
    //ListExpandToggleComponent
  ]
})
export class ListModule {}
