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
import { Injectable } from '@angular/core';
import { BsModalRef, BsModalService } from 'ngx-bootstrap/modal';
import { UploaderDialogComponent } from '../components/uploader-dialog/uploader-dialog.component';

export interface UploaderDialogOptions {
  /** Callback to execute when the dialog is closed */
  onClose?: () => void;
  /** Custom close button name */
  closeBtnName?: string;
  /** Pre-selected files to add to the uploader (only first file will be used) */
  preSelectedFiles?: File[];
  /** Additional initial state for the dialog */
  initialState?: any;
}

@Injectable({
  providedIn: 'root'
})
export class UploaderDialogService {
  
  private currentModalRef: BsModalRef | null = null;

  constructor(private modalService: BsModalService) {}

  /**
   * Opens the artifact uploader dialog
   * @param options Configuration options for the dialog
   * @returns The modal reference
   */
  openArtifactUploader(options: UploaderDialogOptions = {}): BsModalRef {
    const initialState = {
      preSelectedFiles: options.preSelectedFiles,
      ...options.initialState
    };
    console.log('Initial state for uploader dialog:', initialState);
    
    const modalRef = this.modalService.show(UploaderDialogComponent, { initialState });
    this.currentModalRef = modalRef;
    
    if (modalRef.content) {
      modalRef.content.closeBtnName = options.closeBtnName || 'Close';
      if (options.preSelectedFiles) {
        modalRef.content.preSelectedFiles = options.preSelectedFiles;
      }
    }
    
    // Subscribe to modal close event if callback provided
    if (options.onClose) {
      modalRef.onHidden?.subscribe(() => {
        options.onClose!();
      });
    }

    // Clear the reference when modal is closed
    modalRef.onHidden?.subscribe(() => {
      this.currentModalRef = null;
    });
    
    return modalRef;
  }

  /**
   * Checks if an uploader dialog is currently open
   * @returns true if a dialog is open, false otherwise
   */
  isDialogOpen(): boolean {
    return this.currentModalRef !== null;
  }

  /**
   * Adds files to the currently open uploader dialog
   * @param files Files to add to the existing dialog
   * @returns true if files were added successfully, false if no dialog is open
   */
  addFilesToOpenDialog(files: File[]): boolean {
    if (!this.currentModalRef || !this.currentModalRef.content) {
      console.warn('No modal reference or content available for uploader dialog');
      return false;
    }

    const component = this.currentModalRef.content as UploaderDialogComponent;
    if (component.addFiles) {
      component.addFiles(files);
      return true;
    }

    console.warn('Component does not have addFiles method');
    return false;
  }
}
