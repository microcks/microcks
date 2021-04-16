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

import { Response } from '../../models/service.model';

@Component({
  selector: 'edit-response-dialog',
  templateUrl: './edit-response-dialog.component.html',
  styleUrls: ['./edit-response-dialog.component.css']
})
export class EditResponseDialogComponent implements OnInit {
  @Output() saveResponseAction = new EventEmitter<Response>();

  response: Response;
  closeBtnName: string;

  constructor(public bsModalRef: BsModalRef) {}
 
  ngOnInit() {
  }

  saveResponse(): void {
    console.log("[EditResponseDialogComponent saveResponse]");
    this.saveResponseAction.emit(this.response);
    this.bsModalRef.hide();
  }
}