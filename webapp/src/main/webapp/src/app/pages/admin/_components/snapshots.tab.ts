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

import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';
import { FileUploader } from 'ng2-file-upload';

import { Service } from '../../../models/service.model';
import { ServicesService } from '../../../services/services.service';

@Component({
  selector: 'snapshots-tab',
  templateUrl: './snapshots.tab.html',
  styleUrls: ['./snapshots.tab.css']
})
export class SnapshotsTabComponent implements OnInit {

  halfServices: Service[];
  secondHalfServices: Service[];
  servicesCount: number;

  selectedServices: any = { ids: {} };
  uploader: FileUploader = new FileUploader({url: '/api/import', itemAlias: 'file'});

  constructor(private servicesSvc: ServicesService, private notificationService: NotificationService) {}

  ngOnInit() {
    this.getAllServices();
    this.uploader.onAfterAddingFile = (file) => { file.withCredentials = false; };
    this.uploader.onCompleteItem = (item: any, response: any, status: any, headers: any) => {
      console.log('SnapshotUpload:uploaded:', item, status, response);
      this.notificationService.message(NotificationType.SUCCESS,
        "Snapshot", "File uploaded successfully", false, null, null);
    };
  }

  getAllServices():void {
    this.servicesSvc.getServices(1, 1000).subscribe(
      results => {
        this.halfServices = results.slice(0, (results.length / 2) + 1);
        this.secondHalfServices = results.slice((results.length / 2) + 1, results.length);
        this.servicesCount = results.length;
      }
    );
  }

  public createExport(): void {
    var downloadPath = '/api/export?';
    Object.keys(this.selectedServices.ids).forEach(function(element, index, array) {
      downloadPath += '&serviceIds=' + element;
    });
    window.open(downloadPath, '_blank', '');
  }
}