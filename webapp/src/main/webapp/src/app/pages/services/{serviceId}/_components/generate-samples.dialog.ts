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

import { BsModalRef } from 'ngx-bootstrap/modal';
import { HighlightAuto } from 'ngx-highlightjs';

import { AICopilotService } from '../../../../services/aicopilot.service';
import {
  Service,
  ServiceType,
  Exchange,
  RequestResponsePair,
  UnidirectionalEvent,
} from '../../../../models/service.model';
import { TabsModule } from 'ngx-bootstrap/tabs';

@Component({
  selector: 'app-generate-samples-dialog',
  templateUrl: './generate-samples.dialog.html',
  styleUrls: ['./generate-samples.dialog.css'],
  imports: [
    CommonModule,
    FormsModule,
    HighlightAuto,
    TabsModule
  ]
})
export class GenerateSamplesDialogComponent implements OnInit {
  @Output() saveSamplesAction = new EventEmitter<Exchange[]>();

  closeBtnName!: string;
  service!: Service;
  operationName!: string;

  infoMessage?: string;
  errorMessage?: string;
  saveEnabled = false;

  exchanges: Exchange[] = [];
  selectedExchanges: (Exchange | undefined)[] = [];
  exchangesNames: string[] = [];

  constructor(
    private copilotSvc: AICopilotService,
    public bsModalRef: BsModalRef
  ) {}

  ngOnInit() {
    this.getSamplesSuggestions(2);
    /*
    this.exchanges.push({"request": {}, "response": {}});
    this.exchanges.push({"request": {}, "response": {}});
    this.selectedExchanges.push({"request": {}, "response": {}});
    this.infoMessage = 'You need to rename and may unselect samples before saving them';
    */
  }

  isEventTypeService(): boolean {
    return (
      this.service.type === ServiceType.EVENT ||
      this.service.type === ServiceType.GENERIC_EVENT
    );
  }

  getSamplesSuggestions(numberOfSamples: number = 2): void {
    this.copilotSvc
      .getSamplesSuggestions(this.service, this.operationName, numberOfSamples)
      .subscribe({
        next: (res) => {
          if (res.length == 0) {
            this.infoMessage =
              'AI Copilot was not able to analyse your specification and provide samples in specified delay. Please retry later...';
          } else {
            res.forEach((exchange) => {
              if ((exchange as any)['eventMessage'] != undefined) {
                exchange.type = 'unidirEvent';
              } else {
                exchange.type = 'reqRespPair';
              }
            });
            this.exchanges.push(...res);
            this.selectedExchanges.push(...res);
            this.infoMessage =
              'You need to rename and may unselect samples before saving them';
            this.errorMessage = undefined;
          }
        },
        error: (err) => {
          console.log('Observer got an error: ' + JSON.stringify(err));
          this.errorMessage = 'Got an error on server side: ' + err.error;
          this.infoMessage = undefined;
        },
        complete: () => {} //console.log('Observer got a complete notification'),
      });
  }

  getOtherSamples(): void {
    this.infoMessage = undefined;
    this.errorMessage = undefined;
    this.copilotSvc
      .getSamplesSuggestions(this.service, this.operationName, 2)
      .subscribe({
        next: (res) => {
          if (res.length == 0) {
            this.infoMessage =
              'AI Copilot was not able to analyse your specification and provide samples in specified delay. Please retry later...';
          } else {
            res.forEach((exchange) => {
              if ((exchange as any)['eventMessage'] != undefined) {
                exchange.type = 'unidirEvent';
              } else {
                exchange.type = 'reqRespPair';
              }
            });
            this.exchanges.push(...res);
            this.selectedExchanges.push(...res);
            this.infoMessage =
              'You need to rename and may unselect samples before saving them';
            this.errorMessage = undefined;
          }
        },
        error: (err) => {
          console.log('Observer got an error: ' + JSON.stringify(err));
          this.errorMessage = 'Got an error on server side: ' + err.error;
          this.infoMessage = undefined;
        },
        complete: () => {} //console.log('Observer got a complete notification'),
      });
  }

  getExchangeName(index: number): string {
    if (this.exchangesNames[index] === undefined) {
      return 'Sample ' + index;
    }
    return this.exchangesNames[index];
  }

  getReqRespPair(exchange: Exchange): RequestResponsePair {
    return exchange as RequestResponsePair;
  }
  getUnidirEvent(exchange: Exchange): UnidirectionalEvent {
    return exchange as UnidirectionalEvent;
  }

  toggleSelectedExchange(index: number): void {
    if (this.selectedExchanges[index] == undefined) {
      this.selectedExchanges[index] = this.exchanges[index];
    } else {
      this.selectedExchanges[index] = undefined;
    }
    this.isSavingEnabled();
  }

  updateSampleName($event: Event, index: number): void {
    this.exchangesNames[index] = String($event);
    if (!this.isEventTypeService()) {
      // Dissociate request/response pair...
      (this.exchanges[index] as RequestResponsePair).request.name =
        String($event);
      (this.exchanges[index] as RequestResponsePair).response.name =
        String($event);
      (this.selectedExchanges[index] as RequestResponsePair).request.name =
        String($event);
      (this.selectedExchanges[index] as RequestResponsePair).response.name =
        String($event);
    } else {
      // ... from unidirectional event.
      (this.exchanges[index] as UnidirectionalEvent).eventMessage.name =
        String($event);
      (this.selectedExchanges[index] as UnidirectionalEvent).eventMessage.name =
        String($event);
    }
    this.isSavingEnabled();
  }

  isSavingEnabled(): void {
    let missingSomething = false;
    this.selectedExchanges.forEach((exchange, index) => {
      if (exchange != undefined) {
        if (this.exchangesNames[index] == undefined) {
          missingSomething = true;
        }
      }
    });
    this.saveEnabled = !missingSomething && this.selectedExchanges.length > 0;
  }

  saveSamples(): void {
    this.saveSamplesAction.emit(this.selectedExchanges.filter((e) => e != undefined));
    this.bsModalRef.hide();
  }
}
