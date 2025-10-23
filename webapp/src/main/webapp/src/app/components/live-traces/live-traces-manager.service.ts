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
import { Injectable, OnDestroy } from "@angular/core";
import { ReadableSpan } from "@opentelemetry/sdk-trace-base";
import { BehaviorSubject, Observable, Subscription } from "rxjs";
import { TracingService } from "../../services/tracing.service";

/**
 * Service for managing live traces connections, filtering, and deduplication.
 * Provides shared functionality for all trace visualization components.
 */
@Injectable()
export class LiveTracesManagerService implements OnDestroy {
  // Configuration
  serviceName = ".*";
  operationName = ".*";
  clientIpFilter = ".*";
  maxItems = 2000;

  // State
  connected = false;
  error = "";
  isLoading = false;

  // Observable for all traces
  private tracesSubject = new BehaviorSubject<ReadableSpan[][]>([]);
  traces$: Observable<ReadableSpan[][]> = this.tracesSubject.asObservable();

  // Internal array
  private _traces: ReadableSpan[][] = [];

  // Getter for synchronous access
  get traces(): ReadableSpan[][] {
    return this._traces;
  }

  // Total events count
  get totalEvents(): number {
    return this._traces.reduce(
      (acc, g) =>
        acc +
        (g.reduce((count, span) => count + (span.events?.length || 0), 0) || 0),
      0,
    );
  }

  private sub?: Subscription;
  private seenTraceIds = new Set<string>();

  constructor(private tracing: TracingService) {}

  ngOnDestroy(): void {
    this.disconnect();
  }

  /**
   * Connect to the live trace stream.
   */
  connect(): void {
    if (this.connected) return;
    if (!this.serviceName || !this.operationName) {
      this.error = "Service name and operation name are required";
      return;
    }

    // Clean up any previous subscription
    if (this.sub) {
      this.sub.unsubscribe();
      this.sub = undefined;
    }

    this.connected = true;
    this.error = "";
    this._traces = [];
    this.tracesSubject.next(this._traces);
    this.seenTraceIds.clear();

    this.sub = this.tracing
      .streamSpans(
        this.serviceName,
        this.operationName,
        this.clientIpFilter || ".*",
      )
      .subscribe({
        next: (spans) => {
          if (spans && spans.length > 0) {
            const traceId = spans[0]?.spanContext()?.traceId;
            if (traceId && !this.seenTraceIds.has(traceId)) {
              this.seenTraceIds.add(traceId);
              this._traces.unshift(spans);

              // Trim to max items
              if (this._traces.length > this.maxItems) {
                const removed = this._traces.pop();
                if (removed && removed[0]) {
                  const removedTraceId = removed[0].spanContext()?.traceId;
                  if (removedTraceId) {
                    this.seenTraceIds.delete(removedTraceId);
                  }
                }
              }

              // Emit the updated traces
              this.tracesSubject.next(this._traces);
            }
          }
        },
        error: (err) => {
          this.error = `Stream error: ${err.message || err}`;
          console.error("Stream error", err);
          this.connected = false;
          if (this.sub) {
            this.sub.unsubscribe();
            this.sub = undefined;
          }
        },
        complete: () => {
          this.connected = false;
          if (this.sub) {
            this.sub.unsubscribe();
            this.sub = undefined;
          }
        },
      });
  }

  /**
   * Prefill with historical traces.
   */
  prefill(): void {
    if (this.isLoading) return;
    if (!this.serviceName || !this.operationName) {
      this.error = "Service name and operation name are required";
      return;
    }

    this.isLoading = true;
    this.error = "";

    this.tracing
      .getTracesForOperation(
        this.serviceName,
        this.operationName,
        this.clientIpFilter || ".*",
      )
      .subscribe({
        next: (traces) => {
          if (traces && traces.length > 0) {
            // Filter out traces we've already seen
            const newTraces = traces.filter((trace) => {
              if (trace && trace.length > 0) {
                const traceId = trace[0]?.spanContext()?.traceId;
                if (traceId && !this.seenTraceIds.has(traceId)) {
                  this.seenTraceIds.add(traceId);
                  return true;
                }
              }
              return false;
            });

            if (newTraces.length > 0) {
              // Add new prefilled traces to the beginning
              this._traces = [...newTraces, ...this._traces];

              // Trim to max items
              if (this._traces.length > this.maxItems) {
                const toRemove = this._traces.length - this.maxItems;
                const removed = this._traces.splice(this.maxItems, toRemove);
                removed.forEach((trace) => {
                  if (trace && trace[0]) {
                    const removedTraceId = trace[0].spanContext()?.traceId;
                    if (removedTraceId) {
                      this.seenTraceIds.delete(removedTraceId);
                    }
                  }
                });
              }

              // Emit the updated traces
              this.tracesSubject.next(this._traces);
            }
          }
          this.isLoading = false;
        },
        error: (err) => {
          this.error = `Prefill error: ${err.message || err}`;
          console.error("Prefill error", err);
          this.isLoading = false;
        },
      });
  }

  /**
   * Disconnect from the live trace stream.
   */
  disconnect(): void {
    if (this.sub) {
      this.sub.unsubscribe();
      this.sub = undefined;
    }
    this.connected = false;
  }

  /**
   * Clear all traces.
   */
  clear(): void {
    this._traces = [];
    this.tracesSubject.next(this._traces);
    this.seenTraceIds.clear();
    this.error = "";
  }

  /**
   * Update configuration.
   */
  configure(config: {
    serviceName?: string;
    operationName?: string;
    clientIpFilter?: string;
    maxItems?: number;
  }): void {
    if (config.serviceName !== undefined) this.serviceName = config.serviceName;
    if (config.operationName !== undefined)
      this.operationName = config.operationName;
    if (config.clientIpFilter !== undefined)
      this.clientIpFilter = config.clientIpFilter;
    if (config.maxItems !== undefined) this.maxItems = config.maxItems;
  }
}
