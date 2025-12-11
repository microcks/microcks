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
import { Component, Input } from "@angular/core";
import { CommonModule } from "@angular/common";
import { Attributes, HrTime } from "@opentelemetry/api";
import { ReadableSpan, TimedEvent } from "@opentelemetry/sdk-trace-base";

@Component({
  selector: "app-trace-group-list",
  standalone: true,
  imports: [CommonModule],
  templateUrl: "./trace-group-list.component.html",
  styleUrls: ["./trace-group-list.component.css"],
})
export class TraceGroupListComponent {
  @Input() traces: ReadableSpan[][] = [];

  openedTraces = new Set<string>();
  expandedAttributes = new Set<string>();
  readonly MAX_VALUE_LENGTH = 200; // Characters threshold for collapsible content

  trackByTrace(index: number, trace: ReadableSpan[]): string {
    return trace[0].spanContext().traceId;
  }

  trackByEvent(
    index: number,
    e: { event: TimedEvent; spanId: string; spanName: string; traceId: string },
  ): string {
    const t = e.event.time
      ? `${e.event.time[0]}-${e.event.time[1]}`
      : "no-time";

    return `${e.traceId}/${e.spanId}/${e.event.name}/${t}`;
  }

  trackByAttribute(index: number, kv: { key: string; value: any }): string {
    return kv.key;
  }

  getEventTitle(e: TimedEvent): string {
    const raw = (e.attributes as any)?.["message"];
    if (raw === undefined || raw === null) return e.name;
    if (typeof raw === "string") return raw;
    try {
      return JSON.stringify(raw);
    } catch {
      return String(raw);
    }
  }

  getEvents(trace: ReadableSpan[]): {
    event: TimedEvent;
    spanId: string;
    spanName: string;
    traceId: string;
  }[] {
    const events: {
      event: TimedEvent;
      spanId: string;
      spanName: string;
      traceId: string;
    }[] = [];
    for (const span of trace) {
      for (const event of span.events || []) {
        events.push({
          event,
          spanId: span.spanContext().spanId,
          spanName: span.name,
          traceId: span.spanContext().traceId,
        });
      }
    }
    // Sort events by time
    events.sort((a, b) => {
      if (!a.event.time && !b.event.time) return 0;
      if (!a.event.time) return -1;
      if (!b.event.time) return 1;
      if (a.event.time[0] !== b.event.time[0])
        return a.event.time[0] - b.event.time[0];
      return a.event.time[1] - b.event.time[1];
    });
    return events;
  }

  hasNonMessageAttributes(attrs?: Attributes): boolean {
    if (!attrs) return false;
    for (const k in attrs) {
      if (Object.prototype.hasOwnProperty.call(attrs, k) && k !== "message")
        return true;
    }
    return false;
  }

  getNonMessageAttributes(attrs?: Attributes): { key: string; value: any }[] {
    const result: { key: string; value: any }[] = [];
    if (!attrs) return result;
    for (const k in attrs) {
      if (Object.prototype.hasOwnProperty.call(attrs, k) && k !== "message") {
        result.push({ key: k, value: attrs[k] });
      }
    }
    return result;
  }

  formatHrTime(t?: HrTime): string {
    if (!t) return "";
    const [sec, nsec] = t;
    if (
      !Number.isFinite(sec) ||
      !Number.isFinite(nsec) ||
      (sec === 0 && nsec === 0)
    )
      return "";
    const msEpoch = sec * 1000 + Math.floor(nsec / 1_000_000);
    const d = new Date(msEpoch);
    const hh = String(d.getHours()).padStart(2, "0");
    const mm = String(d.getMinutes()).padStart(2, "0");
    const ss = String(d.getSeconds()).padStart(2, "0");
    const ms = String(d.getMilliseconds()).padStart(3, "0");
    return `${hh}:${mm}:${ss}.${ms}`;
  }

  getTraceError(trace: ReadableSpan[]): string | null {
    for (const span of trace) {
      if (span.status && span.status.code === 2) {
        // 2 = ERROR
        return span.status.message || "Error";
      }
    }
    return null;
  }

  isTraceOpen(trace: ReadableSpan[]): boolean {
    const traceId = trace[0].spanContext().traceId;
    return this.openedTraces.has(traceId);
  }
  openTrace(trace: ReadableSpan[]): void {
    const traceId = trace[0].spanContext().traceId;
    this.openedTraces.add(traceId);
  }
  closeTrace(trace: ReadableSpan[]): void {
    const traceId = trace[0].spanContext().traceId;
    this.openedTraces.delete(traceId);
  }
  toggleTrace(trace: ReadableSpan[]): void {
    const traceId = trace[0].spanContext().traceId;
    if (this.openedTraces.has(traceId)) {
      this.closeTrace(trace);
    } else {
      this.openTrace(trace);
    }
  }

  getStartTime(trace: ReadableSpan[]): HrTime {
    if (!trace || trace.length === 0) return [0, 0];
    let start: HrTime | null = null;
    for (const span of trace) {
      if (span.startTime) {
        start = start
          ? start[0] < span.startTime[0]
            ? start
            : span.startTime
          : span.startTime;
      }
    }
    if (!start) return [0, 0];
    return start;
  }
  getEndTime(trace: ReadableSpan[]): HrTime {
    if (!trace || trace.length === 0) return [0, 0];
    let end: HrTime | null = null;
    for (const span of trace) {
      if (span.endTime) {
        end = end
          ? end[0] > span.endTime[0]
            ? end
            : span.endTime
          : span.endTime;
      }
    }
    if (!end) return [0, 0];
    return end;
  }

  getTraceId(trace: ReadableSpan[]): string {
    return trace[0].spanContext().traceId;
  }

  // Format value for display, handling JSON, strings with \n, etc.
  formatAttributeValue(value: any): string {
    if (value === undefined || value === null) return 'null';
    if (typeof value === 'string') return value;
    try {
      return JSON.stringify(value, null, 2);
    } catch {
      return String(value);
    }
  }

  // Check if the value should be collapsible (large or multiline)
  isValueLarge(value: any): boolean {
    const formatted = this.formatAttributeValue(value);
    return formatted.length > this.MAX_VALUE_LENGTH || formatted.includes('\n');
  }

  // Get truncated preview of a large value
  getValuePreview(value: any): string {
    const formatted = this.formatAttributeValue(value);
    if (formatted.length <= this.MAX_VALUE_LENGTH && !formatted.includes('\n')) {
      return formatted;
    }

    // For multiline, show first non empty line truncated
    const firstLine = formatted.split('\n').find(line => line.trim().length > 0);
    if (firstLine && firstLine.length > this.MAX_VALUE_LENGTH) {
      return firstLine.substring(0, this.MAX_VALUE_LENGTH) + '...';
    }
    return firstLine + '...';
  }

  // Generate unique key for attribute expansion state
  getAttributeKey(traceId: string, spanId: string, eventName: string, attrKey: string): string {
    return `${traceId}:${spanId}:${eventName}:${attrKey}`;
  }

  // Toggle attribute expansion
  toggleAttribute(traceId: string, spanId: string, eventName: string, attrKey: string): void {
    const key = this.getAttributeKey(traceId, spanId, eventName, attrKey);
    if (this.expandedAttributes.has(key)) {
      this.expandedAttributes.delete(key);
    } else {
      this.expandedAttributes.add(key);
    }
  }

  // Check if attribute is expanded
  isAttributeExpanded(traceId: string, spanId: string, eventName: string, attrKey: string): boolean {
    const key = this.getAttributeKey(traceId, spanId, eventName, attrKey);
    return this.expandedAttributes.has(key);
  }
}
