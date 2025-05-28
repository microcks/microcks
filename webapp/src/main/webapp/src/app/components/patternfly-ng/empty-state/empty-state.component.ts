import {
  Component,
  DoCheck,
  EventEmitter,
  Input,
  OnInit,
  Output,
  ViewEncapsulation
} from '@angular/core';

import { cloneDeep, defaults, isEqual } from 'lodash-es';

import { Action } from '../action/action';
import { EmptyStateConfig } from './empty-state-config';
import { CommonModule } from '@angular/common';

/**
 * Component for rendering an empty state.
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { EmptyStateModule } from 'patternfly-ng/empty-state';
 * // Or
 * import { EmptyStateModule } from 'patternfly-ng';
 *
 * &#64;NgModule({
 *   imports: [EmptyStateModule,...]
 * })
 * export class AppModule(){}
 * </pre></code>
 *
 * Optional:
 * <code><pre>
 * import { EmptyStateConfig } from 'patternfly-ng/empty-state';
 * </pre></code>
 */
@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'pfng-empty-state',
  templateUrl: './empty-state.component.html',
  imports: [
    CommonModule
  ]
})
export class EmptyStateComponent implements DoCheck, OnInit {
  /**
   * The empty state config containing component properties
   */
  @Input() config!: EmptyStateConfig;

  /**
   * The event emitted when an action is selected
   */
  @Output('onActionSelect') onActionSelect = new EventEmitter();

  private defaultConfig = {
    title: 'No Items Available'
  } as EmptyStateConfig;
  private prevConfig!: EmptyStateConfig;

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
    this.prevConfig = cloneDeep(this.config);
  }

  // Private

  protected handleAction(action: Action): void {
    if (action && action.disabled !== true) {
      this.onActionSelect.emit(action);
    }
  }
}
