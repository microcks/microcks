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
import { Component, ChangeDetectionStrategy, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { Vflow, CustomDynamicNodeComponent } from "ngx-vflow";
import { Observable } from "rxjs";
import { ReadableSpan } from "@opentelemetry/sdk-trace-base";
import { TraceGroupListComponent } from "../../trace-group-list/trace-group-list.component";

export interface OperationNode {
  label: string;
  kind: string;
  serviceName: string;
  operationName: string;
  traces$?: Observable<ReadableSpan[][]>;
}

/**
 * Node component representing an operation in the graph.
 * Shows operation name, service name, and trace count.
 */
@Component({
  selector: "app-operation-node",
  template: `
    <div class="operation-node" [class.expanded]="isExpanded()">
      <div class="node-header" (click)="toggleExpand()">
        <div class="expand-icon">{{ isExpanded() ? "▼" : "▶" }}</div>
        <div class="node-icon">⚡</div>
        <div class="node-info">
          <div class="node-label">{{ data()?.label }}</div>
          <div class="trace-count">
            {{ (data()?.traces$ | async)?.length || 0 }} trace(s)
          </div>
        </div>
      </div>

      @if (isExpanded() && data()?.traces$) {
        <div class="traces-container" (wheel)="onWheel($event)">
          <app-trace-group-list [traces]="(data()!.traces$ | async) || []">
          </app-trace-group-list>
        </div>
      }

      <handle type="target" position="left" />
    </div>
  `,
  styles: [
    `
      .operation-node {
        width: 320px;
        max-height: 380px;
        border: 2px solid #ef6c00;
        border-radius: 8px;
        display: block;
        background: #ffffff;
        overflow: auto;
        padding: 0;
        transition: width 120ms ease-in-out;
        box-shadow: 0 2px 8px rgba(239, 108, 0, 0.2);
      }

      .operation-node.expanded {
        width: 800px;
        overflow: visible;
      }

      .operation-node:hover {
        box-shadow: 0 4px 12px rgba(239, 108, 0, 0.3);
      }

      .node-header {
        display: flex;
        align-items: center;
        gap: 8px;
        cursor: pointer;
        user-select: none;
        padding: 8px;
        background: linear-gradient(135deg, #fff3e0 0%, #ffe0b2 100%);
        border-radius: 6px 6px 0 0;
      }

      .expand-icon {
        font-size: 12px;
        color: #ef6c00;
        flex-shrink: 0;
        width: 16px;
        text-align: center;
      }

      .node-icon {
        font-size: 24px;
        flex-shrink: 0;
      }

      .node-info {
        flex: 1;
        min-width: 0;
      }

      .node-label {
        font-size: 12px;
        font-weight: 700;
        color: #e65100;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .node-service {
        font-size: 10px;
        color: #f57c00;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        margin-top: 2px;
      }

      .trace-count {
        font-size: 9px;
        color: #ef6c00;
        font-weight: 600;
        margin-top: 2px;
      }

      .traces-container {
        padding: 8px;
        background: #ffffff;
        max-height: 350px;
        overflow-y: auto;
        border-radius: 0 0 6px 6px;
      }

      .traces-container::-webkit-scrollbar {
        width: 6px;
      }

      .traces-container::-webkit-scrollbar-track {
        background: #f5f5f5;
        border-radius: 3px;
      }

      .traces-container::-webkit-scrollbar-thumb {
        background: #ef6c00;
        border-radius: 3px;
      }

      .traces-container::-webkit-scrollbar-thumb:hover {
        background: #e65100;
      }
    `,
  ],
  imports: [Vflow, CommonModule, TraceGroupListComponent],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OperationNodeComponent extends CustomDynamicNodeComponent<OperationNode> {
  isExpanded = signal(false);

  toggleExpand(): void {
    this.isExpanded.update((v) => !v);
  }

  onWheel(event: WheelEvent): void {
    // Stop the wheel event from propagating to the vflow canvas
    // This allows scrolling within the traces container without zooming the graph
    event.stopPropagation();
  }
}
