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
import { Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FileItem, FileUploadModule, FileUploader, ParsedResponseHeaders } from 'ng2-file-upload';
import { BsModalRef } from 'ngx-bootstrap/modal';
import { NotificationService, NotificationType } from '../../../patternfly-ng/notification';
import { IAuthenticationService } from '../../../../services/auth.service';

@Component({
  selector: 'app-quick-import-upload-tab',
  standalone: true,
  templateUrl: './quick-import-upload-tab.component.html',
  styleUrls: ['./quick-import-upload-tab.component.css'],
  imports: [CommonModule, FormsModule, ReactiveFormsModule, FileUploadModule]
})
/**
 * Upload File tab for the Quick Import dialog.
 *
 * Responsibilities:
 * - Manage file selection (manual and drag-and-drop via ng2-file-upload)
 * - Maintain a queue with per-file "secondary artifact" flags
 * - Post files to /api/artifact/upload with form data for main/secondary
 * - Close the dialog when uploads complete
 *
 * Inputs:
 * - preSelectedFiles?: File[]  Optional files to pre-populate the queue
 */
export class QuickImportUploadTabComponent implements OnInit {
  /** Optional files provided by the opener to pre-fill the queue */
  @Input() preSelectedFiles?: File[];
  /** Hidden file input used to open the native file chooser */
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  /** Uploader instance configured for the backend endpoint */
  uploader: FileUploader;
  /** Tracks whether a queued file is a secondary artifact (true) or main (false) */
  fileSecondaryStatus = new Map<FileItem, boolean>();

  constructor(
    public bsModalRef: BsModalRef,
    private notificationService: NotificationService,
    protected authService: IAuthenticationService
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

  /**
   * Wire up uploader callbacks for success/error/completion and enqueue any pre-selected files.
   */
  ngOnInit(): void {
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
      this.bsModalRef.hide();
    };

    if (this.preSelectedFiles && this.preSelectedFiles.length > 0) {
      this.addFiles(this.preSelectedFiles);
    }
  }

  /**
   * Start upload of all queued files.
   * - Adds 'mainArtifact' flag per file based on its secondary status.
   * - Reorders queue to send the (single) main artifact first.
   */
  upload(): void {
    this.uploader.onBuildItemForm = (item: FileItem, form: any) => {
      const isSecondaryArtifact = this.fileSecondaryStatus.get(item) || false;
      form.append('mainArtifact', !isSecondaryArtifact);
    };
    this.uploader.queue.sort((a, b) => {
      const aIsMain = this.fileSecondaryStatus.get(a) === false;
      const bIsMain = this.fileSecondaryStatus.get(b) === false;
      return aIsMain === bIsMain ? 0 : aIsMain ? -1 : 1;
    });
    this.uploader.uploadAll();
  }

  /** Get the secondary status for a given queued file item. */
  getFileSecondaryStatus(item: FileItem): boolean {
    return this.fileSecondaryStatus.get(item) || false;
  }

  /** Update the secondary status for a given queued file item. */
  updateFileSecondaryStatus(item: FileItem, isSecondary: boolean): void {
    this.fileSecondaryStatus.set(item, isSecondary);
  }

  /**
   * Add new files to the upload queue; initialize per-file status to main (false) when absent.
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

  /** Remove a file from the queue and clear its tracked secondary status. */
  removeFile(item: FileItem): void {
    this.fileSecondaryStatus.delete(item);
    item.remove();
  }

  /** Programmatically open the hidden native file chooser. */
  triggerFileInput(): void {
    this.fileInput.nativeElement.click();
  }
}
