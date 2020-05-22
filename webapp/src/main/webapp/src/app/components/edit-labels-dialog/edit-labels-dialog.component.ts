/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import { Component, EventEmitter, OnInit, Output } from '@angular/core';

import { BsModalRef } from 'ngx-bootstrap/modal';

@Component({
  selector: 'edit-labels-dialog',
  templateUrl: './edit-labels-dialog.component.html',
  styleUrls: ['./edit-labels-dialog.component.css']
})
export class EditLabelsDialogComponent implements OnInit {
  @Output() saveLabelsAction = new EventEmitter<Map<string, string>>();

  title: string;
  resourceType: string;
  resourceName: string;
  labels: any;
  closeBtnName: string;

  labelKV: string = "";

  constructor(public bsModalRef: BsModalRef) {}
 
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
    console.log("[EditLabelsDialogComponent saveLabels]");
    this.saveLabelsAction.emit(this.labels);
    this.bsModalRef.hide();
  }
}