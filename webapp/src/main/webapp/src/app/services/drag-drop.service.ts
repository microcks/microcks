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
export class DragDropService {
  private dragOverSubject = new Subject<boolean>();
  public dragOver$ = this.dragOverSubject.asObservable();

  constructor(
    private router: Router,
    private ngZone: NgZone,
    private uploaderDialogService: UploaderDialogService
  ) {
    this.initializeGlobalDragDrop();
  }

  private initializeGlobalDragDrop(): void {
    // Prevent default drag behaviors on document
    document.addEventListener('dragenter', this.handleDragEnter.bind(this));
    document.addEventListener('dragover', this.handleDragOver.bind(this));
    document.addEventListener('dragleave', this.handleDragLeave.bind(this));
    document.addEventListener('drop', this.handleDrop.bind(this));
  }

  private handleDragEnter(e: DragEvent): void {
    e.preventDefault();
    e.stopPropagation();
    
    if (this.hasFiles(e)) {
      this.dragOverSubject.next(true);
    }
  }

  private handleDragOver(e: DragEvent): void {
    e.preventDefault();
    e.stopPropagation();
    
    if (this.hasFiles(e)) {
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
    
    if (this.hasFiles(e) && e.dataTransfer?.files) {
      const files = Array.from(e.dataTransfer.files);
      this.handleFileDrop(files);
    }
  }

  private hasFiles(e: DragEvent): boolean {
    if (!e.dataTransfer) return false;
    return Array.from(e.dataTransfer.types).includes('Files');
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
      // Navigate to services page and open uploader with files
      this.router.navigate(['/services']).then(() => {
        // Small delay to ensure the page has loaded
        setTimeout(() => {
          this.uploaderDialogService.openArtifactUploader({
            preSelectedFiles: files
          });
        }, 100);
      });
    });
  }

  destroy(): void {
    document.removeEventListener('dragenter', this.handleDragEnter.bind(this));
    document.removeEventListener('dragover', this.handleDragOver.bind(this));
    document.removeEventListener('dragleave', this.handleDragLeave.bind(this));
    document.removeEventListener('drop', this.handleDrop.bind(this));
  }
}
