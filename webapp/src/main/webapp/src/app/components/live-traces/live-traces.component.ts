import { Component, Input, OnDestroy, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute } from "@angular/router";
import { TraceGroupListComponent } from "./trace-group-list/trace-group-list.component";
import { ReadableSpan, TimedEvent } from "@opentelemetry/sdk-trace-base";
import { Attributes, HrTime, SpanStatusCode } from "@opentelemetry/api";
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
  spans: ReadableSpan[] = [];
  // View of groups comes from service
  traceGroups: ReadableSpan[][] = [];
  clientIpFilter = ".*";
  isLoading = false;

  private sub?: Subscription;
  private groupsSub?: Subscription;
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
    this.connected = true;
    this.error = "";
    this.spans = [];
    this.seenKeys.clear();
    this.traceGroups = [];
    this.seenTraceIds.clear();
    // Subscribe to groups store for this service/op with client IP filter
    const subscription = this.tracing
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
              this.traceGroups.unshift(spans);
              // Trim to max items
              if (this.traceGroups.length > this.maxItems) {
                const removed = this.traceGroups.pop();
                // Remove the trace ID of the removed group
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
        },
        complete: () => {
          this.connected = false;
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
            const newTraces = traces.filter((traceGroup) => {
              if (traceGroup && traceGroup.length > 0) {
                const traceId = traceGroup[0]?.spanContext()?.traceId;
                if (traceId && !this.seenTraceIds.has(traceId)) {
                  this.seenTraceIds.add(traceId);
                  return true;
                }
              }
              return false;
            });

            if (newTraces.length > 0) {
              // Add new prefilled traces to the beginning
              this.traceGroups = [...newTraces, ...this.traceGroups];
              // Trim to max items
              if (this.traceGroups.length > this.maxItems) {
                const toRemove = this.traceGroups.length - this.maxItems;
                const removed = this.traceGroups.splice(
                  this.maxItems,
                  toRemove,
                );
                // Remove trace IDs of removed groups
                removed.forEach((group) => {
                  if (group && group[0]) {
                    const removedTraceId = group[0].spanContext()?.traceId;
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
    if (this.groupsSub) {
      this.groupsSub.unsubscribe();
      this.groupsSub = undefined;
    }
    this.connected = false;
  }

  clear(): void {
    this.spans = [];
    this.seenKeys.clear();
    this.traceGroups = [];
    this.seenTraceIds.clear();
    this.error = "";
  }

  trackBySpan(index: number, s: ReadableSpan): string {
    const ctx = s.spanContext();
    return `${ctx.traceId}/${ctx.spanId}`;
  }

  trackByTrace(index: number, g: { traceId: string }): string {
    return g.traceId;
  }

  trackByEvent(
    index: number,
    e: { traceId: string; spanId: string; name: string; time?: HrTime },
  ): string {
    const t = e.time ? `${e.time[0]}-${e.time[1]}` : "no-time";
    return `${e.traceId}/${e.spanId}/${e.name}/${t}`;
  }

  getEventTitle(e: { name: string; attributes?: Attributes }): string {
    const raw = (e.attributes as any)?.["message"];
    if (raw === undefined || raw === null) return e.name;
    if (typeof raw === "string") return raw;
    try {
      return JSON.stringify(raw);
    } catch {
      return String(raw);
    }
  }

  hasNonMessageAttributes(attrs?: Attributes): boolean {
    if (!attrs) return false;
    for (const k in attrs) {
      if (Object.prototype.hasOwnProperty.call(attrs, k) && k !== "message")
        return true;
    }
    return false;
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

  get totalEvents(): number {
    return this.traceGroups.reduce(
      (acc, g) =>
        acc +
        (g.reduce((count, span) => count + (span.events?.length || 0), 0) || 0),
      0,
    );
  }

  // Helpers to compare HrTime
  // Kept for the component's own time formatting etc; store has its own copies as well.
  private toNanos(t?: HrTime): bigint {
    if (!t) return -1n;
    const [s, ns] = t;
    if (!Number.isFinite(s) || !Number.isFinite(ns) || (s === 0 && ns === 0))
      return -1n;
    return BigInt(Math.trunc(s)) * 1_000_000_000n + BigInt(Math.trunc(ns));
  }
  private minHrTime(
    a: HrTime | undefined,
    b: HrTime | undefined,
  ): HrTime | undefined {
    const an = this.toNanos(a);
    const bn = this.toNanos(b);
    if (an < 0n) return b;
    if (bn < 0n) return a;
    return an <= bn ? a : b;
  }
  private maxHrTime(
    a: HrTime | undefined,
    b: HrTime | undefined,
  ): HrTime | undefined {
    const an = this.toNanos(a);
    const bn = this.toNanos(b);
    if (an < 0n) return b;
    if (bn < 0n) return a;
    return an >= bn ? a : b;
  }
}
