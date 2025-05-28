import {
  Component,
  DoCheck,
  ElementRef,
  EventEmitter,
  Input,
  OnInit,
  Output,
  TemplateRef,
  TrackByFunction,
  ViewEncapsulation
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { cloneDeep, defaults, isEqual, uniqueId } from 'lodash-es';

import { ListBase } from '../list-base';
import { ListConfig } from './list-config';
import { ListEvent } from '../list-event';
import { EmptyStateModule } from '../../empty-state';
import { SortArrayPipe } from '../../pipe';

/**
 * List component
 *
 * For items, use a template named itemTemplate to contain content for each item. For each item in the items array, the
 * expansion can be disabled by setting disabled to true on the item. If using actions, use a template named
 * actionTemplate to contain expandable content for the actions of each item. If using expand items, use a template
 * named itemExpandedTemplate to contain expandable content for each item.
 *
 * Cannot use both multi-select and double click selection at the same time
 * Cannot use both checkbox and click selection at the same time
 *
 * Unique IDs are generated for each list item, which can be overridden by providing an id for the pfng-list tag.
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { ListModule } from 'patternfly-ng/list';
 * // Or
 * import { ListModule } from 'patternfly-ng';
 *
 * // NGX Bootstrap
 * import { BsDropdownConfig, BsDropdownModule } from 'ngx-bootstrap/dropdown';
 * import { TooltipConfig, TooltipModule } from 'ngx-bootstrap/tooltip';
 *
 * &#64;NgModule({
 *   imports: [ListModule, BsDropdownModule.forRoot(), TooltipModule.forRoot(),...],
 *   providers: [BsDropdownConfig, TooltipConfig]
 * })
 * export class AppModule(){}
 * </pre></code>
 *
 * Optional:
 * <code><pre>
 * import { ListConfig, ListEvent } from 'patternfly-ng/list';
 * </pre></code>
 */
@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'pfng-list',
  templateUrl: './list.component.html',
  imports: [
    CommonModule,
    EmptyStateModule,
    FormsModule,
    SortArrayPipe,
  ]
})
export class ListComponent extends ListBase implements DoCheck, OnInit {
  /**
   * The name of the template containing action heading layout
   */
  @Input() actionHeadingTemplate?: TemplateRef<any>;

  /**
   * The list config containing component properties
   */
  @Input() config!: ListConfig;

  /**
   * The name of the template used to contain expandable content for each item
   */
  @Input() expandTemplate!: TemplateRef<any>;

  /**
   * The name of the template containing item heading layout
   */
  @Input() itemHeadingTemplate?: TemplateRef<any>;

  /**
   * The function to pass to the underlying ngFor trackBy property
   */
  @Input() trackBy!: TrackByFunction<any>;

  /**
   * The event emitted when an item pin has been changed
   */
  @Output('onPinChange') onPinChange = new EventEmitter();

  private defaultConfig = {
    dblClick: false,
    hideClose: false,
    multiSelect: false,
    selectedItems: [],
    selectionMatchProp: 'uuid',
    selectItems: false,
    showCheckbox: false,
    showRadioButton: false,
    useExpandItems: false
  } as ListConfig;
  private id: string = uniqueId('pfng-list');
  private prevConfig!: ListConfig;

  /**
   * The default constructor
   */
  constructor(private el: ElementRef) {
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
  protected override setupConfig(): void {
    if (this.config !== undefined) {
      defaults(this.config, this.defaultConfig);
    } else {
      this.config = cloneDeep(this.defaultConfig);
    }
    super.setupConfig();
    this.prevConfig = cloneDeep(this.config);
  }

  /**
   * Return component config
   *
   * @returns {} ListConfig The component config
   */
  protected getConfig(): ListConfig {
    return this.config;
  }

  /**
   * Return an ID for the given element prefix and index (e.g., 'pfng-list1-item0')
   *
   * Note: The ID prefix can be overridden by providing an id for the pfng-list tag.
   *
   * @param {string} suffix The element suffix (e.g., 'item')
   * @param {number} index The current item index
   * @returns {string}
   */
  protected getId(suffix: string, index: number): string {
    let result = this.id;
    if (this.el.nativeElement.id !== undefined && this.el.nativeElement.id.length > 0) {
      result = this.el.nativeElement.id;
    }
    return result + '-' + suffix + index;
  }

  // Toggle

  protected closeExpandItem(item: any): void {
    item.expandId = undefined;
    item.expanded = false;
  }

  /**
   * Toggle expand item open/close
   *
   * @param {MouseEvent} $event The event emitted when an item has been clicked
   * @param {Object} item The object associated with the current row
   */
  protected toggleExpandItem($event: MouseEvent, item: any): void {
    // Do nothing if item expansion is disabled
    if (!this.config.useExpandItems) {
      return;
    }
    // Do not trigger for child items, only on the DOM element to which the event is attached
    if ($event.target !== $event.currentTarget) {
      return;
    }
    // Item may already be open due to compound expansion
    if (item.expanded && item.expandId !== undefined) {
      item.expandId = undefined;
      return;
    }
    item.expandId = undefined;
    item.expanded = !item.expanded;
  }

  protected togglePin($event: MouseEvent, item: any): void {
    item.showPin = (item.showPin === undefined) ? true : !item.showPin;
    this.onPinChange.emit({
      item: item
    } as ListEvent);
  }
}
