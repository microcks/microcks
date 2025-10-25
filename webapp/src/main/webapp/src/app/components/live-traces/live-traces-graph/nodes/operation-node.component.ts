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
  templateUrl: "./operation-node.component.html",
  styleUrl: "./operation-node.component.css",
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
