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
import { CommonModule } from '@angular/common';

import { TooltipModule } from 'ngx-bootstrap/tooltip';

@Component({
  selector: 'app-grade-index',
  templateUrl: './grade-index.component.html',
  styleUrls: ['./grade-index.component.css'],
  imports: [
    CommonModule,
    TooltipModule
  ]
})
export class GradeIndexComponent implements OnInit {

  @Input() score!: number;

  activeGrade!: string;

  ngOnInit() {
    if (this.score >= 80) {
      this.activeGrade = 'A';
    } else if (this.score >= 60 && this.score < 80) {
      this.activeGrade = 'B';
    } else if (this.score >= 40 && this.score < 60) {
      this.activeGrade = 'C';
    } else if (this.score >= 20 && this.score < 40) {
      this.activeGrade = 'D';
    } else {
      this.activeGrade = 'E';
    }
  }
}
