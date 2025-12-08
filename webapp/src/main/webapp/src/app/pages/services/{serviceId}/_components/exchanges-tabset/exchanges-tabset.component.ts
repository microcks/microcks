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
import { TooltipConfig, TooltipModule } from 'ngx-bootstrap/tooltip';
import { HighlightAuto } from 'ngx-highlightjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

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
    TooltipModule,
    RouterLink
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
  @Input() public copyToClipboard!: (url: string, what?: string) => void;
  @Input() public encodeUrl!: (url: string) => string;
  @Input() public removeVerbInUrl!: (operationName: string) => string;
  @Input() public asyncAPIFeatureEndpoint!: (binding: string) => string;

  // Name of the exchange tab to activate on init (deep-linking)
  @Input() public activeExchangeName: string | null | undefined;

  constructor(private router: Router, private route: ActivatedRoute, tooltipConfig: TooltipConfig) {
    // Bind tooltip to body container to have it properly positioned
    tooltipConfig.adaptivePosition = true;
    tooltipConfig.container = 'body';
    tooltipConfig.placement = 'top';
  }

  public shouldRender(index: number) {
    const activeTab = this.tabs.tabs.find((tab) => tab.active);
    if (!activeTab) {
      // Fallback: show the first tab content if none is marked active
      return index === 0;
    }
    return index === this.tabs.tabs.indexOf(activeTab);
  }

  getReqRespPair(exchange: Exchange): RequestResponsePair {
    return exchange as RequestResponsePair;
  }
  getUnidirEvent(exchange: Exchange): UnidirectionalEvent {
    return exchange as UnidirectionalEvent;
  }

  getGRPCMediaType(exchange: RequestResponsePair): string | null {
    if (exchange.response.mediaType && !exchange.response.mediaType.startsWith('application/json')) {
      return exchange.response.mediaType;
    }
    if (exchange.response.status == '200' || exchange.response.status == '0') {
      return 'application/x-protobuf';
    }
    return null;
  }

  private normalizeName(val: string | null | undefined): string | null {
    if (val == null) return null;
    try {
      return val
        .toString()
        .trim()
        .toLowerCase();
    } catch {
      return val as any;
    }
  }

  // Declarative active check for template binding
  public isActive(exchange: Exchange): boolean {
    const target = this.normalizeName(this.activeExchangeName);
    if (target == null) return false;
    return this.normalizeName(this.getExchangeName(exchange)) === target;
  }

  public hasAnyMatch(): boolean {
    const target = this.normalizeName(this.activeExchangeName);
    if (target == null) return false;
    try {
      const exchanges = this.view.messagesMap[this.item.name] as Exchange[];
      return exchanges?.some((ex) => this.normalizeName(this.getExchangeName(ex)) === target) || false;
    } catch {
      return false;
    }
  }

  // Update URL when a tab becomes active
  public onTabSelected(exchange: Exchange): void {
    const exchangeName = this.getExchangeName(exchange);
    const operationName = this.item.name;
    const qp = this.route.snapshot.queryParamMap;
    const curOp = qp.get('operation');
    const curEx = qp.get('exchange');
    if (curOp === operationName && curEx === exchangeName) return;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { operation: operationName, exchange: exchangeName },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  /** Build an absolute URL pointing to the provided exchange (deep link). */
  public buildExchangeLink(exchange: Exchange): string {
    try {
      const tree = this.router.createUrlTree([], {
        relativeTo: this.route,
        queryParams: { operation: this.item.name, exchange: this.getExchangeName(exchange) },
        queryParamsHandling: 'merge'
      });
      const relative = this.router.serializeUrl(tree);
      const base = window.location.origin;
      return base + '/#' + relative;
    } catch {
      return '';
    }
  }

  /** Copy the deep link to clipboard */
  public copyExchangeLink(exchange: Exchange): void {
    const link = this.buildExchangeLink(exchange);
    if (link) {
      this.copyToClipboard(link, 'Mock link');
    }
  }
}
