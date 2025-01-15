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


import { NotificationService } from 'patternfly-ng/notification';
import { TabsetComponent } from 'ngx-bootstrap/tabs';
import { ServiceView, Operation } from 'src/app/models/service.model';
import { ConfigService } from 'src/app/services/config.service';


@Component({
  selector: 'app-exchanges-tabset',
  templateUrl: './exchanges-tabset.component.html',
  styleUrls: ['./exchanges-tabset.component.css'],
})
export class ExchangesTabsetComponent {

  readonly hlLang: string[] = ['json', 'xml', 'yaml'];

  @ViewChild('tabs', { static: true }) tabs?: TabsetComponent;

  @Input() public item: Operation;
  @Input() public view: ServiceView;
  @Input() public resolvedServiceView: ServiceView;
  @Input() public notificationService: NotificationService;
  @Input() public config: ConfigService;
  @Input() public urlType: string;

  @Input() public isEventTypeService: () => void;
  @Input() public getExchangeName: () => void;
  @Input() public getExchangeSourceArtifact: () => void;
  @Input() public hasBinding: () => void;
  @Input() public getBindingProperty: () => void;
  @Input() public formatMockUrl: () => void;
  @Input() public formatAsyncDestination: () => void;
  @Input() public getDestinationOperationPart: () => void;
  @Input() public formatRequestContent: () => void;
  @Input() public formatGraphQLVariables: () => void;
  @Input() public prettyPrintIfJSON: () => void;
  @Input() public formatCurlCmd: () => void;
  @Input() public copyToClipboard: () => void;
  @Input() public encodeUrl: () => void;
  @Input() public removeVerbInUrl: () => void;
  @Input() public asyncAPIFeatureEndpoint: () => void;

  public shouldRender(index: number) {
    const activeTab = this.tabs.tabs.filter((tab) =>  tab.active )[0];
    return index == this.tabs.tabs.indexOf(activeTab);
  }
}
