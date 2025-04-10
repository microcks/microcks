import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';

import { BsDropdownConfig, BsDropdownModule } from 'ngx-bootstrap/dropdown';
import { PopoverModule } from 'ngx-bootstrap/popover';
import { TooltipConfig, TooltipModule } from 'ngx-bootstrap/tooltip';

import { FilterComponent } from './filter.component';
import { FilterFieldsComponent } from './filter-fields.component';
import { FilterResultsComponent } from './filter-results.component';
import { SearchHighlightPipeModule } from '../pipe/search-highlight/search-highlight.pipe.module';
import { TruncatePipeModule } from '../pipe/truncate/truncate.pipe.module';

/**
 * A module containing objects associated with filter components
 */
@NgModule({
  imports: [
    BsDropdownModule.forRoot(),
    CommonModule,
    FilterComponent, FilterFieldsComponent, FilterResultsComponent,
    FormsModule,
    PopoverModule.forRoot(),
    SearchHighlightPipeModule,
    TooltipModule.forRoot(),
    TruncatePipeModule
  ],
  declarations: [
    //FilterComponent, FilterFieldsComponent, FilterResultsComponent
  ],
  exports: [FilterComponent, FilterFieldsComponent, FilterResultsComponent],
  providers: [BsDropdownConfig, TooltipConfig]
})
export class FilterModule {}
