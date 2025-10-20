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
  templateUrl: "./service-node.component.html",
  styleUrl: "./service-node.component.css",
  imports: [Vflow],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServiceNodeComponent extends CustomDynamicNodeComponent<ServiceNode> {}
