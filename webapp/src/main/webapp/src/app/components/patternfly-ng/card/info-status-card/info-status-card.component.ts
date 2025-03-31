import {
  Component,
  DoCheck,
  Input,
  OnInit,
  ViewEncapsulation
} from '@angular/core';
import { CommonModule } from '@angular/common';

import { cloneDeep, defaults, isEqual } from 'lodash-es';

import { InfoStatusCardConfig } from './info-status-card-config';

/**
 * Info Status Card Component
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { InfoStatusCardModule } from 'patternfly-ng/card';
 * // Or
 * import { InfoStatusCardModule } from 'patternfly-ng';
 *
 * &#64;NgModule({
 *   imports: [InfoStatusCardModule,...]
 * })
 * export class AppModule(){}
 * </pre></code>
 *
 * Optional:
 * <code><pre>
 * import { InfoStatusCardConfig } from 'patternfly-ng/card';
 * </pre></code>
 */
@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'pfng-info-status-card',
  templateUrl: './info-status-card.component.html',
  imports: [
    CommonModule
  ]
})
export class InfoStatusCardComponent implements OnInit, DoCheck {

  /**
   * The config object containing component properties
   */
  @Input('config') config!: InfoStatusCardConfig;

  private defaultConfig: InfoStatusCardConfig = {
    showTopBorder: false,
    htmlContent: true
  };

  private prevConfig!: InfoStatusCardConfig;

  /**
   * The default constructor
   */
  constructor() {}

  /**
   * Setup component configuration upon initialization
   */
  ngOnInit(): void {
    this.setupConfig();
  }

  /**
   * Check if any component config props have changed
   */
  ngDoCheck(): void {
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

}
