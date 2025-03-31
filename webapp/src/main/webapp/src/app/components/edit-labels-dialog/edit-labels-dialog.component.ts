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
import { Component, EventEmitter, OnInit, Output } from '@angular/core';

import { BsModalRef } from 'ngx-bootstrap/modal';

import { EditLabelsComponent } from '../edit-labels/edit-labels.component';

@Component({
  selector: 'app-edit-labels-dialog',
  templateUrl: './edit-labels-dialog.component.html',
  styleUrls: ['./edit-labels-dialog.component.css'],
  imports: [
    EditLabelsComponent
  ]
})
export class EditLabelsDialogComponent implements OnInit {
  @Output() saveLabelsAction = new EventEmitter<Map<string, string>>();

  title?: string;
  resourceType!: string;
  resourceName!: string;
  labels!: Map<string, string>;
  closeBtnName!: string;

  labelKV = '';

  constructor(public bsModalRef: BsModalRef) {}

  ngOnInit() {
    if (this.labels == null) {
      this.labels = new Map<string, string>();
    }
  }

  saveLabels(): void {
    // console.log("[EditLabelsDialogComponent saveLabels] with " + JSON.stringify(this.labels));
    this.saveLabelsAction.emit(this.labels);
    this.bsModalRef.hide();
  }
}
