import { Injectable } from '@angular/core';

@Injectable()
export class ChartDefaults {

  protected patternflyDefaults: any = (window as any).patternfly.c3ChartDefaults();

  public getDefaultColors = this.patternflyDefaults.getDefaultColors;
  public getDefaultDonut = this.patternflyDefaults.getDefaultDonut;
  public getDefaultDonutSize = this.patternflyDefaults.getDefaultDonutSize;
  public getDefaultDonutColors = this.patternflyDefaults.getDefaultDonutColors;
  public getDefaultRelationshipDonutColors = this.patternflyDefaults.getDefaultRelationshipDonutColors;
  public getDefaultDonutLegend = this.patternflyDefaults.getDefaultDonutLegend;
  public getDefaultDonutTooltip = this.patternflyDefaults.getDefaultDonutTooltip;
  public getDefaultDonutConfig = this.patternflyDefaults.getDefaultDonutConfig;
  public getDefaultSparklineArea = this.patternflyDefaults.getDefaultSparklineArea;
  public getDefaultSparklineSize = this.patternflyDefaults.getDefaultSparklineSize;
  public getDefaultSparklineAxis = this.patternflyDefaults.getDefaultSparklineAxis;
  public getDefaultSparklineColor = this.patternflyDefaults.getDefaultColors;
  public getDefaultSparklineLegend = this.patternflyDefaults.getDefaultSparklineLegend;
  public getDefaultSparklinePoint = this.patternflyDefaults.getDefaultSparklinePoint;
  public getDefaultSparklineTooltip = this.patternflyDefaults.getDefaultSparklineTooltip;
  public getDefaultSparklineConfig = this.patternflyDefaults.getDefaultSparklineConfig;
  public getDefaultLineConfig = this.patternflyDefaults.getDefaultLineConfig;

  constructor() {
  }
}

