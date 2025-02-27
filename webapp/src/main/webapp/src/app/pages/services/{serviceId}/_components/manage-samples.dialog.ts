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
import { Component, EventEmitter, OnInit, Output } from '@angular/core';

import { BsModalRef } from 'ngx-bootstrap/modal';
import { ListConfig, ListEvent } from 'patternfly-ng/list';

import { AICopilotService } from '../../../../services/aicopilot.service';
import { Exchange, RequestResponsePair, ServiceType, ServiceView, UnidirectionalEvent } from '../../../../models/service.model';

@Component({
  selector: 'app-manage-samples-dialog',
  templateUrl: './manage-samples.dialog.html',
  styleUrls: ['./manage-samples.dialog.css'],
})
export class ManageSamplesDialogComponent implements OnInit {
  @Output() cleanupSelectionAction = new EventEmitter<any>();

  closeBtnName: string;
  serviceView: ServiceView;
  operationsWithAISamples: any[] = [];
  operationsListConfig: ListConfig;

  selectedExchanges: any = {};

  constructor(
    private copilotSvc: AICopilotService,
    public bsModalRef: BsModalRef
  ) {}

  ngOnInit() {
    this.operationsListConfig = {
      dblClick: false,
      emptyStateConfig: null,
      multiSelect: false,
      selectItems: false,
      selectionMatchProp: 'name',
      showCheckbox: false,
      showRadioButton: false,
      useExpandItems: true,
      hideClose: true
    } as ListConfig;

    this.serviceView.service.operations.forEach(operation => {
      let exchanges = this.getOperationAICopilotExchanges(operation.name);
      if (exchanges.length > 0) {
        this.operationsWithAISamples.push(operation);
        if (this.selectedExchanges[operation.name] == undefined) {
          this.selectedExchanges[operation.name] = {};
        }
        exchanges.forEach(exchange => {
          this.selectedExchanges[operation.name][this.getExchangeName(exchange)] = true;
        });
      }
    });

    this.operationsWithAISamples.forEach(operation => {
      operation.expanded = true;
    });
  }

  public selectAllExchanges(): void {
    this.operationsWithAISamples.forEach(operation => {
      if (this.selectedExchanges[operation.name] == undefined) {
        this.selectedExchanges[operation.name] = {};
      }
      this.getOperationAICopilotExchanges(operation.name).forEach(exchange => {
        this.selectedExchanges[operation.name][this.getExchangeName(exchange)] = true;
      });
    });
  }
  public unselectAllExchanges(): void {
    this.operationsWithAISamples.forEach(operation => {
      this.selectedExchanges[operation.name] = {};
    });
  }
  public cleanupEnabled(): boolean {
    let enabled = false;
    this.operationsWithAISamples.forEach(operation => {
      // Have a quick look at defintiion and keys.
      if (this.selectedExchanges[operation.name] != undefined
        && Object.keys(this.selectedExchanges[operation.name]).length > 0
      ) {
        // Now inspect the values.
        Object.values(this.selectedExchanges[operation.name]).forEach(value => {
          if (value === true) {
            enabled = true;
            return;
          }
        });
      }
    });
    return enabled;
  }
  
  public isEventTypeService(): boolean {
    return (
      this.serviceView.service.type === ServiceType.EVENT ||
      this.serviceView.service.type === ServiceType.GENERIC_EVENT
    );
  }
  public getExchangeName(exchange: Exchange): string {
    if (this.isEventTypeService()) {
      return (exchange as UnidirectionalEvent).eventMessage.name;
    } else {
      return (exchange as RequestResponsePair).request.name;
    }
  }

  public halfOperationExchanges(operationName: string): Exchange[] {
    let exchanges = this.getOperationAICopilotExchanges(operationName);
    return exchanges.slice(0, (exchanges.length / 2) + 1);
  }

  public secondHalfOperationExchanges(operationName: string): Exchange[] {
    let exchanges = this.getOperationAICopilotExchanges(operationName);
    return exchanges.slice((exchanges.length / 2) + 1, exchanges.length);
  }

  public cleanupSelection(): void {
    this.cleanupSelectionAction.emit(this.selectedExchanges);
    this.bsModalRef.hide();
  }

  public exportSelection(): void {
    console.log("exportSelection: " + JSON.stringify(this.selectedExchanges));
  }

  getOperationAICopilotExchanges(operationName: string): Exchange[] {
    let exchanges = this.serviceView.messagesMap[operationName];
    return exchanges.filter(exchange => 
      (exchange.request != undefined && exchange.request.sourceArtifact === 'AI Copilot')
      || (exchange.eventMessage != undefined && exchange.eventMessage.sourceArtifact === 'AI Copilot')
    );
  }
}