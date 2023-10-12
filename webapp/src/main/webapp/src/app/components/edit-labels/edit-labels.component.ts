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
import { Component, EventEmitter, OnInit, Input, Output } from '@angular/core';

@Component({
  selector: 'edit-labels',
  templateUrl: './edit-labels.component.html',
  styleUrls: ['./edit-labels.component.css']
})
export class EditLabelsComponent implements OnInit {
  @Output() onSave = new EventEmitter<Map<string, string>>();

  @Input('resourceType') resourceType: string;
  @Input('resourceName') resourceName: string;
  @Input('labels') labels: any;

  labelKV: string = "";

  ngOnInit() {
    if (this.labels == null) {
      this.labels = new Map();
    }
  }

  getKeys(map){
    return Object.keys(map);
  }

  removeLabel(labelK: string): void {
    delete this.labels[labelK];
    this.labelKV = "";
  }

  addLabel(): void {
    if (this.labelKV.indexOf('=') != -1) {
      var kv: string[] = this.labelKV.split('=');
      if (kv.length == 2) {
        this.labels[kv[0]] = kv[1];
      }
    }
    this.labelKV = "";
  }

  saveLabels(): void {
    //console.log("[EditLabelsComponent saveLabels] with " + JSON.stringify(this.labels)");
    this.onSave.emit(this.labels);
  }
}