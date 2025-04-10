import {
  Component,
  DoCheck,
  Input,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { Subscription } from 'rxjs';

import { cloneDeep, defaultsDeep, isEqual, merge, uniqueId } from 'lodash-es';

import * as d3 from 'd3';

import { ChartDefaults } from '../chart-defaults';
import { ChartBase } from '../chart-base';
import { DonutChartBaseConfig } from './donut-chart-base-config';
import { WindowReference } from '../../utilities/window.reference';

/**
 * Donut base
 */
@Component({
  selector: 'pfng-donut-chart-base',
  template: ''
})
export abstract class DonutChartBaseComponent extends ChartBase implements DoCheck, OnDestroy, OnInit {
  /**
   * An array containing key value pairs:
   *
   * key - string representing an arc within the donut chart
   * value - number representing the value of the arc
   */
  @Input() chartData!: any[];

  /**
   * Configuration object containing details about how to render the chart
   */
  @Input() config!: DonutChartBaseConfig;

  private defaultConfig!: DonutChartBaseConfig;
  private prevChartData!: any[];
  private prevConfig!: DonutChartBaseConfig;
  private subscriptions: Subscription[] = [];

  /**
   * Default constructor
   * @param chartDefaults
   */
  constructor(protected chartDefaults: ChartDefaults, protected windowRef: WindowReference) {
    super();
    this.subscriptions.push(this.chartLoaded.subscribe({
      next: (chart: any) => {
        this.chartAvailable(chart);
      }
    }));
  }

  /**
   * Setup component configuration upon initialization
   */
  ngOnInit(): void {
    this.setupConfigDefaults();
    this.setupConfig();
    this.generateChart(this.config);
  }

  /**
   * Check if the component config has changed
   */
  ngDoCheck(): void {
    const dataChanged = !isEqual(this.chartData, this.prevChartData);
    if (dataChanged || !isEqual(this.config, this.prevConfig)) {
      this.setupConfig();
      this.generateChart(this.config, !dataChanged);
    }
  }

  /**
   * Clean up subscriptions
   */
  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe);
  }

  /**
   * Set up default config
   */
  protected setupConfig(): void {
    if (this.config !== undefined) {
      defaultsDeep(this.config, this.defaultConfig);
    } else {
      this.config = cloneDeep(this.defaultConfig);
    }

    if (this.config.chartHeight !== undefined) {
      this.config.size.height = this.config.chartHeight;
    }
    this.config.data = merge(this.config.data, this.getChartData());
    this.prevConfig = cloneDeep(this.config);
    this.prevChartData = cloneDeep(this.chartData);
  }

  /**
   * Set up default config
   */
  protected setupConfigDefaults(): void {
    this.defaultConfig = this.chartDefaults.getDefaultDonutConfig();
    this.defaultConfig.chartId = uniqueId();
    this.defaultConfig.data = {
      type: 'donut',
      order: null
    };
    this.defaultConfig.donut = this.chartDefaults.getDefaultDonut();
    this.defaultConfig.tooltip = { contents: (this.windowRef.nativeWindow).patternfly.pfDonutTooltipContents };
  }

  /**
   * Convert chartData to C3 data property
   */
  protected getChartData(): any {
    return {
      columns: this.chartData,
      colors: this.config.colors
    };
  }

  /**
   * Returns an object containing center label properties
   * @returns {any}
   */
  getCenterLabelText(): any {
    // Public for testing
    let centerLabelText = {
      title: this.getTotal(),
      subTitle: this.config.donut.title
    };
    if (this.config.centerLabel) {
      centerLabelText.title = this.config.centerLabel;
      centerLabelText.subTitle = '';
    }
    return centerLabelText;
  }

  // Private

  private chartAvailable(chart: any): void {
    this.setupDonutChartTitle(chart);
  }

  private getTotal(): number {
    let total = 0;
    if (this.config.data !== undefined && this.config.data.columns !== undefined) {
      this.config.data.columns.forEach((element: any) => {
        if (!isNaN(element[1])) {
          total += Number(element[1]);
        }
      });
    }
    return total;
  }

  private setupDonutChartTitle(chart: any): void {
    let donutChartTitle, centerLabelText;

    if (chart === undefined) {
      return;
    }

    donutChartTitle = d3.select(chart.element).select('text.c3-chart-arcs-title');
    if (donutChartTitle === undefined) {
      return;
    }

    centerLabelText = this.getCenterLabelText();

    donutChartTitle.text('');
    if (centerLabelText.title && !centerLabelText.subTitle) {
      donutChartTitle.text(centerLabelText.title);
    } else {
      donutChartTitle.insert('tspan').text(centerLabelText.title)
        .classed('donut-title-big-pf', true).attr('dy', 0).attr('x', 0);
      donutChartTitle.insert('tspan').text(centerLabelText.subTitle).
        classed('donut-title-small-pf', true).attr('dy', 20).attr('x', 0);
    }
  }
}
