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

import { AboutModalConfig } from './about-modal-config';
import { AboutModalEvent } from './about-modal-event';

/**
 * About Modal component
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { AboutModalModule } from 'patternfly-ng/modal';
 * // Or
 * import { AboutModalModule } from 'patternfly-ng';
 *
 * // NGX Bootstrap
 * import { ModalModule } from 'ngx-bootstrap/modal';
 *
 * &#64;NgModule({
 *   imports: [AboutModalModule, ModalModule.forRoot(),...]
 * })
 * export class AppModule(){}
 * </pre></code>
 *
 * Optional:
 * <code><pre>
 * import { AboutModalConfig, AboutModalEvent } from 'patternfly-ng/modal';
 * </pre></code>
 */
@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'pfng-about-modal',
  templateUrl: './about-modal.component.html',
  imports: [
    CommonModule
  ]
})
export class AboutModalComponent implements DoCheck, OnInit {
  /**
   * The AboutModal config contaning component properties
   */
  @Input() config!: AboutModalConfig;

  /**
   * The Event is emitted when modal is closed
   */
  @Output('onCancel') onCancel = new EventEmitter();

  private defaultConfig = {} as AboutModalConfig;
  private prevConfig!: AboutModalConfig;

  /**
   * The default contructor
   */
  constructor() {}

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
   * Setup default config
   */
  protected setupConfig(): void {
    if (this.config !== undefined) {
      defaults(this.config, this.defaultConfig);
    } else {
      this.config = cloneDeep(this.defaultConfig);
    }
    this.prevConfig = cloneDeep(this.config);
  }

  /**
   * Close the Modal
   * @param  $event MouseEvent to emit
   */
  close(): void {
    this.onCancel.emit({
      close: true
    } as AboutModalEvent);
  }
}

