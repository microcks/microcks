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
import { Component, OnInit, ViewEncapsulation, ChangeDetectionStrategy, Input} from '@angular/core';
import { NgFor } from '@angular/common';

import { Metadata } from '../../../app/models/commons.model';


@Component({
  selector: 'app-label-list',
  encapsulation: ViewEncapsulation.None,
  templateUrl: './label-list.component.html',
  styleUrls: ['./label-list.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgFor]
})
export class LabelListComponent implements OnInit {

  @Input() metadata: Metadata | null = null;

  @Input() filter: string | null = null;

  private labels: any = null;

  check() {
    // Debug trace for diving into ChangeDetectionStrategy.OnPush issues...
    console.log('[LabelListComponent] View checked');
  }

  ngOnInit() {
    if (this.metadata) {
      if (this.filter) {
        this.labels = {};
        const filteredLabels = this.filter.split(',');
        filteredLabels.forEach(label => {
          if (this.metadata?.labels && this.metadata.labels[label]) {
            this.labels[label] = this.metadata.labels[label];
          }
        });
      } else {
        this.labels = this.metadata.labels;
      }
    }
  }

  getLabelsKeys(): string[] | null {
    if (this.metadata) {
      if (this.filter) {
        this.labels = {};
        const filteredLabels = this.filter.split(',');
        filteredLabels.forEach(label => {
          if (this.metadata?.labels && this.metadata.labels[label]) {
            this.labels[label] = this.metadata.labels[label];
          }
        });
      } else {
        this.labels = this.metadata.labels;
      }
    }
    if (this.labels == null) {
      return null;
    }
    return Object.keys(this.labels);
  }
  getLabelValue(label: string): string | null {
    if (this.labels == null) {
      return null;
    }
    return this.labels[label];
  }
}
