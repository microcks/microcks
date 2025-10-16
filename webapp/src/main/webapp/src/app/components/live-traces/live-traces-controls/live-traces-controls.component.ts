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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";

/**
 * Reusable controls component for live traces visualizations.
 * Provides UI for service/operation selection, filtering, and connection management.
 */
@Component({
  selector: "app-live-traces-controls",
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: "./live-traces-controls.component.html",
  styleUrls: ["./live-traces-controls.component.css"],
})
export class LiveTracesControlsComponent {
  @Input() serviceName = ".*";
  @Input() operationName = ".*";
  @Input() clientIpFilter = ".*";
  @Input() connected = false;
  @Input() isLoading = false;
  @Input() error = "";
  @Input() allowCustomization = false;
  @Input() traceCount = 0;
  @Input() totalEvents = 0;

  @Output() serviceNameChange = new EventEmitter<string>();
  @Output() operationNameChange = new EventEmitter<string>();
  @Output() clientIpFilterChange = new EventEmitter<string>();
  @Output() connectClick = new EventEmitter<void>();
  @Output() disconnectClick = new EventEmitter<void>();
  @Output() prefillClick = new EventEmitter<void>();
  @Output() clearClick = new EventEmitter<void>();

  onServiceNameChange(value: string): void {
    this.serviceNameChange.emit(value);
  }

  onOperationNameChange(value: string): void {
    this.operationNameChange.emit(value);
  }

  onClientIpFilterChange(value: string): void {
    this.clientIpFilterChange.emit(value);
  }

  onConnect(): void {
    this.connectClick.emit();
  }

  onDisconnect(): void {
    this.disconnectClick.emit();
  }

  onPrefill(): void {
    this.prefillClick.emit();
  }

  onClear(): void {
    this.clearClick.emit();
  }
}
