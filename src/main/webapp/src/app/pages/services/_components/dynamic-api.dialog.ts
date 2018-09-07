import { Component, EventEmitter, OnInit, Output } from '@angular/core';

import { BsModalRef } from 'ngx-bootstrap/modal/bs-modal-ref.service';

import { Api } from '../../../models/service.model';


@Component({
  selector: 'dynamic-api-dialog',
  templateUrl: './dynamic-api.dialog.html',
  styleUrls: ['./dynamic-api.dialog.css']
})
export class DynamicAPIDialogComponent implements OnInit {
  @Output() createAction = new EventEmitter<Api>();
  
  title: string;
  closeBtnName: string;
  api: Api = new Api();
  
  constructor(public bsModalRef: BsModalRef) {}
 
  ngOnInit() {
  }

  createDynamicApi(api: Api) {
    console.log("[DynamicAPIDialogComponent createDynamicApi]");
    this.createAction.emit(this.api);
    this.bsModalRef.hide();
  }
}