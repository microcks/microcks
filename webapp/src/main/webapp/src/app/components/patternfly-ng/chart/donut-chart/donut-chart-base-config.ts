import { ChartConfigBase } from '../chart-config-base';

/**
 * A config containing properties for the sparkline chart
 */
export class DonutChartBaseConfig extends ChartConfigBase {
  /**
   * Text for the donut chart center label (optional)
   */
  centerLabel?: any;

  /**
   * The height of the donut chart (optional)
   */
  chartHeight?: number;

  /**
   * C3 inherited configuration for colors
   *
   * colors : {
   *   Cats: '#0088ce',
   *   Hamsters: '#3f9c35',
   * }
   */
  colors?: any;

  /**
   * C3 inherited donut configuration
   */
  donut?: any;

  /**
   * C3 inherited legend configuration
   */
  legend?: any;

  /**
   * C3 inherited configuration for size
   */
  size?: any;

  /**
   * C3 inherited configuration for tooltip
   */
  tooltip?: any;
}
