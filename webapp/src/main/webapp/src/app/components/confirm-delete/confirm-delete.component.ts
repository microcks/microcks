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

import { Component, Output, EventEmitter, ViewChildren, QueryList, Provider } from '@angular/core';
import { NgIf } from '@angular/common';

import { ModalDirective, ModalModule } from 'ngx-bootstrap/modal';

@Component({
  selector: 'app-confirm-delete-dialog',
  templateUrl: 'confirm-delete.component.html',
  imports: [NgIf, ModalModule]
})
export class ConfirmDeleteDialogComponent {

  @Output() delete: EventEmitter<any> = new EventEmitter<any>();

  @ViewChildren('confirmDeleteModal') confirmDeleteModal?: QueryList<ModalDirective>;

  private objectToDelete: any;
  protected isOpenBF = false;

  set isOpen(isOpen: boolean) {
    this.isOpenBF = isOpen;
  }

  /**
   * Returns true if the dialog is open.
   */
  get isOpen() {
    return this.isOpenBF;
  }

  /**
   * Called to open the dialog.
   */
  public open(objectToDelete: any): void {
    this.isOpen = true;
    this.objectToDelete = objectToDelete;
    this.confirmDeleteModal?.changes.subscribe(thing => {
      if (this.confirmDeleteModal?.first) {
        this.confirmDeleteModal.first.show();
      }
    });
  }

  /**
   * Called to close the dialog.
   */
  public close(): void {
    this.isOpen = false;
  }

  /**
   * Called when the user clicks "Yes".
   */
  public handleDelete(): void {
    this.delete.emit(this.objectToDelete);
    this.cancel();
  }

  /**
   * Called when the user clicks "cancel".
   */
  public cancel(): void {
    this.objectToDelete = false;
    this.confirmDeleteModal?.first.hide();
  }
}
