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

import { CardAction } from '../card-action/card-action';
import { CardBase } from '../card-base';
import { CardConfig } from './card-config';
import { CardFilter } from '../card-filter/card-filter';
import { CardFilterPosition } from '../card-filter/card-filter-position';
import { CardFilterModule } from '../card-filter';
import { CardActionModule } from '../card-action';

/**
 * Card component
 *
 * For customization, use the templates named headerTemplate and footerTemplate.
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { CardModule } from 'patternfly-ng/card';
 * // Or
 * import { CardModule } from 'patternfly-ng';
 *
 * &#64;NgModule({
 *   imports: [CardModule,...]
 * })
 * export class AppModule(){}
 * </pre></code>
 *
 * Optional:
 * <code><pre>
 * import { CardAction, CardConfig, CardFilter, CardFilterPosition } from 'patternfly-ng/card';
 * </pre></code>
 */
@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'pfng-card',
  templateUrl: './card.component.html',
  imports: [
    CommonModule,
    CardActionModule,
    CardFilterModule
  ]
})
export class CardComponent extends CardBase implements DoCheck, OnInit {
  /**
   * The card config containing component properties
   */
  @Input() config!: CardConfig;

  /**
   * The event emitted when an action is selected
   */
  @Output('onActionSelect') onActionSelect = new EventEmitter();

  /**
   * The event emitted when a filter is selected
   */
  @Output('onFilterSelect') onFilterSelect = new EventEmitter();

  private defaultConfig = {
    filterPosition: CardFilterPosition.FOOTER,
    noPadding: false,
    titleBorder: true,
    topBorder: true
  } as CardConfig;
  private prevConfig!: CardConfig;

  /**
   * The default constructor
   */
  constructor() {
    super();
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

  // Actions

  /**
   * Handle the event emitted when an action is selected
   *
   * @param {CardAction} $event The emitted CardAction object
   */
  protected handleActionSelect($event: CardAction): void {
    this.onActionSelect.emit($event);
  }

  /**
   * Handle the event emitted when a filter is selected
   *
   * @param {CardFilter} $event The emitted CardFilter object
   */
  protected handleFilterSelect($event: CardFilter): void {
    this.onFilterSelect.emit($event);
  }

  // Accessors

  /**
   * Indicates that the footer should be shown in the footer
   *
   * @returns {boolean} True if the footer should be shown in the footer
   */
  protected get showFilterInFooter(): boolean {
    return (this.config.filters !== undefined && this.config.filterPosition !== undefined
      && this.config.filterPosition === CardFilterPosition.FOOTER);
  }

  /**
   * Indicates that the footer should be shown in the header
   *
   * @returns {boolean} True if the footer should be shown in the header
   */
  protected get showFilterInHeader(): boolean {
    return (this.config.filters !== undefined && this.config.filterPosition !== undefined
      && this.config.filterPosition === CardFilterPosition.HEADER);
  }

  /**
   * Indicates that the footer should be shown
   *
   * @returns {boolean} True if the footer should be shown
   */
  get showFooter(): boolean {
    return (this.footerTemplate !== undefined || this.showFilterInFooter || this.config.action !== undefined);
  }

  /**
   * Indicates that the header should be shown
   *
   * @returns {boolean} True if the header should be shown
   */
  get showHeader(): boolean {
    return (this.headerTemplate !== undefined || this.showFilterInHeader || this.config.title !== undefined);
  }
}
