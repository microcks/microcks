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
import { Component, OnInit, TemplateRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';

import { Observable, concat } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { BsDropdownModule } from 'ngx-bootstrap/dropdown';
import { BsModalRef, BsModalService } from 'ngx-bootstrap/modal';

import {
  Notification,
  NotificationEvent,
  NotificationService,
  NotificationType,
  ToastNotificationListComponent,
} from '../../../../components/patternfly-ng/notification';

import { markdownConverter } from '../../../../components/markdown';

import { HubService } from '../../../../services/hub.service';
import { ImportersService } from '../../../../services/importers.service';
import {
  APIPackage,
  APIVersion,
  APISummary,
} from '../../../../models/hub.model';
import { ImportJob } from '../../../../models/importer.model';

@Component({
  selector: 'app-hub-api-version-page',
  templateUrl: './apiVersion.page.html',
  styleUrls: ['./apiVersion.page.css'],
  imports: [
    CommonModule,
    BsDropdownModule,
    RouterLink,
    ToastNotificationListComponent
  ]
})
export class HubAPIVersionPageComponent implements OnInit {
  
  modalRef?: BsModalRef;
  package: Observable<APIPackage> | null = null;
  packageAPIVersion: Observable<APIVersion> | null = null;
  resolvedPackage?: APIPackage;
  resolvedPackageAPI?: APISummary;
  resolvedAPIVersion?: APIVersion;
  notifications: Notification[] = [];

  importJobId: string | null = null;
  discoveredService: string | null = null;

  constructor(
    private packagesSvc: HubService,
    private importersSvc: ImportersService,
    private modalService: BsModalService,
    private notificationService: NotificationService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();

    this.package = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.packagesSvc.getPackage(params.get('packageId')!)
      )
    );
    this.packageAPIVersion = this.route.paramMap.pipe(
      switchMap((params: ParamMap) =>
        this.packagesSvc.getAPIVersion(
          params.get('packageId')!,
          params.get('apiVersionId')!
        )
      )
    );

    this.package.subscribe((result) => {
      this.resolvedPackage = result;
      this.packageAPIVersion!.subscribe((apiVersion) => {
        this.resolvedPackage!.apis.forEach((api) => {
          if (api.name === apiVersion.id) {
            this.resolvedPackageAPI = api;
          }
        });
      });
    });
    this.packageAPIVersion.subscribe((result) => {
      this.resolvedAPIVersion = result;
    });
  }

  renderDescription(): string {
    return markdownConverter.makeHtml(this.resolvedAPIVersion!.description);
  }

  renderCapabilityLevel(): string {
    if ('Full Mocks' === this.resolvedAPIVersion!.capabilityLevel) {
      return '/assets/images/mocks-level-2.svg';
    } else if (
      'Mocks + Assertions' === this.resolvedAPIVersion!.capabilityLevel
    ) {
      return '/assets/images/mocks-level-2.svg';
    }
    return '/assets/images/mocks-level-1.svg';
  }

  openModal(template: TemplateRef<void>) {
    this.modalRef = this.modalService.show(template, { class: 'modal-lg' });
  }

  handleCloseNotification($event: NotificationEvent): void {
    this.notificationService.remove($event.notification);
  }

  installByDirectUpload(): void {
    this.notificationService.message(
      NotificationType.INFO,
      this.resolvedAPIVersion!.name,
      'Starting install in Microcks. Hold on...',
      false
    );

    const uploadBatch = [];
    for (let i = 0; i < this.resolvedAPIVersion!.contracts.length; i++) {
      console.log('Uploading contract: ' + this.resolvedAPIVersion!.contracts[i].url);
      uploadBatch.push(
        this.packagesSvc.importAPIVersionContractContent(
          this.resolvedAPIVersion!.contracts[i].url,
          i == 0
        )
      );
    }

    // Concat all the observables to run them in sequence.
    concat(...uploadBatch).subscribe({
      next: (res: any) => {
        this.discoveredService = res.name;
        this.notificationService.message(
          NotificationType.SUCCESS,
          this.discoveredService!,
          'Import and discovery of service has been done', false
        );
      },
      error: (err) => {
        this.notificationService.message(
          NotificationType.DANGER,
          this.resolvedAPIVersion!.name, 
          'Importation error on server side (' + err.error.text + ')', false
        );
      },
      complete: () => {} //console.log('Observer got a complete notification'),
    });
  }

  installByImporterCreation(): void {
    for (let i = 0; i < this.resolvedAPIVersion!.contracts.length; i++) {
      const job = {} as ImportJob;
      job.name =
        this.resolvedAPIVersion!.id +
        ' - v. ' +
        this.resolvedAPIVersion!.version +
        ' [' + i + ']';
      job.repositoryUrl = this.resolvedAPIVersion!.contracts[i].url;
      // Mark is as secondary artifact if not the first.
      if (i > 0) {
        job.mainArtifact = false;
      }
      this.importersSvc.createImportJob(job).subscribe({
        next: (res) => {
          this.notificationService.message(
            NotificationType.SUCCESS,
            job.name, 'Import job has been created', false
          );
          // Retrieve job id before activating.
          job.id = res.id;
          this.importJobId = job.id;
          this.activateImportJob(job);
        },
        error: (err) => {
          this.notificationService.message(
            NotificationType.DANGER,
            job.name, 'Import job cannot be created (' + err.message + ')', false
          );
        },
        complete: () => {} //console.log('Observer got a complete notification'),
      });
    }
  }

  activateImportJob(job: ImportJob): void {
    this.importersSvc.activateImportJob(job).subscribe({
      next: (res) => {
        job.active = true;
        this.notificationService.message(
          NotificationType.SUCCESS,
          job.name, 'Import job has been started/activated', false
        );
        this.startImportJob(job);
      },
      error: (err) => {
        this.notificationService.message(
          NotificationType.DANGER,
          job.name, 'Import job cannot be started/activated (' + err.message + ')', false
        );
      },
      complete: () => {} //console.log('Observer got a complete notification'),
    });
  }

  startImportJob(job: ImportJob): void {
    this.importersSvc.startImportJob(job).subscribe({
      next: (res) => {
        this.notificationService.message(
          NotificationType.SUCCESS,
          job.name, 'Import job has been forced', false
        );
      },
      error: (err) => {
        this.notificationService.message(
          NotificationType.DANGER,
          job.name, 'Import job cannot be forced now', false
        );
      },
      complete: () => {} //console.log('Observer got a complete notification'),
    });
  }

  navigateToImporters(): void {
    this.modalRef?.hide();
    this.router.navigate(['/importers']);
  }
  navigateToService(): void {
    this.modalRef?.hide();
    this.router.navigate(['/services', this.discoveredService]);
  }
}
