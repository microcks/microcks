import { Component, Output, EventEmitter, ViewChildren, QueryList } from "@angular/core";

import { ModalDirective } from "ngx-bootstrap";

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
  protected delete(): void {
    this.onDelete.emit(this.objectToDelete);
    this.cancel();
  }

  /**
   * Called when the user clicks "cancel".
   */
  protected cancel(): void {
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