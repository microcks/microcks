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
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

import { BsModalRef } from 'ngx-bootstrap/modal';

import type { ServiceRef } from '../../models/importer.model';

@Component({
  selector: 'app-servicerefs-dialog',
  template: `
    <div class="modal-header">
      <h4 class="modal-title pull-left">Services</h4>
      <button type="button" class="close pull-right" aria-label="Close" (click)="bsModalRef.hide()">
        <span aria-hidden="true">&times;</span>
      </button>
    </div>
    <div class="modal-body">
      <ul *ngIf="serviceRefs.length">
        <li *ngFor="let serviceRef of serviceRefs">
        <a routerLink="['/services', serviceRef.serviceId]">{{ serviceRef.name }} - {{ serviceRef.version }}</a>
        </li>
      </ul>
    </div>
    <div class="modal-footer">
      <button type="button" class="btn btn-default" (click)="bsModalRef.hide()">{{closeBtnName}}</button>
    </div>
  `,
  imports: [
    CommonModule
  ],
})
export class ServiceRefsDialogComponent implements OnInit {
  title?: string;
  closeBtnName?: string;
  serviceRefs: ServiceRef[] = [];

  constructor(public bsModalRef: BsModalRef) {}

  ngOnInit() {
  }
}
