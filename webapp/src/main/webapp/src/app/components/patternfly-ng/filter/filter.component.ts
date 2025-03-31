import {
  Component,
  DoCheck,
  EventEmitter,
  Input,
  OnInit,
  Output,
  ViewChild,
  ViewEncapsulation
} from '@angular/core';

import { cloneDeep, defaults, find, isEqual, remove } from 'lodash-es';

import { Filter } from './filter';
import { FilterConfig } from './filter-config';
import { FilterEvent } from './filter-event';
import { FilterFieldsComponent } from './filter-fields.component';
import { FilterType } from './filter-type';
import { FilterResultsComponent } from './filter-results.component';

/**
 * Filter component
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { FilterModule } from 'patternfly-ng/filter';
 * // Or
 * import { FilterModule } from 'patternfly-ng';
 *
 * &#64;NgModule({
 *   imports: [FilterModule,...]
 * })
 * export class AppModule(){}
 * </pre></code>
 *
 * Optional:
 * <code><pre>
 * import {
 *   Filter,
 *   FilterConfig,
 *   FilterField,
 *   FilterEvent,
 *   FilterType
 * } from 'patternfly-ng/filter';
 * </pre></code>
 */
@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'pfng-filter',
  templateUrl: './filter.component.html',
  imports: [
    FilterFieldsComponent,
    FilterResultsComponent
  ]
})
export class FilterComponent implements DoCheck, OnInit {
  /**
   * The filter config containing component properties
   */
  @Input() config!: FilterConfig;

  /**
   * The event emitted when a filter has been changed
   */
  @Output('onChange') onChange = new EventEmitter();

  /**
   * The event emitted when a query (i.e., saved filter) has been deleted
   */
  @Output('onDelete') onDelete = new EventEmitter();

  /**
   * The event emitted when a field menu option is selected
   */
  @Output('onFieldSelect') onFilterSelect = new EventEmitter();

  /**
   * The event emitted when a filter has been changed
   */
  @Output('onSave') onSave = new EventEmitter();

  /**
   * The event emitted when the user types ahead in the query input field
   */
  @Output('onTypeAhead') onTypeAhead = new EventEmitter();

  /**
   * A reference to the underlying filter fields component
   */
  @ViewChild('filterFields') private filterFields!: FilterFieldsComponent;

  private defaultConfig = {
    disabled: false
  } as FilterConfig;
  private prevConfig: FilterConfig = new FilterConfig;

  /**
   * The default constructor
   */
  constructor() {
  }

  // Initialization

  /**
   * Setup component configuration upon initialization
   */
  ngOnInit(): void {
    this.setupConfig();
  }

  /**
   * Check if the component config has changed
   */
  ngDoCheck(): void {
    // Do a deep compare on config
    if (!isEqual(this.config, this.prevConfig)) {
      this.setupConfig();
    }
  }

  /**
   * Set up default config
   */
  protected setupConfig(): void {
    if (this.config !== undefined) {
      defaults(this.config, this.defaultConfig);
    } else {
      this.config = cloneDeep(this.defaultConfig);
    }

    if (this.config && this.config.appliedFilters === undefined) {
      this.config.appliedFilters = [];
    }
    this.prevConfig = cloneDeep(this.config);
  }

  // Actions

  /**
   * Handle add filter event
   *
   * @param $event The FilterEvent contining properties for this event
   */
  addFilter($event: FilterEvent): void {
    let newFilter = {
      field: $event.field,
      query: $event.query,
      value: $event.value
    } as Filter;

    if (!this.filterExists(newFilter)) {
      if (newFilter.field.type === FilterType.SELECT || newFilter.field.type === FilterType.TYPEAHEAD) {
        this.enforceSingleSelect(newFilter);
      }
      if (this.config.appliedFilters) {
        this.config.appliedFilters.push(newFilter);
      }
      $event.appliedFilters = this.config.appliedFilters;
      this.onChange.emit($event);
    }
  }

  /**
   * Handle clear filter event
   *
   * @param $event An array of current Filter objects
   */
  clearFilter($event: Filter[]): void {
    this.config.appliedFilters = $event;
    this.onChange.emit({
      appliedFilters: $event
    } as FilterEvent);
  }

  /**
   * Handle delete query (i.e., saved filter) event
   *
   * @param $event The FilterEvent contining properties for this event
   */
  deleteQuery($event: FilterEvent): void {
    this.onDelete.emit($event);
  }

  /**
   * Handle filter field selected event
   *
   * @param $event The FilterEvent contining properties for this event
   */
  fieldSelected($event: FilterEvent): void {
    this.onFilterSelect.emit($event);
  }

  /**
   * Reset current field
   */
  resetCurrentField(): void {
    this.filterFields.reset();
  }

  /**
   * Handle save filter event
   *
   * @param $event An array of current Filter objects
   */
  saveFilter($event: FilterEvent): void {
    this.onSave.emit($event);
  }

  /**
   * Handle type ahead event
   *
   * @param $event The FilterEvent contining properties for this event
   */
  typeAhead($event: FilterEvent) {
    this.onTypeAhead.emit($event);
  }

  // Private

  private enforceSingleSelect(filter: Filter): void {
    const filterField = { title: filter.field.title };
    if (this.config.appliedFilters) {
      remove(this.config.appliedFilters, { field: filterField });
    }
  }

  private filterExists(filter: Filter): boolean {
    let foundFilter = find(this.config.appliedFilters, {
      field: filter.field,
      value: filter.value
    });
    return foundFilter !== undefined;
  }
}
