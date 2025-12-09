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
import { Router } from '@angular/router';
import { BsModalRef, BsModalService } from 'ngx-bootstrap/modal';
import { QuickImportDialogComponent } from '../components/uploader-dialog/quick-import-dialog.component';

export interface UploaderDialogOptions {
  /** Callback to execute when the dialog is closed */
  onClose?: () => void;
  /** Custom close button name */
  closeBtnName?: string;
  /** Pre-selected files to add to the uploader (only first file will be used) */
  preSelectedFiles?: File[];
  /** Pre-filled URL for the download tab */
  prefillUrl?: string;
  /** Tab to activate when opening the dialog */
  activeTab?: 'upload' | 'download';
  /** Additional initial state for the dialog */
  initialState?: UploaderDialogOptions;
}

@Injectable({
  providedIn: 'root'
})
/**
 * Service for managing the artifact uploader dialog within the application.
 *
 * Provides methods to open the uploader dialog, add files to an open dialog,
 * check dialog state, and register/unregister page refresh callbacks for specific routes.
 *
 * Handles dialog lifecycle, including invoking custom close callbacks and
 * route-specific refresh logic when the dialog is closed.
 *
 */
export class UploaderDialogService {

  private currentModalRef: BsModalRef | null = null;

  // Registry of page refresh callbacks by route
  private refreshCallbacks = new Map<string, () => void>();

  constructor(
    private modalService: BsModalService,
    private router: Router
  ) {}

  /**
   * Opens the artifact uploader dialog
   * @param options Configuration options for the dialog
   * @returns The modal reference
   */
  openArtifactUploader(options: UploaderDialogOptions = {
    activeTab: 'upload'
  }): BsModalRef {
    const initialState = {
      preSelectedFiles: options.preSelectedFiles,
      prefillUrl: options.prefillUrl,
      activeTab: options.activeTab,
      ...options.initialState
    };
    
    const modalRef = this.modalService.show(QuickImportDialogComponent, { initialState });
    this.currentModalRef = modalRef;
    
    if (modalRef.content) {
      modalRef.content.closeBtnName = options.closeBtnName || 'Close';
      if (options.preSelectedFiles) {
        modalRef.content.preSelectedFiles = options.preSelectedFiles;
      }
    }
    
    // Subscribe to modal close event
    modalRef.onHidden?.subscribe(() => {
      // Call the provided callback
      if (options.onClose) {
        options.onClose();
      }

      // Also call the registered page refresh callback if available
      const currentRoute = this.getCurrentRouteKey();
      const refreshCallback = this.refreshCallbacks.get(currentRoute);
      if (refreshCallback) {
        refreshCallback();
      }
    });
    
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
    
    const component = this.currentModalRef.content as QuickImportDialogComponent;
    if (component.addFiles) {
      component.addFiles(files);
      return true;
    }
    
    console.warn('Component does not have addFiles method');
    return false;
  }

  /** Prefill the download tab URL if the dialog is already open. */
  prefillDownloadUrlInOpenDialog(url: string): boolean {
    if (!this.currentModalRef || !this.currentModalRef.content) {
      return false;
    }
    
    const component = this.currentModalRef.content as QuickImportDialogComponent;
    if (component.setDownloadUrl) {
      component.setDownloadUrl(url);
      return true;
    }
    
    return false;
  }

  /**
   * Opens the artifact uploader dialog with the Download tab active and URL prefilled.
   */
  openArtifactDownloadWithUrl(url: string, options: UploaderDialogOptions = {}): BsModalRef {
    return this.openArtifactUploader({
      ...options,
      prefillUrl: url,
      activeTab: 'download',
      initialState: {
        ...(options.initialState || {}),
        prefillUrl: url,
        activeTab: 'download'
      }
    });
  }

  /**
   * Registers a refresh callback for a specific route
   * @param route The route path (e.g., '/services')
   * @param callback The function to call when uploader closes on this route
   */
  registerPageRefreshCallback(route: string, callback: () => void): void {
    this.refreshCallbacks.set(route, callback);
  }

  /**
   * Unregisters a refresh callback for a specific route
   * @param route The route path to unregister
   */
  unregisterPageRefreshCallback(route: string): void {
    this.refreshCallbacks.delete(route);
  }

  /**
   * Gets the current route key for callback lookup
   * @returns The current route path
   */
  private getCurrentRouteKey(): string {
    const url = this.router.url;
    // Extract base route without query parameters
    return url.split('?')[0];
  }
}
