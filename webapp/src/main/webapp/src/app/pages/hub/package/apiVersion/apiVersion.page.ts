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
import { ActivatedRoute, Router, ParamMap } from "@angular/router";

import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';

import { Observable, concat } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { HubService } from '../../../../services/hub.service';
import { ImportersService } from '../../../../services/importers.service';
import { APIPackage, APIVersion, APISummary } from '../../../../models/hub.model';
import { ImportJob } from '../../../../models/importer.model';

import { markdownConverter } from '../../../../components/markdown';

@Component({
  selector: 'hub-apiVersion-page',
  templateUrl: './apiVersion.page.html',
  styleUrls: ['./apiVersion.page.css']
})
export class HubAPIVersionPageComponent implements OnInit {

  package: Observable<APIPackage>;
  packageAPIVersion: Observable<APIVersion>;
  resolvedPackage: APIPackage;
  resolvedPackageAPI: APISummary;
  resolvedAPIVersion: APIVersion;
  notifications: Notification[];

  importJobId: string;
  discoveredService: string;

  constructor(private packagesSvc: HubService, private importersSvc: ImportersService, private route: ActivatedRoute,
    private notificationService: NotificationService) { }

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();

    this.package = this.route.paramMap.pipe(
      switchMap((params: ParamMap) => 
        this.packagesSvc.getPackage(params.get('packageId')))
    );
    this.packageAPIVersion = this.route.paramMap.pipe(
      switchMap((params: ParamMap) => 
        this.packagesSvc.getAPIVersion(params.get('packageId'), params.get('apiVersionId')))
    );

    this.package.subscribe( result => {
      this.resolvedPackage = result;
      this.packageAPIVersion.subscribe( apiVersion => {
        this.resolvedPackage.apis.forEach(api => {
          if (api.name === apiVersion.id) {
            this.resolvedPackageAPI = api;
          }
        });
      });
    });
    this.packageAPIVersion.subscribe (result => {
      this.resolvedAPIVersion = result;
    });
  }

  renderDescription(): string {
    return markdownConverter.makeHtml(this.resolvedAPIVersion.description);
  }

  renderCapabilityLevel(): string {
    if ("Full Mocks" === this.resolvedAPIVersion.capabilityLevel) {
      return "/assets/images/mocks-level-2.svg"
    } else if ("Mocks + Assertions" === this.resolvedAPIVersion.capabilityLevel) {
      return "/assets/images/mocks-level-2.svg"
    }
    return "/assets/images/mocks-level-1.svg"
  }

  onModalEnter(): void {
    // Nothing to do here.
  }

  handleCloseNotification($event: NotificationEvent): void {
    this.notificationService.remove($event.notification);
  }

  installByDirectUpload(): void {
    this.notificationService.message(NotificationType.INFO, this.resolvedAPIVersion.name, "Starting install in Microcks. Hold on...", false, null, null);

    let uploadBatch = [];
    for (let i=0; i<this.resolvedAPIVersion.contracts.length; i++) {
      uploadBatch.push(this.packagesSvc.importAPIVersionContractContent(this.resolvedAPIVersion.contracts[i].url, (i == 0)));
    }

    // Concat all the observables to run them in sequence.
    concat(...uploadBatch).subscribe(
      {
        next: res => {
          this.discoveredService = res['name'];
          this.notificationService.message(NotificationType.SUCCESS,
            this.discoveredService, "Import and discovery of service has been done", false, null, null);
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
            this.resolvedAPIVersion.name, "Importation error on server side (" + err.error.text + ")", false, null, null);
        },
        complete: () => console.log('Observer got a complete notification'),
      }
    );
  }

  installByImporterCreation(): void {
    for (let i=0; i<this.resolvedAPIVersion.contracts.length; i++) {
      var job = new ImportJob();
      job.name = this.resolvedAPIVersion.id + " - v. " + this.resolvedAPIVersion.version + " [" + i + "]";
      job.repositoryUrl = this.resolvedAPIVersion.contracts[i].url;
      // Mark is as secondary artifact if not the first.
      if (i > 0) {
        job.mainArtifact = false;
      }
      this.importersSvc.createImportJob(job).subscribe(
        {
          next: res => {
            this.notificationService.message(NotificationType.SUCCESS,
                job.name, "Import job has been created", false, null, null);
            // Retrieve job id before activating.
            job.id = res.id;
            this.importJobId = job.id;
            this.activateImportJob(job);
          },
          error: err => {
            this.notificationService.message(NotificationType.DANGER,
                job.name, "Import job cannot be created (" + err.message + ")", false, null, null);
          },
          complete: () => console.log('Observer got a complete notification'),
        }
      );
    }
  }

  activateImportJob(job: ImportJob): void {
    this.importersSvc.activateImportJob(job).subscribe(
      {
        next: res => {
          job.active = true;
          this.notificationService.message(NotificationType.SUCCESS,
              job.name, "Import job has been started/activated", false, null, null);
          this.startImportJob(job);
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              job.name, "Import job cannot be started/activated (" + err.message + ")", false, null, null);
        },
        complete: () => console.log('Observer got a complete notification'),
      }
    );
  }

  startImportJob(job: ImportJob): void {
    this.importersSvc.startImportJob(job).subscribe(
      {
        next: res => {
          this.notificationService.message(NotificationType.SUCCESS,
              job.name, "Import job has been forced", false, null, null);
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              job.name, "Import job cannot be forced now", false, null, null);
        },
        complete: () => console.log('Observer got a complete notification'),
      }
    );
  }
}