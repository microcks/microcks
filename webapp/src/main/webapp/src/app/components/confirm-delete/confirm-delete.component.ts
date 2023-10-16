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
import { Component, Output, EventEmitter, ViewChildren, QueryList } from "@angular/core";

import { ModalDirective } from "ngx-bootstrap/modal";

@Component({
  selector: "confirm-delete-dialog",
  templateUrl: "confirm-delete.component.html"
})
export class ConfirmDeleteDialogComponent {

  @Output() onDelete: EventEmitter<any> = new EventEmitter<any>();

  @ViewChildren("confirmDeleteModal") confirmDeleteModal: QueryList<ModalDirective>;

  private objectToDelete: any;
  protected _isOpen: boolean = false;

  /**
   * Called to open the dialog.
   */
  public open(objectToDelete: any): void {
    this._isOpen = true;
    this.objectToDelete = objectToDelete;
    this.confirmDeleteModal.changes.subscribe(thing => {
      if (this.confirmDeleteModal.first) {
        this.confirmDeleteModal.first.show();
      }
    });
  }

  /**
   * Called to close the dialog.
   */
  public close(): void {
    this._isOpen = false;
  }

  /**
   * Called when the user clicks "Yes".
   */
  public delete(): void {
    this.onDelete.emit(this.objectToDelete);
    this.cancel();
  }

  /**
   * Called when the user clicks "cancel".
   */
  public cancel(): void {
    this.objectToDelete = false;
    this.confirmDeleteModal.first.hide();
  }

  /**
   * Returns true if the dialog is open.
   */
  public isOpen(): boolean {
    return this._isOpen;
  }
}