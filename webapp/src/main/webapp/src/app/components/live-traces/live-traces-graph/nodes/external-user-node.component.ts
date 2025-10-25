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
  clientIpFilter?: string;
}

/**
 * Node component representing the external user (root of the graph).
 */
@Component({
  selector: "app-external-user-node",
  templateUrl: "./external-user-node.component.html",
  styleUrl: "./external-user-node.component.css",
  imports: [Vflow],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExternalUserNodeComponent extends CustomDynamicNodeComponent<ExternalUserNode> {

  clientIpFilter(): string | undefined {
    return this.data()?.clientIpFilter !== ".*" ? this.data()?.clientIpFilter : undefined;
  }
}
