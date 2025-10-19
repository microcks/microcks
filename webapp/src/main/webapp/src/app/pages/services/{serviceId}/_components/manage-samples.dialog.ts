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
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { BsDropdownModule } from 'ngx-bootstrap/dropdown';
import { BsModalRef } from 'ngx-bootstrap/modal';

import { ListConfig, ListModule } from '../../../../components/patternfly-ng/list';

import { Exchange, RequestResponsePair, ServiceType, ServiceView, UnidirectionalEvent } from '../../../../models/service.model';
import { IAuthenticationService } from '../../../../services/auth.service';

@Component({
  selector: 'app-manage-samples-dialog',
  templateUrl: './manage-samples.dialog.html',
  styleUrls: ['./manage-samples.dialog.css'],
  imports: [
    CommonModule,
    BsDropdownModule,
    FormsModule,
    ListModule
  ]
})
export class ManageSamplesDialogComponent implements OnInit {

  @Output() cleanupSelectionAction = new EventEmitter<Record<string, Record<string, boolean>>>();

  closeBtnName!: string;
  serviceView!: ServiceView;
  samplesMode: string = 'AI';
  operationsWithSamples: any[] = [];
  operationsListConfig!: ListConfig;

  //selectedExchanges: any = {};
  selectedExchanges: Record<string, Record<string, boolean>> = {};
  exportFormat: string = 'APIExamples';

  constructor(
    private authService: IAuthenticationService,
    public bsModalRef: BsModalRef
  ) {}

  ngOnInit() {
    this.operationsListConfig = {
      dblClick: false,
      //emptyStateConfig: null,
      multiSelect: false,
      selectItems: false,
      selectionMatchProp: 'name',
      showCheckbox: false,
      showRadioButton: false,
      useExpandItems: true,
      hideClose: true
    } as ListConfig;

    this.serviceView.service.operations.forEach(operation => {
      let exchanges = (this.samplesMode === 'AI') ? this.getOperationAICopilotExchanges(operation.name) 
        : this.getOperationAllExchanges(operation.name);
      if (exchanges.length > 0) {
        this.operationsWithSamples.push(operation);
        if (this.selectedExchanges[operation.name] == undefined) {
          this.selectedExchanges[operation.name] = {};
        }
        exchanges.forEach(exchange => {
          this.selectedExchanges[operation.name][this.getExchangeName(exchange)] = true;
        });
      }
    });

    this.operationsWithSamples.forEach(operation => {
      operation.expanded = true;
    });
  }

  public selectAllExchanges(): void {
    this.operationsWithSamples.forEach(operation => {
      if (this.selectedExchanges[operation.name] == undefined) {
        this.selectedExchanges[operation.name] = {};
      }
      this.getOperationAICopilotExchanges(operation.name).forEach(exchange => {
        this.selectedExchanges[operation.name][this.getExchangeName(exchange)] = true;
      });
    });
  }
  public unselectAllExchanges(): void {
    this.operationsWithSamples.forEach(operation => {
      this.selectedExchanges[operation.name] = {};
    });
  }
  public cleanupEnabled(): boolean {
    let enabled = false;
    if (this.samplesMode === 'AI') {
      this.operationsWithSamples.forEach(operation => {
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
    }
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
    let exchanges = (this.samplesMode === 'AI') ? this.getOperationAICopilotExchanges(operationName) 
        : this.getOperationAllExchanges(operationName);
    return exchanges.slice(0, (exchanges.length / 2) + 1);
  }

  public secondHalfOperationExchanges(operationName: string): Exchange[] {
    let exchanges = (this.samplesMode === 'AI') ? this.getOperationAICopilotExchanges(operationName) 
        : this.getOperationAllExchanges(operationName);
    return exchanges.slice((exchanges.length / 2) + 1, exchanges.length);
  }

  public cleanupSelection(): void {
    if (this.samplesMode === 'AI') {
      // Remove exchanges that are not selected.
      this.operationsWithSamples.forEach(operation => {
        Object.keys(this.selectedExchanges[operation.name]).forEach(exchangeName => {
          if (this.selectedExchanges[operation.name][exchangeName] === false) {
            delete this.selectedExchanges[operation.name][exchangeName];
          }
        });
      });
      this.cleanupSelectionAction.emit(this.selectedExchanges);
      this.bsModalRef.hide();
    }
  }

  public exportSelection(): void {
    // Remove exchanges that are not selected.
    this.operationsWithSamples.forEach(operation => {
      Object.keys(this.selectedExchanges[operation.name]).forEach(exchangeName => {
        if (this.selectedExchanges[operation.name][exchangeName] === false) {
          delete this.selectedExchanges[operation.name][exchangeName];
        }
      });
    });
    let exchangeSelection: { serviceId: string; exchanges: Record<string, string[]> } = {
      serviceId: this.serviceView.service.id,
      exchanges: {}
    };
    Object.keys(this.selectedExchanges).forEach((operationName) => {
      exchangeSelection.exchanges[operationName] = [];
      Object.keys(this.selectedExchanges[operationName]).forEach((exchangeName) => {
        exchangeSelection.exchanges[operationName]!.push(exchangeName);
      });
    });

    // Now download the selected exchanges.
    let downloadPath = '/api/copilot/samples/' + this.serviceView.service.id + '/export?format=' + this.exportFormat;
    
    // Just opening a window with the download path is not working
    // because Authorization header is not sent.
    //window.open(downloadPath, '_blank', '');

    // So we have to use XMLHttpRequest to send Authorization header and get the file
    // before triggering the Save as dialog by simulating a click on a link.
    const xhr = new XMLHttpRequest();
    xhr.open('POST', location.origin + downloadPath, true);
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.setRequestHeader('Authorization', 'Bearer ' + this.authService.getAuthenticationSecret());
    xhr.onreadystatechange = function() {
      if (xhr.readyState == 4) {
        if (xhr.status == 200) {
          const blob = new Blob([xhr.response], { type: 'text/plain' });
          const url = window.URL.createObjectURL(blob);

          var a = document.createElement("a");
          document.body.appendChild(a);
          a.href = url;
          a.download = 'api-examples.yaml';
          a.click();

          window.URL.revokeObjectURL(url);
        } else {
          alert('Problem while retrieving APIExamples export');
        }
      }
    };
    xhr.send(JSON.stringify(exchangeSelection));
  }

  getOperationAllExchanges(operationName: string): Exchange[] {
    return this.serviceView.messagesMap[operationName];
  }

  getOperationAICopilotExchanges(operationName: string): Exchange[] {
    let exchanges = this.serviceView.messagesMap[operationName];
    return exchanges.filter(exchange => 
      this.isEventTypeService() ?
        (exchange as UnidirectionalEvent).eventMessage.sourceArtifact === 'AI Copilot'
        : (exchange as RequestResponsePair).request.sourceArtifact === 'AI Copilot'
    );
  }
}