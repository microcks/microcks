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
import { FormsModule } from '@angular/forms';

import { NotificationService, NotificationType, } from '../../../components/patternfly-ng/notification';
import { FileUploader, FileUploadModule } from 'ng2-file-upload';

import { Service } from '../../../models/service.model';
import { ServicesService } from '../../../services/services.service';
import { IAuthenticationService } from '../../../services/auth.service';

@Component({
  selector: 'app-snapshots-tab',
  templateUrl: './snapshots.tab.html',
  styleUrls: ['./snapshots.tab.css'],
  imports: [
    CommonModule,
    FormsModule,
    FileUploadModule
  ]
})
export class SnapshotsTabComponent implements OnInit {

  halfServices?: Service[];
  secondHalfServices?: Service[];
  servicesCount: number = 0;

  selectedServices: any = { ids: {} };
  uploader: FileUploader = new FileUploader({url: '/api/import', itemAlias: 'file'});

  constructor(
    private servicesSvc: ServicesService,
    private authService: IAuthenticationService,
    private notificationService: NotificationService
  ) {}

  ngOnInit() {
    this.getAllServices();
    this.uploader.onAfterAddingFile = (file) => { file.withCredentials = false; };
    this.uploader.onCompleteItem = (item: any, response: any, status: any, headers: any) => {
      if (status == 201) {
        this.notificationService.message(NotificationType.SUCCESS,
          'Snapshot', 'File uploaded successfully', false);
      } else {
        this.notificationService.message(NotificationType.DANGER,
          'Snapshot', 'File uploaded failed with status ' + status, false);
      } 
    };
    this.uploader.authToken = 'Bearer ' + this.authService.getAuthenticationSecret();
  }

  getAllServices(): void {
    this.servicesSvc.getServices(1, 1000).subscribe(
      results => {
        this.halfServices = results.slice(0, (results.length / 2) + 1);
        this.secondHalfServices = results.slice((results.length / 2) + 1, results.length);
        this.servicesCount = results.length;
      }
    );
  }

  public createExport(): void {
    let downloadPath = '/api/export?';
    Object.keys(this.selectedServices.ids).forEach(function(element, index, array) {
      downloadPath += '&serviceIds=' + element;
    });

    // Just opening a window with the download path is not working
    // because Authorization header is not sent.
    //window.open(downloadPath, '_blank', '');

    // So we have to use XMLHttpRequest to send Authorization header and get the file
    // before triggering the Save as dialog by simulating a click on a link.
    const xhr = new XMLHttpRequest();
    xhr.open('GET', location.origin + downloadPath, true);
    xhr.setRequestHeader('Accept', 'application/json');
    xhr.setRequestHeader('Authorization', 'Bearer ' + this.authService.getAuthenticationSecret());
    xhr.onreadystatechange = function() {
      if (xhr.readyState == 4) {
        if (xhr.status == 200) {
          const blob = new Blob([xhr.response], { type: 'text/plain' });
          const url = window.URL.createObjectURL(blob);

          var a = document.createElement("a");
          document.body.appendChild(a);
          a.href = url;
          a.download = 'microcks-repository.json';
          a.click();

          window.URL.revokeObjectURL(url);
        } else {
          alert('Problem while retrieving snapshot export');
        }
      }
    };
    xhr.send();
  }
}
