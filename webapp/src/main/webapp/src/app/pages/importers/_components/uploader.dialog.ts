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
import { Component, OnInit } from '@angular/core';

import { BsModalRef } from 'ngx-bootstrap/modal';
import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';
import { FileUploader, FileItem, ParsedResponseHeaders } from 'ng2-file-upload';


@Component({
  selector: 'uploader-dialog',
  templateUrl: './uploader.dialog.html',
  styleUrls: ['./uploader.dialog.css']
})
export class ArtifactUploaderDialogComponent implements OnInit {
  title: string;
  closeBtnName: string;

  uploader: FileUploader = new FileUploader({url: '/api/artifact/upload', itemAlias: 'file'});
  
  constructor(public bsModalRef: BsModalRef, private notificationService: NotificationService) {}
 
  ngOnInit() {
    this.uploader.onErrorItem = (item: FileItem, response: string, status: number, headers: ParsedResponseHeaders) => {
      this.notificationService.message(NotificationType.DANGER,
        item.file.name, "Importation error on server side (" + response + ")", false, null, null);
    }
    this.uploader.onSuccessItem = (item: FileItem, response: string, status: number, headers: ParsedResponseHeaders) => {
      this.notificationService.message(NotificationType.SUCCESS,
        item.file.name, "Import of " + response + " done!", false, null, null);
    }
  }
}