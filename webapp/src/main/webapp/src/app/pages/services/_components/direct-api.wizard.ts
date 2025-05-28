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
import { Component, OnInit, viewChild, ViewEncapsulation, Output, EventEmitter } from '@angular/core';
import {
  FormsModule,
  ReactiveFormsModule,
} from '@angular/forms';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';

import { BsModalRef } from 'ngx-bootstrap/modal';

import { Api, ServiceType } from '../../../models/service.model';
import { ServicesPageComponent } from '../services.page';

const API_TYPE = {
  GENERIC_EVENT: 'GENERIC_EVENT',
  GENERIC_REST: 'GENERIC_REST',
} as const;

@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'app-direct-api-wizard',
  templateUrl: './direct-api.wizard.html',
  styleUrls: ['./direct-api.wizard.css'],
  imports: [FormsModule, MatStepperModule, ReactiveFormsModule]
})
export class DirectAPIWizardComponent implements OnInit {
  private stepper = viewChild<MatStepper>('stepper');

  @Output() saveDirectAPIAction = new EventEmitter<Api>();

  API_TYPE = API_TYPE;
  selectedApiType: keyof typeof API_TYPE | undefined;

  formInvalid = true;
  api: Api = {} as Api;
  apiType: ServiceType | undefined;

  wizardHost: ServicesPageComponent | undefined;
 
  constructor(public bsModalRef: BsModalRef) {
  }
 
  ngOnInit() {
  }

  changeApiType(value: keyof typeof API_TYPE) {
    this.selectedApiType = value;
    this.apiType = value === 'GENERIC_EVENT' ? ServiceType.GENERIC_EVENT : ServiceType.GENERIC_REST;
    this.stepper && this.stepper()?.next();
  }
  updateApiProperties(): void {
    this.formInvalid = false;
    if (this.api.name == null || this.api.version == null || this.api.resource == null) {
      this.formInvalid = true;
    }
  }
  updateApiReference(): void {
    this.formInvalid = false;
    if (this.api.referencePayload != null && this.api.referencePayload.trim() != '') {
      try {
        JSON.parse(this.api.referencePayload);
      } catch (e) {
        this.formInvalid = true;
      }
    }
    if (this.apiType === ServiceType.GENERIC_EVENT &&
        ((this.api.referencePayload == null || this.api.referencePayload.trim() === ''))) {
      this.formInvalid = true;
    }
  }

  save($event: any) {
    /*
    if (this.wizardHost) {
      this.wizardHost.createDirectAPI(this.apiType || ServiceType.GENERIC_REST, this.api);
      this.wizardHost.closeDirectAPIWizardModal($event);
    }
    */
    this.api.type = this.apiType || ServiceType.GENERIC_REST;
    this.saveDirectAPIAction.emit(this.api);
    this.close();
  }

  close(): void {
    this.bsModalRef.hide()
  }

  isNextDisabled(): boolean {
    return this.formInvalid;
  }
  next($event: any): void {
    if (this.stepper && this.stepper()?.selectedIndex) {
      if (this.stepper()?.selectedIndex === 3) {
        this.save($event);
      }
    }
    this.stepper && this.stepper()?.next();
  }

  isPreviousDisabled(): boolean {
    if (this.stepper && this.stepper()?.selectedIndex) {
      return this.stepper()?.selectedIndex === 0;
    }
    return true;
  }
  previous(): void {
    this.stepper && this.stepper()?.previous();
  }
}
