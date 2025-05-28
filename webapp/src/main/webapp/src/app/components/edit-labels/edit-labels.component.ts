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
import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnInit, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-edit-labels',
  templateUrl: './edit-labels.component.html',
  styleUrls: ['./edit-labels.component.css'],
  imports: [
    CommonModule,
    FormsModule
  ]
})
export class EditLabelsComponent implements OnInit {
  @Output() save = new EventEmitter<Map<string, string>>();

  @Input() resourceType?: string;
  @Input() resourceName!: string;
  @Input() labels!: any;

  labelKV = '';

  ngOnInit() {
    if (this.labels == null) {
      this.labels = new Map();
    }
  }

  getKeys(map: any): string[] {
    return Object.keys(map);
  }

  removeLabel(labelK: string): void {
    delete this.labels[labelK];
    this.labelKV = '';
  }

  addLabel(): void {
    if (this.labelKV.indexOf('=') != -1) {
      const kv: string[] = this.labelKV.split('=');
      if (kv.length == 2) {
        this.labels[kv[0]] = kv[1];
      }
    }
    this.labelKV = '';
  }

  saveLabels(): void {
    // console.log("[EditLabelsComponent saveLabels] with " + JSON.stringify(this.labels)");
    this.save.emit(this.labels);
  }
}
