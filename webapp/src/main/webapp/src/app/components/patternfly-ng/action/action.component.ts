import {
  Component,
  DoCheck,
  ElementRef,
  EventEmitter,
  Input,
  OnInit,
  Output,
  TemplateRef,
  ViewEncapsulation
} from '@angular/core';
import { CommonModule } from '@angular/common';

import { clone, cloneDeep, defaults, isEqual } from 'lodash-es';

import { Action } from './action';
import { ActionConfig } from './action-config';

/**
 * List actions component.
 *
 * By default, buttons and kebab have no padding so they may inherit stying from components such as list and toolbar.
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { ActionModule } from 'patternfly-ng/action';
 * // Or
 * import { ActionModule } from 'patternfly-ng';
 *
 * // NGX Bootstrap
 * import { BsDropdownConfig, BsDropdownModule } from 'ngx-bootstrap/dropdown';
 *
 * &#64;NgModule({
 *   imports: [ActionModule, BsDropdownModule.forRoot(),...],
 *   providers: [BsDropdownConfig]
 * })
 * export class AppModule(){}
 * </pre></code>
 *
 * Optional:
 * <code><pre>
 * import { Action, ActionConfig } from 'patternfly-ng/action';
 * </pre></code>
 */
@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'pfng-action',
  templateUrl: './action.component.html',
  imports: [
    CommonModule
  ]
})
export class ActionComponent implements DoCheck, OnInit {
  /**
   * The action config containing component properties
   */
  @Input() config?: ActionConfig;

  /**
   * Action template for custom actions
   */
  @Input() template!: TemplateRef<any>;

  /**
   * The event emitted when an action has been selected
   */
  @Output('onActionSelect') onActionSelect = new EventEmitter();

  private defaultConfig = {
    moreActionsDisabled: false,
    moreActionsVisible: true
  } as ActionConfig;
  isMoreActionsDropup: boolean = false;
  private prevConfig!: ActionConfig;

  /**
   * The default constructor
   *
   * @param el The element reference for this component
   */
  constructor(private el: ElementRef) {
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
    // lodash has issues deep cloning templates -- best seen with list component
    this.prevConfig = clone(this.config);
  }

  // Private

  handleAction(action: Action): void {
    if (action && action.disabled !== true) {
      this.onActionSelect.emit(action);
    }
  }

  /**
   * Set flag indicating if kebab should be shown as a dropdown or dropup
   *
   * @param $event The MouseEvent triggering this function
   */
  initMoreActionsDropup($event: MouseEvent): void {
    window.requestAnimationFrame(() => {
      let kebabContainer = this.closest($event.target, '.dropdown-kebab-pf.open', 'pfng-list-actions');
      let listContainer = this.closest(this.el.nativeElement, '.list-pf', 'pfng-list');
      if (kebabContainer === null || listContainer === null) {
        return;
      }

      let dropdownButton = kebabContainer.querySelector('.dropdown-toggle');
      let dropdownMenu = kebabContainer.querySelector('.dropdown-menu');
      if (dropdownButton === null || dropdownMenu === null) {
        return;
      }
      let buttonRect = dropdownButton.getBoundingClientRect();
      let menuRect = dropdownMenu.getBoundingClientRect();
      let menuTop = buttonRect.top - menuRect.height;
      let menuBottom = buttonRect.top + buttonRect.height + menuRect.height;
      let parentRect = listContainer.getBoundingClientRect();

      if ((menuBottom <= parentRect.top + parentRect.height) || (menuTop < parentRect.top)) {
        this.isMoreActionsDropup = false;
      } else {
        this.isMoreActionsDropup = true;
      }
    });
  }

  // Utils

  /**
   * Get the closest ancestor based on given selector
   *
   * @param el The HTML element to start searching for matching ancestor
   * @param selector The selector to match
   * @param stopSelector If this selector is matched, the search is stopped
   * @returns {HTMLElement} The matching HTML element or null if not found
   */
  private closest(el: any, selector: string, stopSelector: string): HTMLElement {
    let retval = null;
    while (el) {
      if (el.matches(selector)) {
        retval = el;
        break;
      } else if (stopSelector && el.matches(stopSelector)) {
        break;
      }
      el = el.parentElement;
    }
    return retval;
  }
}
