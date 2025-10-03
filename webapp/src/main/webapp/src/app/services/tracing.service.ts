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
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { HttpClient, HttpParams } from "@angular/common/http";
import { ReadableSpan } from "@opentelemetry/sdk-trace-base";

import { ConfigService } from "./config.service";
import { parseReadableSpans } from "../utils/otel-parser";

@Injectable({ providedIn: "root" })
export class TracingService {
  private readonly rootUrl = "/api";

  constructor(
    private config: ConfigService,
    private http: HttpClient,
  ) {}

  /**
   * Get all trace IDs currently stored.
   * @returns Observable of Set of trace IDs
   */
  getAllTraceIds(): Observable<Set<string>> {
    return this.http.get<Set<string>>(`${this.rootUrl}/traces`);
  }

  /**
   * Get all spans for a specific trace ID.
   * @param traceId The trace ID to look up
   * @returns Observable of List of spans for the trace
   */
  getSpansForTrace(traceId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.rootUrl}/traces/${traceId}/spans`);
  }

  /**
   * Get traces for a specific service operation.
   * @param serviceName The service name
   * @param operationName The operation name
   * @param clientAddress The client address pattern (optional, defaults to '.*')
   * @returns Observable of List of List of ReadableSpan
   */
  getTracesForOperation(
    serviceName: string,
    operationName: string,
    clientAddress?: string,
  ): Observable<ReadableSpan[][]> {
    const params = new HttpParams()
      .set("serviceName", serviceName)
      .set("operationName", operationName)
      .set("clientAddress", clientAddress || ".*");

    return new Observable<ReadableSpan[][]>((subscriber) => {
      this.http
        .get<any[][]>(`${this.rootUrl}/traces/operations`, { params })
        .subscribe({
          next: (rawTraces) => {
            // Parse each trace
            const parsedTraces = rawTraces.map((traceGroup) =>
              parseReadableSpans(traceGroup),
            );
            subscriber.next(parsedTraces);
            subscriber.complete();
          },
          error: (err) => {
            subscriber.error(err);
          },
        });
    });
  }

  /**
   * Clear all stored traces and spans.
   * @returns Observable of success message
   */
  clearAllTraces(): Observable<string> {
    return this.http.delete<string>(`${this.rootUrl}/traces`);
  }

  /**
   * Open an SSE stream of spans for a given service/operation.
   * Emits whenever a 'trace' event arrives with a JSON array of spans.
   * The returned Observable will close the connection on unsubscribe.
   */
  streamSpans(
    serviceName: string,
    operationName: string,
    clientAddress?: string,
  ): Observable<ReadableSpan[]> {
    return new Observable<ReadableSpan[]>((subscriber) => {
      // Build query string safely
      let params = new HttpParams()
        .set("serviceName", serviceName)
        .set("operationName", operationName)
        .set("clientAddress", clientAddress || "*");

      const url = `${this.rootUrl}/traces/operations/stream?${params.toString()}`;

      // withCredentials allows cookies if backend uses session auth
      const es = new EventSource(url, { withCredentials: true });

      const onTrace = (evt: MessageEvent) => {
        try {
          const raw = JSON.parse(evt.data);
          const spans = Array.isArray(raw) ? parseReadableSpans(raw) : [];
          subscriber.next(spans);
        } catch (err) {
          subscriber.error(err);
          // Keep the stream open; caller can resubscribe if needed
        }
      };

      const onError = (err: any) => {
        // EventSource reconnection is automatic; only close on fatal network errors.
        // We forward the error for UI to optionally display status.
        subscriber.error(err);
        try {
          es.close();
        } catch {}
      };

      es.addEventListener("trace", onTrace as EventListener);
      // Optional: handle default 'message' events if server doesn't name them
      es.onmessage = onTrace;
      es.onerror = onError as any;

      // Cleanup on unsubscribe
      return () => {
        try {
          es.removeEventListener("trace", onTrace as EventListener);
          es.close();
        } catch {}
      };
    });
  }
}
