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
import { Router } from "@angular/router";

import * as d3 from 'd3';

@Component({
  selector: 'score-treemap',
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
  @Input('data')
  data: d3.layout.treemap.Node;

  @Input('scoreAttr')
  scoreAttr: string;

  @Input('block')
  block: string;

  @Input('elements')
  elements: string;

  @Input('legend')
  legend: string;

  margin = {top: 5, right: 5, bottom: 5, left: 5};
  width: number;
  height: number;

  treemap: d3.layout.Treemap<d3.layout.treemap.Node>;
  tooltip: d3.Selection<any>;

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

    var svg = d3.select('#scoreTreemap')
      .append('svg')
        .attr('width', this.width + this.margin.left + this.margin.right)
        .attr('height', this.height + this.margin.top + this.margin.bottom)
      .append('g')
        .attr('transform', "translate(" + this.margin.left + "," + this.margin.top + ")");
    
    this.treemap = d3.layout.treemap().round(false)
      .size([this.width, this.height]).value(function(d) { return d.value; });

    var nodes: d3.layout.treemap.Node[] = this.treemap.nodes(this.data)
      .filter(function(d) { return !d.children; })
      .sort((a, b) => b[this.scoreAttr] - a[this.scoreAttr]);

    var cell = svg.selectAll("g")
      .data(nodes)
      .enter().append('g')
        .attr('class', 'cell')
        .attr('transform', function(d) { return "translate(" + d.x + "," + d.y + ")"; });

    this.initializeTreemap(cell);
  }

  private initializeTreemap(cell: d3.Selection<any>): void {
    var tooltip = this.tooltip;
    var scoreAttr = this.scoreAttr;
    var block = this.block;
    var elements = this.elements;

    cell.append('rect')
      .attr('id', function(d) { return d['name']; })
      .attr('width', function(d) { return d.dx - 1; })
      .attr('height', function(d) { return d.dy - 1; })
      .on('mouseover', function(d) {
        tooltip.text(d['value'] + ' ' + elements + ' - ' + d[scoreAttr] + " %"); 
        cell.selectAll('rect')
          .filter(d => d['name'] !== this.id)
          .classed('rect-defocused', true);
        return tooltip.style('visibility', 'visible');
      })
      .on('mousemove', function() { 
        return tooltip
          .style('top', ((<any>d3.event).pageY - 10) + 'px')
          .style('left', ((<any>d3.event).pageX + 10) + 'px'); 
      })
      .on('mouseout', function() { 
        cell.selectAll('rect')
          .filter(d => d['name'] !== this.id)
          .classed('rect-defocused', false);
        return tooltip.style('visibility', 'hidden'); 
      })
      .style('fill', d => this.colorGradient(1 - (d[scoreAttr] / 100)) );

    cell.append('text')
      .attr('x', function(d) { return d.dx / 2; })
      .attr('y', function(d) { return d.dy / 2; })
      .attr('dy', '.35em')
      .attr('text-anchor', 'middle')
      .attr('fill', d => d[scoreAttr] < 50 ? 'white':'black')
      .text(d => block + ':' + d['name'] );

    d3.select(window).on('resize', () => 
      this.resizeTreemap()
    );
  }

  private resizeTreemap(): void {
    this.width = parseInt(d3.select('#scoreTreemap').style('width')) - this.margin.left - this.margin.right;
    this.height = this.width / 3;

    var svg = d3.select('#scoreTreemap').select('svg')
      .attr('width', this.width + this.margin.left + this.margin.right)
      .attr('height', this.height + this.margin.top + this.margin.bottom);

    this.treemap.size([this.width, this.height]);

    var nodes: d3.layout.treemap.Node[] = this.treemap.nodes(this.data)
      .filter(function(d) {return !d.children; })
      .sort((a, b) => b[this.scoreAttr] - a[this.scoreAttr]);

    // Reinitialize graphics in svg.
    var cell = svg.selectAll("g");
    cell.remove();

    svg = svg.append('g')
      .attr('transform', "translate(" + this.margin.left + "," + this.margin.top + ")");

    cell = svg.selectAll("g")
      .data(nodes)
      .enter().append('g')
        .attr('class', 'cell')
        .attr('transform', function(d) { return "translate(" + d.x + "," + d.y + ")"; });

    this.initializeTreemap(cell);
  }

  private colorGradient(fadeFraction: number): string {
    /*
    let rgbColor1 = {red: 63, green: 156, blue: 53};
    let rgbColor2 = {red: 239, green: 170, blue: 0};
    let rgbColor3 = {red: 204, green: 0, blue: 0};
    //background-image: linear-gradient(to left, #3f9c35, #efaa00, #cc0000);
    */
    let rgbColor1 = {red: 222, green: 243, blue: 255};    
    let rgbColor2 = {red: 57, green: 165, blue: 220};
    let rgbColor3 = {red: 0, green: 67, blue: 104};
    //background-image: linear-gradient(to left, #def3ff, #39a5dc, #004368);
    var color1 = rgbColor1;
    var color2 = rgbColor2;
    var fade = fadeFraction * 2;
    if (fade >= 1) {
      fade -= 1;
      color1 = rgbColor2;
      color2 = rgbColor3;
    }
    var diffRed  = color2.red - color1.red;
    var diffGreen  = color2.green - color1.green;
    var diffBlue  = color2.blue - color1.blue;
    var gradient = {
      red: parseInt(Math.floor(color1.red + (diffRed * fade)).toString(), 10),
      green: parseInt(Math.floor(color1.green + (diffGreen * fade)).toString(), 10),
      blue: parseInt(Math.floor(color1.blue + (diffBlue * fade)).toString(), 10),
    };
    return 'rgb(' + gradient.red + ',' + gradient.green + ',' + gradient.blue + ')';
  }
}