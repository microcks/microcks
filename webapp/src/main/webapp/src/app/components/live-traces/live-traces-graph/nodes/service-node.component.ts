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
import { Component, ChangeDetectionStrategy } from "@angular/core";
import { Vflow, CustomDynamicNodeComponent } from "ngx-vflow";

export interface ServiceNode {
  label: string;
  kind: string;
}

/**
 * Node component representing a service in the graph.
 */
@Component({
  selector: "app-service-node",
  template: `
    <div class="service-node">
      <div class="node-icon">⚙️</div>
      <div class="node-label">{{ data()?.label }}</div>
      <handle type="source" position="right" />
      <handle type="target" position="left" />
    </div>
  `,
  styles: [
    `
      .service-node {
        width: 140px;
        height: 80px;
        border: 2px solid #1565c0;
        border-radius: 8px;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        background: linear-gradient(135deg, #e3f2fd 0%, #bbdefb 100%);
        box-shadow: 0 2px 8px rgba(21, 101, 192, 0.2);
        transition: all 0.2s ease;
        cursor: pointer;
      }

      .service-node:hover {
        box-shadow: 0 4px 12px rgba(21, 101, 192, 0.3);
      }

      .node-icon {
        font-size: 28px;
        margin-bottom: 4px;
      }

      .node-label {
        font-size: 11px;
        font-weight: 600;
        color: #0d47a1;
        text-align: center;
        max-width: 120px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    `,
  ],
  imports: [Vflow],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServiceNodeComponent extends CustomDynamicNodeComponent<ServiceNode> {}
