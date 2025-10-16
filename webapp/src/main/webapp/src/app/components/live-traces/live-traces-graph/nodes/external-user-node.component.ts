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

export interface ExternalUserNode {
  label: string;
  kind: string;
}

/**
 * Node component representing the external user (root of the graph).
 */
@Component({
  selector: "app-external-user-node",
  template: `
    <div class="external-user-node">
      <div class="node-icon">👤</div>
      <div class="node-label">External User</div>
      <handle type="source" position="right" />
    </div>
  `,
  styles: [
    `
      .external-user-node {
        width: 140px;
        height: 80px;
        border: 2px solid #2e7d32;
        border-radius: 8px;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        background: linear-gradient(135deg, #e8f5e9 0%, #c8e6c9 100%);
        box-shadow: 0 2px 8px rgba(46, 125, 50, 0.2);
        transition: all 0.2s ease;
        cursor: pointer;
      }

      .external-user-node:hover {
        box-shadow: 0 4px 12px rgba(46, 125, 50, 0.3);
      }

      .node-icon {
        font-size: 28px;
        margin-bottom: 4px;
      }

      .node-label {
        font-size: 12px;
        font-weight: 600;
        color: #1b5e20;
        text-align: center;
      }
    `,
  ],
  imports: [Vflow],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExternalUserNodeComponent extends CustomDynamicNodeComponent<ExternalUserNode> {}
