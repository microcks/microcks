import { Component, Input, OnDestroy, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute } from "@angular/router";
import { TraceGroupListComponent } from "./trace-group-list/trace-group-list.component";
import { ReadableSpan } from "@opentelemetry/sdk-trace-base";
import { Subscription } from "rxjs";
import { TracingService } from "../../services/tracing.service";

@Component({
  selector: "app-live-traces",
  standalone: true,
  imports: [CommonModule, FormsModule, TraceGroupListComponent],
  templateUrl: "./live-traces.component.html",
  styleUrls: ["./live-traces.component.css"],
})
export class LiveTracesComponent implements OnInit, OnDestroy {
  @Input() set initialServiceName(value: string) {
    if (value) this.serviceName = value;
  }
  @Input() set initialOperationName(value: string) {
    if (value) this.operationName = value;
  }
  @Input() autoConnect = false;
  @Input() maxItems = 200;
  @Input() allowCustomization = false; // Controls whether service/operation names are editable

  serviceName = "";
  operationName = "";
  connected = false;
  error = "";
  traces: ReadableSpan[][] = [];
  clientIpFilter = ".*";
  isLoading = false;

  private sub?: Subscription;
  private seenKeys = new Set<string>();
  private seenTraceIds = new Set<string>();

  constructor(
    private tracing: TracingService,
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
      this.connect();
    }
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  connect(): void {
    if (this.connected) return;
    if (!this.serviceName || !this.operationName) {
      this.error = "Service name and operation name are required";
      return;
    }
    // Ensure any previous subscription is cleaned up
    if (this.sub) {
      this.sub.unsubscribe();
      this.sub = undefined;
    }
    this.connected = true;
    this.error = "";
    this.seenKeys.clear();
    this.traces = [];
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
            // Get trace ID from first span
            const traceId = spans[0]?.spanContext()?.traceId;
            if (traceId && !this.seenTraceIds.has(traceId)) {
              this.seenTraceIds.add(traceId);
              this.traces.unshift(spans);
              // Trim to max items
              if (this.traces.length > this.maxItems) {
                const removed = this.traces.pop();
                // Remove the trace ID of the removed trace
                if (removed && removed[0]) {
                  const removedTraceId = removed[0].spanContext()?.traceId;
                  if (removedTraceId) {
                    this.seenTraceIds.delete(removedTraceId);
                  }
                }
              }
            }
          }
        },
        error: (err) => {
          this.error = `Stream error: ${err.message || err}`;
          console.error("Stream error", err);
          this.connected = false;
          // Ensure subscription reference is cleared on error
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
              this.traces = [...newTraces, ...this.traces];
              // Trim to max items
              if (this.traces.length > this.maxItems) {
                const toRemove = this.traces.length - this.maxItems;
                const removed = this.traces.splice(this.maxItems, toRemove);
                // Remove trace IDs of removed traces
                removed.forEach((trace) => {
                  if (trace && trace[0]) {
                    const removedTraceId = trace[0].spanContext()?.traceId;
                    if (removedTraceId) {
                      this.seenTraceIds.delete(removedTraceId);
                    }
                  }
                });
              }
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

  disconnect(): void {
    if (this.sub) {
      this.sub.unsubscribe();
      this.sub = undefined;
    }
    this.connected = false;
  }

  clear(): void {
    this.seenKeys.clear();
    this.traces = [];
    this.seenTraceIds.clear();
    this.error = "";
  }

  get totalEvents(): number {
    return this.traces.reduce(
      (acc, g) =>
        acc +
        (g.reduce((count, span) => count + (span.events?.length || 0), 0) || 0),
      0,
    );
  }
}
