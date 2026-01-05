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
import { Injectable, NgZone } from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { UploaderDialogService } from './uploader-dialog.service';

@Injectable({
  providedIn: 'root'
})
/**
 * Service to handle global drag-and-drop file operations in the application.
 *
 * Registers global event listeners for drag-and-drop events on the document,
 * manages drag-over state via an observable, and coordinates file uploads
 * through the `UploaderDialogService`.
 *
 * - Emits `true` on `dragOver$` when files are dragged over the document.
 * - Emits `false` when drag leaves the document or after a drop.
 * - Handles dropped files by opening or updating the uploader dialog.
 * - Ensures dialog operations run inside Angular's zone for proper change detection.
 * - Provides a `destroy()` method to clean up event listeners and complete the observable.
 *
 * @example
 * // Subscribe to drag-over state
 * dragDropService.dragOver$.subscribe(isOver => { ... });
 *
 * @remarks
 * Call `destroy()` when the service is no longer needed to avoid memory leaks.
 */
export class DragDropService {
  private dragOverSubject = new Subject<boolean>();
  public dragOver$ = this.dragOverSubject.asObservable();

  private boundHandleDragEnter = this.handleDragEnter.bind(this);
  private boundHandleDragOver = this.handleDragOver.bind(this);
  private boundHandleDragLeave = this.handleDragLeave.bind(this);
  private boundHandleDrop = this.handleDrop.bind(this);
  private boundHandlePaste = this.handlePaste.bind(this);

  constructor(
    private router: Router,
    private ngZone: NgZone,
    private uploaderDialogService: UploaderDialogService
  ) {
    this.initializeGlobalDragDrop();
  }

  private initializeGlobalDragDrop(): void {
    // Prevent default drag behaviors on document
    document.addEventListener('dragenter', this.boundHandleDragEnter);
    document.addEventListener('dragover', this.boundHandleDragOver);
    document.addEventListener('dragleave', this.boundHandleDragLeave);
    document.addEventListener('drop', this.boundHandleDrop);
    document.addEventListener('paste', this.boundHandlePaste);
  }

  private handleDragEnter(e: DragEvent): void {
    e.preventDefault();
    e.stopPropagation();
    
    if (this.hasFilesOrUrls(e)) {
      this.dragOverSubject.next(true);
    }
  }

  private handleDragOver(e: DragEvent): void {
    e.preventDefault();
    e.stopPropagation();
    
    if (this.hasFilesOrUrls(e)) {
      if (e.dataTransfer) {
        e.dataTransfer.dropEffect = 'copy';
      }
    }
  }

  private handleDragLeave(e: DragEvent): void {
    e.preventDefault();
    e.stopPropagation();
    
    // Only hide the overlay if we're leaving the document
    if (e.clientX === 0 && e.clientY === 0) {
      this.dragOverSubject.next(false);
    }
  }

  private handleDrop(e: DragEvent): void {
    e.preventDefault();
    e.stopPropagation();
    
    this.dragOverSubject.next(false);
    
    const files = e.dataTransfer?.files ? Array.from(e.dataTransfer.files) : [];
    const url = this.extractUrlFromDataTransfer(e.dataTransfer);
    
    if (files.length > 0) {
      this.handleFileDrop(files);
      return;
    }
    
    if (url) {
      this.handleUrlDrop(url);
    }
  }

  private handlePaste(e: ClipboardEvent): void {
    // Avoid hijacking paste inside form fields
    const target = e.target as Element | null;
    if (target && this.isEditableElement(target)) {
      return;
    }
    
    const files = e.clipboardData?.files ? Array.from(e.clipboardData.files) : [];
    const url = this.extractUrlFromDataTransfer(e.clipboardData);
    
    if (files.length > 0) {
      e.preventDefault();
      this.handleFileDrop(files);
      return;
    }
    
    if (url) {
      e.preventDefault();
      this.handleUrlDrop(url);
    }
  }

  private hasFilesOrUrls(e: DragEvent): boolean {
    if (!e.dataTransfer) return false;
    const types = Array.from(e.dataTransfer.types);
    return types.includes('Files') || types.includes('text/uri-list') || types.includes('text/plain');
  }

  private handleFileDrop(files: File[]): void {
    if (files.length === 0) {
      console.warn('No valid API specification files found in the dropped files.');
      return;
    }
    
    // Check if uploader dialog is already open
    if (this.uploaderDialogService.isDialogOpen()) {
      // Add files to existing dialog within Angular zone
      this.ngZone.run(() => {
        this.uploaderDialogService.addFilesToOpenDialog(files);
      });
      return;
    }
    
    this.ngZone.run(() => {
        this.uploaderDialogService.openArtifactUploader({
            preSelectedFiles: files,
            onClose: () => {
                this.router.navigate(['/services']);
            },
            activeTab: 'upload'
        });
    });
  }

  private handleUrlDrop(url: string): void {
    this.ngZone.run(() => {
      if (this.uploaderDialogService.isDialogOpen()) {
        const patched = this.uploaderDialogService.prefillDownloadUrlInOpenDialog(url);
        if (patched) {
          return;
        }
      }
      
      this.uploaderDialogService.openArtifactDownloadWithUrl(url, {
        onClose: () => {
          this.router.navigate(['/services']);
        }
      });
    });
  }

  private extractUrlFromDataTransfer(dataTransfer: DataTransfer | null): string | null {
    if (!dataTransfer) {
      return null;
    }
    
    const uriList = dataTransfer.getData('text/uri-list');
    if (uriList && this.isHttpUrl(uriList)) {
      return uriList.trim();
    }
    
    const text = dataTransfer.getData('text/plain');
    if (text && this.isHttpUrl(text)) {
      return text.trim();
    }
    
    return null;
  }

  private isEditableElement(element: Element): boolean {
    const tagName = element.tagName.toLowerCase();
    const editableTags = ['input', 'textarea'];
    const isContentEditable = (element as HTMLElement).isContentEditable;
    return editableTags.includes(tagName) || isContentEditable;
  }

  private isHttpUrl(value: string): boolean {
    try {
      const parsed = new URL(value.trim());
      return parsed.protocol === 'http:' || parsed.protocol === 'https:';
    } catch {
      return false;
    }
  }

  destroy(): void {
    document.removeEventListener('dragenter', this.boundHandleDragEnter);
    document.removeEventListener('dragover', this.boundHandleDragOver);
    document.removeEventListener('dragleave', this.boundHandleDragLeave);
    document.removeEventListener('drop', this.boundHandleDrop);
    document.removeEventListener('paste', this.boundHandlePaste);
    
    this.dragOverSubject.complete();
  }
}
