import {
  Component,
  ViewEncapsulation
} from '@angular/core';

import { ChartDefaults } from '../../chart-defaults';
import { DonutChartBaseComponent } from '../donut-chart-base.component';
import { WindowReference } from '../../../utilities/window.reference';

/**
 * Donut chart component.
 *
 * Note: In order to use charts, please include the following JavaScript file from PatternFly.
 * <code><pre>
 * require('patternfly/dist/js/patternfly-settings');
 * </pre></code>
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { DonutChartModule } from 'patternfly-ng/chart';
 * // Or
 * import { DonutChartModule } from 'patternfly-ng';
 *
 * &#64;NgModule({
 *   imports: [DonutChartModule,...]
 * })
 * export class AppModule(){}
 * </pre></code>
 *
 * Optional:
 * <code><pre>
 * import { DonutChartConfig } from 'patternfly-ng/chart';
 * </pre></code>
 */
@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'pfng-donut-chart',
  templateUrl: './donut-chart.component.html'
})
export class DonutChartComponent extends DonutChartBaseComponent {
  /**
   * Default constructor
   */
  constructor(protected override chartDefaults: ChartDefaults, protected override windowRef: WindowReference) {
    super(chartDefaults, windowRef);
  }
}
