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
import { Component, OnInit, ViewChild, ViewEncapsulation, Host } from '@angular/core';

import { WizardComponent, WizardConfig, WizardEvent, WizardStep, WizardStepComponent, WizardStepConfig } from 'patternfly-ng/wizard';

import { ImportersPageComponent } from '../importers.page';
import { ImportJob } from '../../../models/importer.model';
import { Secret, SecretRef } from '../../../models/secret.model';
import { SecretsService } from '../../../services/secrets.service';


@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'importer-wizard',
  templateUrl: './importer.wizard.html',
  styleUrls: ['./importer.wizard.css']
})
export class ImporterWizardComponent implements OnInit {
  @ViewChild('wizard', {static: true}) wizard: WizardComponent;

  data: any = {};
  job: ImportJob = null;
  useSecret: boolean = false;
  secrets: Secret[];

  // Wizard Step 1
  step1Config: WizardStepConfig;
  step2Config: WizardStepConfig;
  step3Config: WizardStepConfig;

  // Wizard
  wizardConfig: WizardConfig;
  wizardHost: ImportersPageComponent;

  constructor(@Host() wizardHost: ImportersPageComponent, private secretsSvc: SecretsService) {
    this.wizardHost = wizardHost;
  }
  
  ngOnInit() {
    var wizardTitle: string = 'Create a new Job';
    if (this.wizardHost.selectedJob) {
      this.job = this.wizardHost.selectedJob;
      this.useSecret = (this.job.secretRef != null);
      wizardTitle = 'Edit existing Job "' + this.job.name + '"';
    } else {
      this.job = new ImportJob();
    }

    // Step 1
    this.step1Config = {
      id: 'step1',
      priority: 0,
      title: 'Importer Job properties',
      expandReviewDetails: true,
      nextEnabled: false
    } as WizardStepConfig;
    
    // Step 2
    this.step2Config = {
      id: 'step2',
      priority: 0,
      title: 'Authentication options',
      expandReviewDetails: true,
      nextEnabled: true
    } as WizardStepConfig;

    // Step 3
    this.step3Config = {
      id: 'step3',
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

  ngAfterViewInit() {
    this.getSecrets();
  }

  getSecrets(page: number = 1): void {
    this.secretsSvc.getSecrets(page).subscribe(results => this.secrets = results);
  }

  nextClicked($event: WizardEvent): void {
    if ($event.step.config.id === 'step3') {
      //
      this.wizardHost.saveOrUpdateImportJob(this.job);
      this.wizardHost.closeImportJobWizardModal($event);
    }
  }

  stepChanged($event: WizardEvent) {
    let flatSteps = this.flattenWizardSteps(this.wizard);
    let currentStep = flatSteps.filter(step => step.config.id === $event.step.config.id);
    if (currentStep && currentStep.length > 0) {
      currentStep[0].config.nextEnabled = true;
    }
    if ($event.step.config.id === 'step1') {
      this.updateJobProperties();
    } else if ($event.step.config.id === 'step3') {
      if (this.job.id) {
        this.wizardConfig.nextTitle = 'Update';  
      } else {
        this.wizardConfig.nextTitle = 'Create';
      }
    } else {
      this.wizardConfig.nextTitle = 'Next >';
    }
  }

  updateJobProperties(): void {
    this.step1Config.nextEnabled = 
      (this.job.name !== undefined && this.job.name.length > 0 && this.job.repositoryUrl !== undefined && this.job.repositoryUrl.length > 0);
    this.setNavAway(this.step1Config.nextEnabled);
    if (this.useSecret && this.job.secretRef == null) {
      this.job.secretRef = new SecretRef('none', '');
    } else {
      this.job.secretRef = null;
    }
  }
  updateSecretProperties(event: any): void {
    var secretId = event.target.value;
    if ('none' != event.target.value) {
      for (var i=0; i<this.secrets.length; i++) {
        var secret = this.secrets[i];
        if (secretId === secret.id) {
          this.job.secretRef = new SecretRef(secret.id, secret.name);
          break;
        }
      };
    } else {
      this.job.secretRef = null;
    }
  }

  private setNavAway(allow: boolean) {
    this.step1Config.allowClickNav = allow;
    this.step2Config.allowClickNav = allow;
    this.step3Config.allowClickNav = allow;
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