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
import { Component } from '@angular/core';
import { BsModalRef } from 'ngx-bootstrap/modal';
import { CommonModule } from '@angular/common';
import { TabsModule } from 'ngx-bootstrap/tabs';
import { QuickImportUploadTabComponent } from './tabs/upload/quick-import-upload-tab.component';

@Component({
  selector: 'app-quick-import-dialog',
  templateUrl: './quick-import-dialog.component.html',
  styleUrls: ['./quick-import-dialog.component.css'],
  imports: [CommonModule, TabsModule, QuickImportUploadTabComponent],
})
/**
 * Container dialog for Quick Import with multiple tabs.
 * Currently embeds the Upload File tab as a standalone component.
 */
export class QuickImportDialogComponent {
  title?: string;
  closeBtnName?: string;
  preSelectedFiles?: File[];
  constructor(public bsModalRef: BsModalRef) {}
}
