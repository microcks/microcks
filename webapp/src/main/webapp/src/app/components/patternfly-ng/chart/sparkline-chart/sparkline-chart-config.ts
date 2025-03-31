import { ChartConfigBase } from '../chart-config-base';

/**
 * A config containing properties for the sparkline chart
 */
export class SparklineChartConfig extends ChartConfigBase {
  /**
   * C3 inherited configuration for axis
   */
  axis?: any;

  /**
   * The height of the chart
   */
  chartHeight?: number;

  /**
   * Boolean to indicate whether or not to show the x axis
   */
  showXAxis?: boolean;

  /**
   * Boolean to indicate whether or not to show the y axis
   */
  showYAxis?: boolean;

  /**
   * C3 inherited configuration for size object
   *
   * See: http://c3js.org/reference.html#size
   */
  size?: any;

  /**
   * C3 inherited configuration for tooltip
   *
   * See: http://c3js.org/reference.html#tooltip
   */
  tooltip?: any;

  /**
   * Options include usagePerDay, valuePerDay, percentage or default
   */
  tooltipType?: string;

  /**
   * The unit of measure for the chart
   */
  units?: string;
}
