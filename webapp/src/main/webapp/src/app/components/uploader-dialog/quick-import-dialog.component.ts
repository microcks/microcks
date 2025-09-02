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
import { Component, ViewChild } from '@angular/core';
import { BsModalRef } from 'ngx-bootstrap/modal';
import { CommonModule } from '@angular/common';
import { TabsModule } from 'ngx-bootstrap/tabs';
import { QuickImportUploadTabComponent } from './tabs/upload/quick-import-upload-tab.component';
import { QuickImportDownloadTabComponent } from './tabs/download/quick-import-download-tab.component';

@Component({
  selector: 'app-quick-import-dialog',
  templateUrl: './quick-import-dialog.component.html',
  styleUrls: ['./quick-import-dialog.component.css'],
  imports: [CommonModule, TabsModule, QuickImportUploadTabComponent, QuickImportDownloadTabComponent],
})
/**
 * Container dialog for Quick Import with multiple tabs.
 * Currently embeds the Upload File tab as a standalone component.
 */
export class QuickImportDialogComponent {
  title?: string;
  closeBtnName?: string;
  preSelectedFiles?: File[];
  /** Reference to the Upload tab to delegate file-queue operations */
  @ViewChild(QuickImportUploadTabComponent) private uploadTab?: QuickImportUploadTabComponent;
  constructor(public bsModalRef: BsModalRef) {}

  /**
   * Add files to the Upload tab queue. Keeps preSelectedFiles in sync as well.
   */
  addFiles(files: File[]): void {
    if (!files || files.length === 0) { return; }
    // Keep local state (used for input binding) in sync for future reopens/renders
    this.preSelectedFiles = [...(this.preSelectedFiles || []), ...files];
    // Delegate to the upload tab if available (after view init)
    this.uploadTab?.addFiles(files);
  }
}
