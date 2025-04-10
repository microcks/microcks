import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';

import { ChartDefaults } from '../../chart-defaults';

import { DonutChartComponent } from './donut-chart.component';
import { WindowReference } from '../../../utilities/window.reference';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    DonutChartComponent
  ],
  //declarations: [DonutChartComponent],
  exports: [DonutChartComponent],
  providers: [ChartDefaults, WindowReference]
})
export class DonutChartModule {}
