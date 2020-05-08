import { Component } from "@angular/core";

import { BsModalRef } from 'ngx-bootstrap/modal/bs-modal-ref.service';

@Component({
  selector: "help-dialog",
  templateUrl: "help-dialog.component.html"
})
export class HelpDialogComponent {

  protected _isOpen: boolean = false;

  constructor(public bsModalRef: BsModalRef) {}

  ngOnInit() {
  }
}