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
import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import {
  FormsModule,
  ReactiveFormsModule,
} from '@angular/forms';

import { BsModalRef } from 'ngx-bootstrap/modal';
import {
  NotificationService,
  NotificationType,
} from '../patternfly-ng/notification';
import { FileUploader, FileItem, ParsedResponseHeaders, FileUploadModule } from 'ng2-file-upload';
import { IAuthenticationService } from '../../services/auth.service';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { TabsModule } from 'ngx-bootstrap/tabs';

@Component({
  selector: 'app-uploader-dialog',
  templateUrl: './uploader-dialog.component.html',
  styleUrls: ['./uploader-dialog.component.css'],
  imports: [
    FileUploadModule,
    FormsModule,
    ReactiveFormsModule,
  CommonModule,
  TabsModule
  ],
})
export class UploaderDialogComponent implements OnInit {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;
  
  title?: string;
  closeBtnName?: string;
  preSelectedFiles?: File[];

  uploader: FileUploader;
  // Map to track secondary artifact status for each file
  fileSecondaryStatus = new Map<FileItem, boolean>();

  constructor(
    public bsModalRef: BsModalRef,
    private notificationService: NotificationService,
    protected authService: IAuthenticationService,
    private router: Router
  ) {
    if (this.authService.isAuthenticated()) {
      this.uploader = new FileUploader({
        url: '/api/artifact/upload',
        authToken: 'Bearer ' + this.authService.getAuthenticationSecret(),
        itemAlias: 'file',
        parametersBeforeFiles: true,
      });
    } else {
      this.uploader = new FileUploader({
        url: '/api/artifact/upload',
        itemAlias: 'file',
        parametersBeforeFiles: true,
      });
    }
  }

  ngOnInit() {
    this.uploader.onErrorItem = (
      item: FileItem,
      response: string,
      status: number,
      headers: ParsedResponseHeaders
    ) => {
      this.notificationService.message(
        NotificationType.DANGER,
        item.file.name ?? 'Unknown file',
        'Importation error on server side (' + response + ')',
        false
      );
    };
    this.uploader.onSuccessItem = (
      item: FileItem,
      response: string,
      status: number,
      headers: ParsedResponseHeaders
    ) => {
      this.notificationService.message(
        NotificationType.SUCCESS,
        item.file.name ?? 'Unknown file',
        'Import of ' + response + ' done!',
        false
      );
    };
    this.uploader.onCompleteAll = () => {
        // close dialog and redirect to services
        this.bsModalRef.hide();
    };

    // Add pre-selected files if any
    if (this.preSelectedFiles && this.preSelectedFiles.length > 0) {
      this.addFiles(this.preSelectedFiles);
    }
  }

  protected upload(): void {
    this.uploader.onBuildItemForm = (item: FileItem, form: any) => {
      // Use individual file's secondary status, defaulting to false (main artifact)
      const isSecondaryArtifact = this.fileSecondaryStatus.get(item) || false;
      form.append('mainArtifact', !isSecondaryArtifact);
    };
    // reorder the queue by putting the main artifact first
    this.uploader.queue.sort((a, b) => {
      const aIsMain = this.fileSecondaryStatus.get(a) === false;
      const bIsMain = this.fileSecondaryStatus.get(b) === false;
      return aIsMain === bIsMain ? 0 : aIsMain ? -1 : 1;
    });
    this.uploader.uploadAll();
  }

  /**
   * Gets the secondary artifact status for a file
   * @param item FileItem to check
   * @returns true if file is marked as secondary artifact
   */
  getFileSecondaryStatus(item: FileItem): boolean {
    return this.fileSecondaryStatus.get(item) || false;
  }

  /**
   * Updates the secondary artifact status for a file
   * @param item FileItem to update
   * @param isSecondary true if file should be marked as secondary artifact
   */
  updateFileSecondaryStatus(item: FileItem, isSecondary: boolean): void {
    this.fileSecondaryStatus.set(item, isSecondary);
  }

  /**
   * Adds new files to the uploader queue
   * @param files Files to add to the queue
   */
  addFiles(files: File[]): void {
    if (files && files.length > 0) {
      this.uploader.addToQueue(files);
      for (const item of this.uploader.queue) {
        if (!this.fileSecondaryStatus.has(item)) {
          this.fileSecondaryStatus.set(item, false);
        }
      }
    }
  }

  /**
   * Removes a file from the uploader queue
   * @param item FileItem to remove
   */
  removeFile(item: FileItem): void {
    // Clean up the secondary status tracking
    this.fileSecondaryStatus.delete(item);
    item.remove();
  }

  /**
   * Triggers the hidden file input when placeholder is clicked
   */
  triggerFileInput(): void {
    this.fileInput.nativeElement.click();
  }
}
