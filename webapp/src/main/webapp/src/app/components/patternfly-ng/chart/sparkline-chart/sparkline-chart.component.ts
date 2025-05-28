import {
  Component,
  DoCheck,
  Input,
  OnInit,
  ViewEncapsulation
} from '@angular/core';

import { cloneDeep, defaultsDeep, isEqual, merge, uniqueId } from 'lodash-es';

import { ChartBase } from '../chart-base';
import { ChartDefaults } from '../chart-defaults';
import { SparklineChartConfig } from './sparkline-chart-config';
import { SparklineChartData } from './sparkline-chart-data';

/**
 * Sparkline chart component
 *
 * Note: In order to use charts, please include the following JavaScript file from PatternFly.
 * <code><pre>
 * require('patternfly/dist/js/patternfly-settings');
 * </pre></code>
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { SparklineChartModule } from 'patternfly-ng/chart';
 * // Or
 * import { SparklineChartModule } from 'patternfly-ng';
 *
 * &#64;NgModule({
 *   imports: [SparklineChartModule,...]
 * })
 * export class AppModule(){}
 * </pre></code>
 *
 * Optional:
 * <code><pre>
 * import { SparklineChartConfig, SparklineChartData } from 'patternfly-ng/chart';
 * </pre></code>
 */
@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'pfng-sparkline-chart',
  templateUrl: './sparkline-chart.component.html'
})
export class SparklineChartComponent extends ChartBase implements DoCheck, OnInit {
  /**
   * Chart data
   */
  @Input() chartData!: SparklineChartData;

  /**
   * Configuration object containing details about how to render the chart
   */
  @Input() config!: SparklineChartConfig;

  private defaultConfig!: SparklineChartConfig;
  private prevChartData!: SparklineChartData;
  private prevConfig!: SparklineChartConfig;

  /**
   * Default constructor
   * @param chartDefaults
   */
  constructor(protected chartDefaults: ChartDefaults) {
    super();
  }

  /**
   * Setup component configuration upon initialization
   */
  ngOnInit(): void {
    this.setupConfigDefaults();
    this.setupConfig();
    this.generateChart(this.config, true);
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
   * Set up default config
   */
  protected setupConfig(): void {
    if (this.config !== undefined) {
      defaultsDeep(this.config, this.defaultConfig);
    } else {
      this.config = cloneDeep(this.defaultConfig);
    }

    /*
     * Setup Axis options. Default is to not show either axis. This can be overridden in two ways:
     *   1) in the config, setting showAxis to true will show both axes
     *   2) in the attributes showXAxis and showYAxis will override the config if set
     *
     * By default only line and the tick marks are shown, no labels. This is a sparkline and should be used
     * only to show a brief idea of trending. This can be overridden by setting the config.axis options per C3
     */
    if (this.config.axis !== undefined) {
      this.config.axis.x.show = this.config.showXAxis === true;
      this.config.axis.y.show = this.config.showYAxis === true;
    }
    if (this.config.chartHeight !== undefined) {
      this.config.size.height = this.config.chartHeight;
    }
    this.config.data = merge(this.config.data, this.getChartData());
    this.prevConfig = cloneDeep(this.config);
    this.prevChartData = cloneDeep(this.chartData);
  }

  /**
   * Set up config defaults
   */
  protected setupConfigDefaults(): void {
    this.defaultConfig = this.chartDefaults.getDefaultSparklineConfig();

    this.defaultConfig.axis = {
      x: {
        show: this.config.showXAxis === true,
        type: 'timeseries',
        tick: {
          format: () => {
            return ''; // change to lambda ?
          }
        }
      },
      y: {
        show: this.config.showYAxis === true,
        tick: {
          format: () => {
            return ''; // change to lambda ?
          }
        }
      }
    };
    this.defaultConfig.chartId = uniqueId(this.config.chartId);
    this.defaultConfig.data = { type: 'area' };
    this.defaultConfig.tooltip = this.tooltip();
    this.defaultConfig.units = '';
  }

  // Chart

  /**
   * Convert chartData to C3 data property
   */
  protected getChartData(): any {
    let data: any = {};

    if (this.chartData && this.chartData.dataAvailable !== false && this.chartData.xData && this.chartData.yData) {
      data.x = this.chartData.xData[0];
      data.columns = [
        this.chartData.xData,
        this.chartData.yData
      ];
    }
    return data;
  }

  /**
   * Tooltip function for sparklines
   *
   * @returns {{contents: ((d:any)=>string), position: ((data:any, width:number,
   *            height:number, element:any)=>{top: number, left: number})}}
   */
  tooltip(): any {
    return {
      contents: (d: any) => {
        let tipRows;
        let percentUsed = 0;

        switch (this.config.tooltipType) {
          case 'usagePerDay':
            if (this.chartData && this.chartData.dataAvailable !== false && (this.chartData.total || 0) > 0) {
              percentUsed = Math.round(d[0].value / (this.chartData.total || 0) * 100.0);
            }
            tipRows =
              '<tr>' +
              '  <th colspan="2">' + d[0].x.toLocaleDateString() + '</th>' +
              '</tr>' +
              '<tr>' +
              '  <td class="name">' + percentUsed + '%:' + '</td>' +
              '  <td class="value text-nowrap">' + d[0].value + ' '
              + (this.config.units ? this.config.units + ' ' : '') + d[0].name + '</td>' +
              '</tr>';
            break;
          case 'valuePerDay':
            tipRows =
              '<tr>' +
              '  <td class="value">' + d[0].x.toLocaleDateString() + '</td>' +
              '  <td class="value text-nowrap">' + d[0].value + ' ' + d[0].name + '</td>' +
              '</tr>';
            break;
          case 'percentage':
            percentUsed = Math.round(d[0].value / (this.chartData.total || 0) * 100.0);
            tipRows =
              '<tr>' +
              '  <td class="name">' + percentUsed + '%' + '</td>' +
              '</tr>';
            break;
          default:
            tipRows = this.chartDefaults.getDefaultSparklineTooltip().contents(d);
        }
        return this.getTooltipTableHTML(tipRows);
      },
      position: (data: any, width: number, height: number, element: any) => {
        let center;
        let top;
        let chartBox;
        let graphOffsetX;
        let x;

        try {
          center = parseInt(element.getAttribute('x'), 10);
          top = parseInt(element.getAttribute('y'), 10);
          const chartElement = document.querySelector('#' + this.config.chartId);
          const axisYElement = document.querySelector('#' + this.config.chartId + ' g.c3-axis-y');
          
          if (chartElement && axisYElement) {
            chartBox = chartElement.getBoundingClientRect();
            graphOffsetX = axisYElement.getBoundingClientRect().right;

            x = Math.max(0, center + graphOffsetX - chartBox.left - Math.floor(width / 2));

            // As width is now always the same, we can't use it to calculate the position.
            // Math.min() below will always return 0 so we just put a fix padding....
            return {
              top: top - height + 10,
              left: Math.min(x, chartBox.width - width) + 10
            };
          }
          
          throw new Error('Error getting chart element');
        } catch (e) {
          console.log('Error getting tooltip position', e);
        }

        return { top: 0, left: 0 }
      }
    };
  }

  // Private

  private getTooltipTableHTML(tipRows: any): string {
    return '<div class="module-triangle-bottom">' +
      '  <table class="c3-tooltip">' +
      '    <tbody>' +
      tipRows +
      '    </tbody>' +
      '  </table>' +
      '</div>';
  }
}
