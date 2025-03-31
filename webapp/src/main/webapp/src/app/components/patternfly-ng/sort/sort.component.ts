import {
  Component,
  DoCheck,
  EventEmitter,
  Input,
  OnInit,
  Output,
  ViewEncapsulation
} from '@angular/core';
import { CommonModule } from '@angular/common';

import { cloneDeep, defaults, isEqual } from 'lodash-es';

import { SortConfig } from './sort-config';
import { SortField } from './sort-field';
import { SortEvent } from './sort-event';


/**
 * Sort component
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { SortModule } from 'patternfly-ng/sort';
 * // Or
 * import { SortModule } from 'patternfly-ng';
 *
 * &#64;NgModule({
 *   imports: [SortModule,...]
 * })
 * export class AppModule(){}
 * </pre></code>
 *
 * Optional:
 * <code><pre>
 * import { SortConfig, SortEvent, SortField } from 'patternfly-ng/sort';
 * </pre></code>
 */
@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'pfng-sort',
  templateUrl: './sort.component.html',
  imports: [
    CommonModule
  ]
})
export class SortComponent implements DoCheck, OnInit {
  /**
   * The sort config containing component properties
   */
  @Input() config?: SortConfig;

  /**
   * The event emitted when the sort has changed
   */
  @Output('onChange') onChange = new EventEmitter();

  currentField: SortField = new SortField;
  private defaultConfig: SortConfig = {
    isAscending: true,
    visible: true
  } as SortConfig;
  private prevConfig: SortConfig = new SortConfig;

  /**
   * The default constructor
   */
  constructor() {
  }

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

    if (this.config && this.config.fields && this.config.fields.length > 0) {
      if (this.currentField === undefined) {
        this.currentField = this.config.fields[0];
      }
      if (this.config.isAscending === undefined) {
        this.config.isAscending = true;
      }
    }
    this.prevConfig = cloneDeep(this.config);
  }

  // Actions

  getIconStyleClass(): string {
    let iconStyleClass: string;
    if (this.currentField && this.currentField.sortType
        && this.currentField.sortType === 'numeric') {
      if (this.config?.isAscending) {
        iconStyleClass = 'fa fa-sort-numeric-asc';
      } else {
        iconStyleClass = 'fa fa-sort-numeric-desc';
      }
    } else {
      if (this.config?.isAscending) {
        iconStyleClass = 'fa fa-sort-alpha-asc';
      } else {
        iconStyleClass = 'fa fa-sort-alpha-desc';
      }
    }
    return iconStyleClass;
  }

  onChangeDirection(): void {
    this.config!.isAscending = !this.config?.isAscending;
    this.onChange.emit({
      field: this.currentField,
      isAscending: this.config?.isAscending
    } as SortEvent);
  }

  selectField(field: SortField): void {
    this.currentField = field;
    this.onChange.emit({
      field: this.currentField,
      isAscending: this.config?.isAscending
    } as SortEvent);
  }
}
