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
import {
  Component,
  Input,
  Output,
  EventEmitter,
  ChangeDetectorRef,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { LiveTracesComponent } from "../live-traces/live-traces.component";

@Component({
  selector: "app-collapsible-live-traces",
  standalone: true,
  imports: [CommonModule, LiveTracesComponent],
  templateUrl: "./collapsible-live-traces.component.html",
  styleUrls: ["./collapsible-live-traces.component.css"],
})
export class CollapsibleLiveTracesComponent {
  defaultPanelWidth: number = 700;
  @Input() serviceName!: string;
  @Input() operationName!: string;
  @Input() isCollapsed: boolean = true;
  @Input() panelWidth: number = this.defaultPanelWidth;

  @Output() isCollapsedChange = new EventEmitter<boolean>();
  @Output() panelWidthChange = new EventEmitter<number>();

  private resizing = false;
  private startX = 0;
  private startWidth = 0;

  constructor(private ref: ChangeDetectorRef) {}

  toggleCollapse(): void {
    this.isCollapsed = !this.isCollapsed;
    this.isCollapsedChange.emit(this.isCollapsed);
  }

  closePanel(): void {
    this.isCollapsed = true;
    this.isCollapsedChange.emit(this.isCollapsed);
  }

  openPanel(): void {
    this.isCollapsed = false;
    this.isCollapsedChange.emit(this.isCollapsed);
  }

  startResize(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();

    this.resizing = true;
    this.startX = event.clientX;
    this.startWidth = this.panelWidth || this.defaultPanelWidth;

    const mouseMoveListener = (e: MouseEvent) => this.onResize(e);
    const mouseUpListener = () =>
      this.stopResize(mouseMoveListener, mouseUpListener);

    document.addEventListener("mousemove", mouseMoveListener);
    document.addEventListener("mouseup", mouseUpListener);
  }

  private onResize(event: MouseEvent): void {
    if (!this.resizing) return;

    const deltaX = this.startX - event.clientX;
    let newWidth = this.startWidth + deltaX;

    // Constrain width between 300px and 1200px
    newWidth = Math.max(300, Math.min(1200, newWidth));

    this.panelWidth = newWidth;
    this.panelWidthChange.emit(this.panelWidth);
    this.ref.detectChanges();
  }

  private stopResize(
    mouseMoveListener: (e: MouseEvent) => void,
    mouseUpListener: () => void,
  ): void {
    document.removeEventListener("mousemove", mouseMoveListener);
    document.removeEventListener("mouseup", mouseUpListener);
    this.resizing = false;
  }
}
