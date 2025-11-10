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
  SpanKind,
  SpanContext,
  TraceFlags,
  SpanStatusCode,
  Attributes,
  AttributeValue,
  HrTime,
  Link,
} from "@opentelemetry/api";
import {ReadableSpan, TimedEvent} from "@opentelemetry/sdk-trace-base";

// --- Helper: convert epoch nanos -> HrTime tuple (handles bigint/number/string precisely) ---
function epochNanosToHrTime(epochNanos: number | string | bigint): HrTime {
  if (epochNanos == null) return [0, 0];
  try {
    const n = typeof epochNanos === "bigint" ? epochNanos : BigInt(epochNanos);
    const sec = n / 1_000_000_000n;
    const nsec = n % 1_000_000_000n;
    return [Number(sec), Number(nsec)];
  } catch {
    return [0, 0];
  }
}

// Convert HrTime to total nanoseconds (BigInt)
function hrTimeToNanos(t: HrTime): bigint {
  if (!t) return 0n;
  const [s, ns] = t;
  return BigInt(s) * 1_000_000_000n + BigInt(ns);
}

// Compute end-start as HrTime, clamped to >= 0
function hrTimeDiff(start: HrTime, end: HrTime): HrTime {
  const s = hrTimeToNanos(start);
  const e = hrTimeToNanos(end);
  const d = e > s ? e - s : 0n;
  const sec = d / 1_000_000_000n;
  const nsec = d % 1_000_000_000n;
  return [Number(sec), Number(nsec)];
}

// --- Helper: parse trace flags from various shapes ---
function parseTraceFlags(tf: any): TraceFlags {
  if (typeof tf === "number") return tf as TraceFlags;
  if (tf?.sampled === true) return TraceFlags.SAMPLED;
  return TraceFlags.NONE;
}

// --- Helper: normalize SpanKind (string | number) ---
function normalizeSpanKind(kind: any): SpanKind {
  if (typeof kind === "number") return kind as SpanKind;
  switch (String(kind).toUpperCase()) {
    case "INTERNAL":
      return SpanKind.INTERNAL;
    case "SERVER":
      return SpanKind.SERVER;
    case "CLIENT":
      return SpanKind.CLIENT;
    case "PRODUCER":
      return SpanKind.PRODUCER;
    case "CONSUMER":
      return SpanKind.CONSUMER;
    default:
      return SpanKind.INTERNAL;
  }
}

// --- Helper: parse attributes (supports: plain object, OTel "data" alt-pairs, or array of {key,value}) ---
function parseAttributes(attrs?: any): Attributes {
  if (!attrs) return {};

  // If it's already a plain key/value object
  if (!Array.isArray(attrs) && !Array.isArray(attrs?.data)) {
    const out: Record<string, AttributeValue> = {};
    for (const [k, v] of Object.entries(attrs)) {
      const av = toAttributeValue(v);
      if (av !== undefined) out[k] = av;
    }
    return out;
  }

  const data = Array.isArray(attrs?.data) ? attrs.data : attrs;

  // Case 1: alt-pairs like [ {key: "k1"}, v1, {key: "k2"}, v2, ... ]
  if (Array.isArray(data) && data.length > 0 && data.length % 2 === 0) {
    if (typeof data[0] === "object" && "key" in data[0] && !("value" in data[0])) {
      const out: Record<string, AttributeValue> = {};
      for (let i = 0; i + 1 < data.length; i += 2) {
        const kMeta = data[i];
        const v = data[i + 1];
        const key: string =
          (kMeta?.key as string) ??
          (typeof kMeta?.keyUtf8 === "string" ? safeBase64Decode(kMeta.keyUtf8) : undefined) ??
          (typeof kMeta === "string" ? kMeta : String(kMeta));
        const av = toAttributeValue(v);
        if (key && av !== undefined) out[key] = av;
      }
      return out;
    }
  }

  // Case 2: array of { key, value } where value may be typed {stringValue,intValue,...}
  if (Array.isArray(data) && data.every((e) => typeof e === "object" && "key" in e)) {
    const out: Record<string, AttributeValue> = {};
    for (const entry of data) {
      const key: string =
        entry.key ??
        (typeof entry.keyUtf8 === "string" ? safeBase64Decode(entry.keyUtf8) : undefined) ??
        "";
      let value = entry.value;

      // Unwrap typed value objects if present
      if (value && typeof value === "object") {
        const typed =
          value.stringValue ??
          value.boolValue ??
          value.intValue ??
          value.doubleValue ??
          value.bytesValue;
        if (typed !== undefined) value = typed;
      }

      const av = toAttributeValue(value);
      if (key && av !== undefined) out[key] = av;
    }
    return out;
  }

  // Fallback
  return {};
}

// Convert arbitrary value to AttributeValue (string | number | boolean | (Array of))
function toAttributeValue(value: any): AttributeValue | undefined {
  if (value == null) return undefined;
  const t = typeof value;
  if (t === "string" || t === "number" || t === "boolean") return value as AttributeValue;
  if (Array.isArray(value)) {
    const arr = value
      .map((v) => (typeof v === "string" || typeof v === "number" || typeof v === "boolean" ? v : undefined))
      .filter((v) => v !== undefined) as (string | number | boolean)[];
    return arr as AttributeValue;
  }
  // Try to coerce BigInt to number (nanos keys shouldn't land here typically)
  if (typeof value === "bigint") return Number(value) as AttributeValue;
  // Fallback: JSON stringify simple objects
  try {
    return JSON.stringify(value) as unknown as AttributeValue;
  } catch {
    return undefined;
  }
}

