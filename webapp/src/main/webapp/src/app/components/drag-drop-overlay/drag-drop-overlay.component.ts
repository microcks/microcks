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
import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { DragDropService } from '../../services/drag-drop.service';
import { UploaderDialogService } from '../../services/uploader-dialog.service';

@Component({
  selector: 'app-drag-drop-overlay',
  templateUrl: './drag-drop-overlay.component.html',
  styleUrls: ['./drag-drop-overlay.component.css'],
  imports: [CommonModule]
})
/**
 * Component that displays a drag-and-drop overlay when files are dragged over the application.
 * 
 * Listens to drag events via `DragDropService` and shows the overlay only if the uploader dialog is not open.
 * 
 * @remarks
 * - Subscribes to drag-over events on initialization and unsubscribes on destruction to prevent memory leaks.
 * - The overlay visibility is controlled by the `isDragOver` property.
 * 
 */
export class DragDropOverlayComponent implements OnInit, OnDestroy {
  isDragOver = false;
  private subscription?: Subscription;

  constructor(
    private dragDropService: DragDropService,
    private uploaderDialogService: UploaderDialogService
  ) {}

  ngOnInit(): void {
    this.subscription = this.dragDropService.dragOver$.subscribe(
      isDragOver => {
        // Only show overlay if dialog is not already open
        this.isDragOver = isDragOver && !this.uploaderDialogService.isDialogOpen();
      }
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
