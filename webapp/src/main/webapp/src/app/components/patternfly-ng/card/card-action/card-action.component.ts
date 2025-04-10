import {
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
  ViewEncapsulation
} from '@angular/core';
import { CommonModule } from '@angular/common';

import { CardAction } from './card-action';

/**
 * Card action component
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { CardActionModule } from 'patternfly-ng/card';
 * // Or
 * import { CardActionModule } from 'patternfly-ng';
 *
 * &#64;NgModule({
 *   imports: [CardActionModule,...]
 * })
 * export class AppModule(){}
 * </pre></code>
 *
 * Optional:
 * <code><pre>
 * import { CardAction } from 'patternfly-ng/card';
 * </pre></code>
 */
@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'pfng-card-action',
  templateUrl: './card-action.component.html',
  imports: [
    CommonModule
  ]
})
export class CardActionComponent implements OnInit {
  /**
   * The card filters
   */
  @Input() action?: CardAction;

  /**
   * The event emitted when a filter is selected
   */
  @Output('onActionSelect') onActionSelect = new EventEmitter();

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
  }

  // Actions

  protected select($event: MouseEvent): void {
    this.onActionSelect.emit(this.action);
  }
}