// Safe base64 decode for keyUtf8 fields
function safeBase64Decode(b64: string): string {
  try {
    if (typeof atob === "function") {
      return decodeURIComponent(
        Array.prototype.map
          .call(atob(b64), (c: string) => `%${("00" + c.charCodeAt(0).toString(16)).slice(-2)}`)
          .join("")
      );
    }
  } catch {
    // ignore
  }
  // As last resort, return original
  return b64;
}

// --- Helper: status mapping (string | number) -> SpanStatusCode ---
function parseStatusCode(code: any): SpanStatusCode {
  if (typeof code === "number") {
    if (code === SpanStatusCode.OK) return SpanStatusCode.OK;
    if (code === SpanStatusCode.ERROR) return SpanStatusCode.ERROR;
    return SpanStatusCode.UNSET;
  }
  const s = String(code ?? "").toUpperCase();
  if (s === "OK") return SpanStatusCode.OK;
  if (s === "ERROR") return SpanStatusCode.ERROR;
  return SpanStatusCode.UNSET;
}

// --- Main parser: raw JSON span -> ReadableSpan ---
export function parseReadableSpan(raw: any): ReadableSpan {
  // Handle null/undefined input
  if (!raw || typeof raw !== 'object') {
    raw = {};
  }

  const traceId = raw.traceId ?? raw.spanContext?.traceId ?? "";
  const spanId = raw.spanId ?? raw.spanContext?.spanId ?? "";

  const spanContextObj: SpanContext = {
    traceId,
    spanId,
    traceFlags: parseTraceFlags(raw.spanContext?.traceFlags ?? raw.traceFlags),
    isRemote: Boolean(raw.spanContext?.remote ?? raw.spanContext?.isRemote ?? false),
  };

  const attributes = parseAttributes(raw.attributes);
  const statusCode = parseStatusCode(raw.status?.statusCode);
  const statusMessage = raw.status?.description ?? raw.status?.message ?? "";

  // Parent span id: ignore all-zero parent
  const parentSpanIdRaw =
    raw.parentSpanId ??
    raw.parentSpanContext?.spanId ??
    raw.spanContext?.parentSpanId;
  const parentSpanId =
    parentSpanIdRaw && parentSpanIdRaw !== "0000000000000000"
      ? parentSpanIdRaw
      : undefined;

  // Only create parentSpanContext if we have a valid parent span ID
  const parentSpanContext: SpanContext | undefined = parentSpanId && raw.parentSpanContext
    ? {
      traceId: raw.parentSpanContext.traceId ?? traceId,
      spanId: parentSpanId,
      traceFlags: parseTraceFlags(raw.parentSpanContext.traceFlags),
      isRemote: Boolean(raw.parentSpanContext.remote ?? raw.parentSpanContext.isRemote ?? false),
    }
    : parentSpanId
      ? {
        traceId,
        spanId: parentSpanId,
        traceFlags: TraceFlags.NONE,
        isRemote: false,
      }
      : undefined;

  const eventsArray: any[] = Array.isArray(raw.events) ? raw.events : [];
  const events: TimedEvent[] = eventsArray.map((e: any) => ({
    name: String(e.name ?? ""),
    time: epochNanosToHrTime(e.epochNanos ?? e.timeUnixNano ?? 0),
    attributes: parseAttributes(e.attributes),
  }));

  const instr =
    raw.instrumentationScopeInfo ??
    raw.instrumentationLibraryInfo ??
    raw.instrumentationScope ??
    {};

  const resourceAttrs = parseAttributes(raw.resource?.attributes);
  const droppedAttributesCount = Math.max(
    0,
    (raw.totalAttributeCount ?? 0) - Object.keys(attributes).length
  );
  const droppedEventsCount = Math.max(
    0,
    (raw.totalRecordedEvents ?? 0) - eventsArray.length
  );
  const droppedLinksCount = Math.max(
    0,
    (raw.totalRecordedLinks ?? 0) - (Array.isArray(raw.links) ? raw.links.length : 0)
  );

  return {
    name: raw.name ?? "",
    kind: normalizeSpanKind(raw.kind),
    spanContext: () => spanContextObj,
    parentSpanContext,
    startTime: epochNanosToHrTime(raw.startEpochNanos ?? raw.startTimeUnixNano ?? 0),
    endTime: epochNanosToHrTime(raw.endEpochNanos ?? raw.endTimeUnixNano ?? 0),
    attributes,
    status: {
      code: statusCode,
      message: statusMessage,
    },
    events,
    links: (Array.isArray(raw.links) ? [] : []) as Link[],
    resource: {
      attributes: resourceAttrs,
    } as any, // Keep minimal shape compatible with our consumer
    instrumentationScope: {
      name: instr.name ?? "",
      version: instr.version,
    },
    duration: hrTimeDiff(
      epochNanosToHrTime(raw.startEpochNanos ?? raw.startTimeUnixNano ?? 0),
      epochNanosToHrTime(raw.endEpochNanos ?? raw.endTimeUnixNano ?? 0)
    ),
    ended:
      hrTimeToNanos(epochNanosToHrTime(raw.endEpochNanos ?? raw.endTimeUnixNano ?? 0)) >
      hrTimeToNanos(epochNanosToHrTime(raw.startEpochNanos ?? raw.startTimeUnixNano ?? 0)),
    droppedAttributesCount,
    droppedEventsCount,
    droppedLinksCount,
  };
}

// --- Batch parser (multiple spans) ---
export function parseReadableSpans(rawSpans: any[]): ReadableSpan[] {
  return (rawSpans ?? []).map(parseReadableSpan);
}
