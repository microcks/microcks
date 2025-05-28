import { Component, EventEmitter, Output } from '@angular/core';

import { cloneDeep } from 'lodash-es';
import * as c3 from 'c3';

import { ChartConfigBase } from './chart-config-base';

/**
 * Chart base
 *
 * Note: In order to use charts, please include the following JavaScript file from patternfly. For example:
 * <code>require('patternfly/dist/js/patternfly-settings');</code>
 */
@Component({
  selector: 'pfng-chart-base',
  template: ''
})
export abstract class ChartBase {
  /**
   * Event emitted with the chart reference after load is complete
   * @type {EventEmitter}
   */
  @Output() chartLoaded: EventEmitter<any> = new EventEmitter();

  // Store the chart object
  private chart: any;

  /**
   * Default constructor
   */
  constructor() {}

  /**
   * Protected method called when configuration or data changes by any class that inherits from this
   *
   * @param config The config for the c3 chart
   * @param reload True to reload
   */
  protected generateChart(config: ChartConfigBase, reload?: boolean): void {
    setTimeout(() => {
      let c3Config: any = cloneDeep(config);
      c3Config.bindto = '#' + config.chartId;

      // Note: Always re-generate donut pct chart because it's colors change based on data and thresholds
      if (this.chart === undefined || reload === true) {
        this.chart = c3.generate(c3Config);
      } else {
        // if chart is already created, then we only need to re-load data
        this.chart.load(c3Config.data);
      }
      this.chartLoaded.emit(this.chart);
    });
  }
}
