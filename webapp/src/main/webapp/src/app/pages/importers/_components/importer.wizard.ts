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
import {
  Component,
  OnInit,
  viewChild,
  ViewEncapsulation,
  Output,
  EventEmitter,
  ChangeDetectorRef,
  AfterViewInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormsModule,
  ReactiveFormsModule,
} from '@angular/forms';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';

import { BsModalRef } from 'ngx-bootstrap/modal';

import { EditLabelsComponent } from '../../../components/edit-labels/edit-labels.component';
import { LabelListComponent } from '../../../components/label-list/label-list.component';

import { ImportJob } from '../../../models/importer.model';
import { Secret, SecretRef } from '../../../models/secret.model';
import { Metadata } from '../../../models/commons.model';
import { SecretsService } from '../../../services/secrets.service';

@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'app-importer-wizard',
  templateUrl: './importer.wizard.html',
  styleUrls: ['./importer.wizard.css'],
  imports: [
    CommonModule,
    EditLabelsComponent,
    FormsModule, 
    LabelListComponent,
    MatStepperModule, 
    ReactiveFormsModule
  ]
})
export class ImporterWizardComponent implements OnInit, AfterViewInit {
  private stepper = viewChild<MatStepper>('stepper');

  @Output() saveImportJobAction = new EventEmitter<ImportJob>();

  wizardTitle = 'Create a new Job';
  formInvalid = true;

  data: any = {};
  job: ImportJob = {} as ImportJob;
  useSecret = false;
  secrets: Secret[] = [];

  constructor(
    private secretsSvc: SecretsService,
    private ref: ChangeDetectorRef,
    public bsModalRef: BsModalRef
  ) {
  }

  ngOnInit() {
    this.wizardTitle = 'Create a new Job';
    if (this.job.id != null) {
      this.useSecret = this.job.secretRef != null;
      this.wizardTitle = 'Edit existing Job "' + this.job.name + '"';
      this.updateJobProperties();
    } else {
      this.job = {
        metadata: {
          labels: {},
        } as Metadata,
      } as ImportJob;
    }
  }

  ngAfterViewInit() {
    // TODO: verify if needed?
    this.getSecrets();
  }

  getSecrets(page: number = 1): void {
    this.secretsSvc
      .getSecrets(page)
      .subscribe((results) => (this.secrets = results));
  }

  updateJobProperties(): void {
    this.formInvalid = !(
      this.job.name !== undefined &&
      this.job.name.length > 0 &&
      this.job.repositoryUrl !== undefined &&
      this.job.repositoryUrl.length > 0);

    if (this.useSecret && this.job.secretRef == null) {
      this.job.secretRef = new SecretRef('none', '');
    } else if (!this.useSecret) {
      this.job.secretRef = undefined;
    }

    if (this.job.metadata == undefined) {
      this.job.metadata = {} as Metadata;
      this.job.metadata.labels = {};
    }
  }
  updateMainArtifact(event: any): void {
    this.job.mainArtifact = !event;
  }
  updateSecretProperties(event: any): void {
    const secretId = event.target.value;
    if ('none' != event.target.value) {
      for (const secret of this.secrets) {
        if (secretId === secret.id) {
          this.job.secretRef = new SecretRef(secret.id, secret.name);
          break;
        }
      }
    } else {
      this.job.secretRef = undefined;
    }
  }

  save($event: any) {
    this.saveImportJobAction.emit(this.job);
    this.close();
  }

  close(): void {
    this.bsModalRef.hide()
  }

  isNoSecretSelected(): boolean {
    return this.job.secretRef == undefined || this.job.secretRef?.secretId === 'none';
  }
  isSecretSelected(secret: Secret): boolean {
    return secret.id === this.job.secretRef?.secretId;
  }

  isNextDisabled(): boolean {
    return this.formInvalid;
  }
  next($event: any): void {
    // Because label list is eagerly loaded, it doesn't see changes in labels.
    // And because the label list uses the ChangeDetectionStrategy.OnPush, we have to explicitely
    // set a new value (and not only mutate) to this.job to force evaluation later on.
    // This is the only way I know to build a deep clone of job and force reassignement...
    this.job = JSON.parse(JSON.stringify(this.job));
    // Trigger view reevaluation to update the label list component.
    this.ref.detectChanges();

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
