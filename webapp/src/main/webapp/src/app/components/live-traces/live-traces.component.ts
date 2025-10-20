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
import { Component, Input, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ActivatedRoute } from "@angular/router";
import { TraceGroupListComponent } from "./trace-group-list/trace-group-list.component";
import { LiveTracesControlsComponent } from "./live-traces-controls/live-traces-controls.component";
import { LiveTracesManagerService } from "./live-traces-manager.service";

/**
 * Component for displaying live traces in a list format.
 * Uses LiveTracesManagerService for connection, filtering, and deduplication logic.
 */
@Component({
  selector: "app-live-traces",
  standalone: true,
  imports: [CommonModule, TraceGroupListComponent, LiveTracesControlsComponent],
  providers: [LiveTracesManagerService],
  templateUrl: "./live-traces.component.html",
  styleUrls: ["./live-traces.component.css"],
})
export class LiveTracesComponent implements OnInit {
  @Input() set initialServiceName(value: string) {
    if (value) this.manager.serviceName = value;
  }
  @Input() set initialOperationName(value: string) {
    if (value) this.manager.operationName = value;
  }
  @Input() autoConnect = false;
  @Input() set maxItems(value: number) {
    this.manager.maxItems = value;
  }
  @Input() allowCustomization = false;

  constructor(
    public manager: LiveTracesManagerService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    // Check if allowCustomization is set in route data
    this.route.data.subscribe((data) => {
      if (data["allowCustomization"] !== undefined) {
        this.allowCustomization = data["allowCustomization"];
      }
    });

    if (this.autoConnect) {
      this.manager.connect();
    }
  }
}
