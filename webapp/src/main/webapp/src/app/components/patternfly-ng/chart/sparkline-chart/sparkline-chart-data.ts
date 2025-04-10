/**
 * A base config containing properties for chart data
 */
export abstract class SparklineChartData {
  /**
   * True is data is available
   */
  dataAvailable?: boolean;

  /**
   * The Total amount, used when determining percentages
   */
  total?: number;

  /**
   * X values for the data points, first element must be the name of the data
   */
  xData?: any[];

  /**
   * Y Values for the data points, first element must be the name of the data
   */
  yData?: any[];
}
