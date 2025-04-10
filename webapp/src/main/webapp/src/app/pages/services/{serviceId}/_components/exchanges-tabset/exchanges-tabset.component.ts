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
import { Component, ViewChild, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { TabsetComponent, TabsModule } from 'ngx-bootstrap/tabs';
import { TooltipModule } from 'ngx-bootstrap/tooltip';
import { HighlightAuto } from 'ngx-highlightjs';

import { NotificationService } from '../../../../../components/patternfly-ng/notification';

import { ServiceView, Operation, Exchange, EventMessage, Parameter, RequestResponsePair, UnidirectionalEvent } from '../../../../../models/service.model';
import { ConfigService } from '../../../../../services/config.service';

@Component({
  selector: 'app-exchanges-tabset',
  templateUrl: './exchanges-tabset.component.html',
  styleUrls: ['./exchanges-tabset.component.css'],
  imports: [
    CommonModule,
    FormsModule,
    HighlightAuto,
    TabsModule,
    TooltipModule
  ]
})
export class ExchangesTabsetComponent {

  readonly hlLang: string[] = ['json', 'xml', 'yaml'];

  @ViewChild('tabs', { static: true }) tabs!: TabsetComponent;

  @Input() public item!: Operation;
  @Input() public view!: ServiceView;
  @Input() public resolvedServiceView!: ServiceView;
  @Input() public notificationService!: NotificationService;
  @Input() public config!: ConfigService;
  @Input() public urlType!: string;

  @Input() public isEventTypeService!: () => boolean;
  @Input() public getExchangeName!: (exchange: Exchange) => string;
  @Input() public getExchangeSourceArtifact!: (exchange: Exchange) => string;
  @Input() public hasBinding!: (operation: Operation, binding: string) => boolean;
  @Input() public getBindingProperty!: (operation: Operation, binding: string, property: string) => string | null;
  @Input() public formatMockUrl!: (operation: Operation, dispatchCriteria: string, queryParameters: Parameter[]) => string;
  @Input() public formatAsyncDestination!: (operation: Operation, eventMessage: EventMessage, binding: string) => string;
  @Input() public getDestinationOperationPart!: (peration: Operation, eventMessage: EventMessage) => string;
  @Input() public formatRequestContent!: (requestContent: string) => string;
  @Input() public formatGraphQLVariables!: (requestContent: string) => string;
  @Input() public prettyPrintIfJSON!: (content: string) => string;
  @Input() public formatCurlCmd!: (operation: Operation, exchange: RequestResponsePair) => string;
  @Input() public copyToClipboard!: (url: string) => void;
  @Input() public encodeUrl!: (url: string) => string;
  @Input() public removeVerbInUrl!: (operationName: string) => string;
  @Input() public asyncAPIFeatureEndpoint!: (binding: string) => string;

  public shouldRender(index: number) {
    const activeTab = this.tabs.tabs.filter((tab) =>  tab.active )[0];
    return index == this.tabs.tabs.indexOf(activeTab);
  }

  getReqRespPair(exchange: Exchange): RequestResponsePair {
    return exchange as RequestResponsePair;
  }
  getUnidirEvent(exchange: Exchange): UnidirectionalEvent {
    return exchange as UnidirectionalEvent;
  }
}
