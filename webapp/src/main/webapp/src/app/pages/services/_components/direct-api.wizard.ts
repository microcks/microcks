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
import { Component, OnInit, ViewChild, ViewEncapsulation, Host } from '@angular/core';

import { WizardComponent, WizardConfig, WizardEvent, WizardStep, WizardStepComponent, WizardStepConfig } from 'patternfly-ng/wizard';

import { Api, Service, ServiceType } from '../../../models/service.model';
import { ServicesPageComponent } from '../services.page';

@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'direct-api-wizard',
  templateUrl: './direct-api.wizard.html',
  styleUrls: ['./direct-api.wizard.css']
})
export class DirectAPIWizardComponent implements OnInit {
  @ViewChild('wizard', {static: true}) wizard: WizardComponent;

  formInvalid: boolean = true;
  api: Api = new Api();
  apiType: ServiceType = null;
  
  // Wizard Step 1
  step1Config: WizardStepConfig;
  step2Config: WizardStepConfig;
  step3Config: WizardStepConfig;
  step4Config: WizardStepConfig;

  // Wizard
  wizardConfig: WizardConfig;
  wizardHost: ServicesPageComponent;

  constructor(@Host() wizardHost: ServicesPageComponent) {
    this.wizardHost = wizardHost;
  }

  ngOnInit() {
    var wizardTitle: string = 'Add a new Direct API';
    
    // Step 1
    this.step1Config = {
      id: 'step1',
      priority: 0,
      title: 'Direct API type',
      expandReviewDetails: true,
      nextEnabled: false
    } as WizardStepConfig;

    // Step 2
    this.step2Config = {
      id: 'step2',
      priority: 0,
      title: 'API properties',
      expandReviewDetails: true,
      nextEnabled: true
    } as WizardStepConfig;

    // Step 3
    this.step3Config = {
      id: 'step3',
      priority: 0,
      title: 'Reference payload',
      expandReviewDetails: true,
      nextEnabled: true
    } as WizardStepConfig;

    // Step 4
    this.step4Config = {
      id: 'step4',
      priority: 2,
      title: 'Review'
    } as WizardStepConfig;

    // Wizard
    this.wizardConfig = {
      title: wizardTitle,
      sidebarStyleClass: 'example-wizard-sidebar',
      stepStyleClass: 'example-wizard-step'
    } as WizardConfig;

    this.setNavAway(false);
  }

  nextClicked($event: WizardEvent): void {
    if ($event.step.config.id === 'step4') {
      //
      this.wizardHost.createDirectAPI(this.apiType, this.api);
      this.wizardHost.closeDirectAPIWizardModal($event);
    }
  }

  stepChanged($event: WizardEvent) {
    let flatSteps = this.flattenWizardSteps(this.wizard);
    let currentStep = flatSteps.filter(step => step.config.id === $event.step.config.id);
    if (currentStep && currentStep.length > 0) {
      currentStep[0].config.nextEnabled = true;
    }
    if ($event.step.config.id === 'step1') {
      this.updateApiType();
    } if ($event.step.config.id === 'step2') {
      this.updateApiProperties();
    } if ($event.step.config.id === 'step3') {
      this.updateApiReference();
    } else {
      this.wizardConfig.nextTitle = 'Next >';
    }
  }

  changeApiType(type: ServiceType): void {
    this.apiType = type;
    this.updateApiType();
  }
  updateApiType(): void {
    this.step1Config.nextEnabled = 
      (this.apiType !== undefined && this.apiType != null && 
        (this.apiType == ServiceType.GENERIC_REST || this.apiType == ServiceType.GENERIC_EVENT));
    this.setNavAway(this.step1Config.nextEnabled);
  }
  updateApiProperties(): void {
    this.formInvalid = false;
    if (this.api.name == null || this.api.version == null || this.api.resource == null) {
      this.formInvalid = true;
    }
    this.step2Config.nextEnabled = !this.formInvalid;
  }
  updateApiReference(): void {
    this.formInvalid = false;
    if (this.api.referencePayload != null && this.api.referencePayload.trim() != "") {
      try {
        JSON.parse(this.api.referencePayload)
      } catch (e) {
        this.formInvalid = true;
      }
    }
    if (this.apiType === ServiceType.GENERIC_EVENT && 
        ((this.api.referencePayload == null || this.api.referencePayload.trim() === ""))) {
      this.formInvalid = true;
    }
    this.step3Config.nextEnabled = !this.formInvalid;
  }

  private setNavAway(allow: boolean) {
    this.step1Config.allowClickNav = allow;
    this.step2Config.allowClickNav = allow;
    this.step3Config.allowClickNav = allow;
    this.step4Config.allowClickNav = allow;
  }

  private flattenWizardSteps(wizard: WizardComponent): WizardStep[] {
    let flatWizard: WizardStep[] = [];
    wizard.steps.forEach((step: WizardStepComponent) => {
      if (step.hasSubsteps) {
        step.steps.forEach(substep => {
          flatWizard.push(substep);
        });
      } else {
        flatWizard.push(step);
      }
    });
    return flatWizard;
  }
}