/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { Component, OnInit, Input } from '@angular/core';
import { Router } from '@angular/router';
import * as d3 from 'd3';
import { HierarchyRectangularNode } from 'd3-hierarchy';

const phi: number = (1 + Math.sqrt(5)) / 2;

@Component({
  selector: 'app-score-treemap',
  styleUrls: ['./score-treemap.component.css'],
  template: `
    <div style="padding-left: 10px">
      <div class="row">
        <div class="col-md-6">
          {{ legend }}
        </div>
        <div class="col-md-3 col-md-offset-3 score-treemap-legend-container pull-right">
          0% <span class="score-treemap-legend"></span> 100%
        </div>
      </div>
    </div>
    <div id="scoreTreemap"></div>
  `
})
export class ScoreTreemapComponent implements OnInit {
  
  @Input()
  data!: HierarchyRectangularNode<any>;
  //data: d3.layout.treemap.Node;

  @Input()
  scoreAttr!: string;

  @Input()
  block!: string;

  @Input()
  elements!: string;

  @Input()
  legend!: string;

  margin = {top: 5, right: 5, bottom: 5, left: 5};
  width: number = 30;
  height: number = 10;
  

  treemap: d3.TreemapLayout<any> = {} as d3.TreemapLayout<any>;
  tooltip: d3.Selection<any, unknown, HTMLElement, any> = {} as d3.Selection<any, unknown, HTMLElement, any>;
  
  //treemap: d3.layout.Treemap<d3.layout.treemap.Node>;
  //tooltip: d3.Selection<any>;

  constructor(private router: Router) {
  }

  ngOnInit() {
    this.width = parseInt(d3.select('#scoreTreemap').style('width')) - this.margin.left - this.margin.right,
    this.height = this.width / 3;

    this.tooltip = d3.select('body')
      .append('div')
        .style('position', 'absolute')
        .style('z-index', '10')
        .style('visibility', 'hidden')
        .style('padding', '0 6px')
        .style('color', '#fff')
        .style('background', '#292e34');

    const svg = d3.select('#scoreTreemap')
      .append('svg')
        .attr('width', this.width + this.margin.left + this.margin.right)
        .attr('height', this.height + this.margin.top + this.margin.bottom)
      .append('g')
        .attr('transform', 'translate(' + this.margin.left + ',' + this.margin.top + ')');

    this.treemap = d3.treemap()
      .round(false)
      .size([this.width, this.height])
      .padding(1)
      //.tile(d3.treemapBinary);
      //.tile(d3.treemapSquarify);
      //.tile(d3.treemapResquarify.ratio(phi));

    const nodes: HierarchyRectangularNode<any>[] = this.treemap(
      d3.hierarchy(this.data)
        .sum((d) => (d.value || 0))
        .sort((a, b) => (b.data as any)[this.scoreAttr] - (a.data as any)[this.scoreAttr])
    ).leaves();

    /*
    for (let i = 0; i < nodes.length; i++) {
      console.log("nodes[" + i + "]: data.name: " + nodes[i].data.name + ", data.value: " + nodes[i].data.value + ", data.score: " + (nodes[i].data as any).score);
      console.log("  x0: " + nodes[i].x0 + ", y0: " + nodes[i].y0 + ", x1: " + nodes[i].x1 + ", y1: " + nodes[i].y1);
    }
    */

    const cell = svg.selectAll('g')
      .data(nodes)
      .enter().append('g')
        .attr('class', 'cell')
        .attr('transform', (d) => 'translate(' + d.x0 + ',' + d.y0 + ')');

    this.initializeTreemap(cell);
  }

  private initializeTreemap(cell: d3.Selection<SVGGElement, HierarchyRectangularNode<any>, SVGGElement, unknown>): void {
    const tooltip = this.tooltip;
    const scoreAttr = this.scoreAttr;
    const block = this.block;
    const elements = this.elements;

    cell.append('rect')
      .attr('id', (d) => d.data.name)
      .attr('width', (d) => {
        return d.x1 - d.x0
      })
      .attr('height', (d) => d.y1 - d.y0)
      .on('mouseover', (event, d) => {
        tooltip.text(d.value + ' ' + elements + ' - ' + (d.data as any)[scoreAttr] + ' %');
        cell.selectAll('rect')
          .filter(function(item) { 
            return (item as any).data.name !== d.data.name; 
          })
          .classed('rect-defocused', true);
        return tooltip.style('visibility', 'visible');
      })
      .on('mousemove', (event) => tooltip
        .style('top', (event.pageY - 10) + 'px')
        .style('left', (event.pageX + 10) + 'px')
      )
      .on('mouseout', () => {
        cell.selectAll('rect')
          .filter(function(d) { 
            return true;
          })
          .classed('rect-defocused', false);
        return tooltip.style('visibility', 'hidden');
      })
      .style('fill', d => this.colorGradient(1 - ((d.data as any)[scoreAttr] / 100)) );

    cell.append('text')
      .attr('x', (d) => (d.x1 - d.x0) / 2)
      .attr('y', (d) => (d.y1 - d.y0) / 2)
      .attr('dy', '.35em')
      .attr('text-anchor', 'middle')
      .attr('fill', d => (d.data as any)[scoreAttr] < 50 ? 'white' : 'black')
      .text(d => block + ':' + d.data.name );

    d3.select(window).on('resize', () =>
      this.resizeTreemap()
    );
  }

  private resizeTreemap(): void {
    this.width = parseInt(d3.select('#scoreTreemap').style('width')) - this.margin.left - this.margin.right;
    this.height = this.width / 3;

    let svg = d3.select('#scoreTreemap').select('svg')
      .attr('width', this.width + this.margin.left + this.margin.right)
      .attr('height', this.height + this.margin.top + this.margin.bottom);

    this.treemap.round(false)
      .size([this.width, this.height])
      .padding(1)
      .tile(d3.treemapSquarify);

    const nodes: HierarchyRectangularNode<any>[] = this.treemap(
      d3.hierarchy(this.data)
        .sum((d) => (d.value || 0))
        .sort((a, b) => (b.data as any)[this.scoreAttr] - (a.data as any)[this.scoreAttr])
    ).leaves();

    // Reinitialize graphics in svg.
    let cell = svg.selectAll('g');
    cell.remove();

    let newSvg = svg.append('g')
      .attr('transform', 'translate(' + this.margin.left + ',' + this.margin.top + ')');

    let newCell = newSvg.selectAll('g')
      .data(nodes)
      .enter().append('g')
        .attr('class', 'cell')
        .attr('transform', (d) => 'translate(' + d.x0 + ',' + d.y0 + ')');

    this.initializeTreemap(newCell);
  }

  private colorGradient(fadeFraction: number): string {
    /*
    let rgbColor1 = {red: 63, green: 156, blue: 53};
    let rgbColor2 = {red: 239, green: 170, blue: 0};
    let rgbColor3 = {red: 204, green: 0, blue: 0};
    //background-image: linear-gradient(to left, #3f9c35, #efaa00, #cc0000);
    */
    const rgbColor1 = {red: 222, green: 243, blue: 255};
    const rgbColor2 = {red: 57, green: 165, blue: 220};
    const rgbColor3 = {red: 0, green: 67, blue: 104};
    // background-image: linear-gradient(to left, #def3ff, #39a5dc, #004368);
    let color1 = rgbColor1;
    let color2 = rgbColor2;
    let fade = fadeFraction * 2;
    if (fade >= 1) {
      fade -= 1;
      color1 = rgbColor2;
      color2 = rgbColor3;
    }
    const diffRed  = color2.red - color1.red;
    const diffGreen  = color2.green - color1.green;
    const diffBlue  = color2.blue - color1.blue;
    const gradient = {
      red: parseInt(Math.floor(color1.red + (diffRed * fade)).toString(), 10),
      green: parseInt(Math.floor(color1.green + (diffGreen * fade)).toString(), 10),
      blue: parseInt(Math.floor(color1.blue + (diffBlue * fade)).toString(), 10),
    };
    return 'rgb(' + gradient.red + ',' + gradient.green + ',' + gradient.blue + ')';
  }
}
