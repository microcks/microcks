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
import { ReadableSpan } from "@opentelemetry/sdk-trace-base";

/**
 * Extract trace event information from spans
 * Looks for service.name, operation.name, and client.address attributes
 */
export interface TraceEvent {
  traceId: string;
  serviceName: string;
  operationName: string;
  clientAddress: string;
}

export function extractTraceEvent(spans: ReadableSpan[]): TraceEvent | null {
  if (!spans || spans.length === 0) {
    return null;
  }

  let serviceName: string | null = null;
  let operationName: string | null = null;
  let clientAddress: string | null = null;
  let traceId: string | null = null;

  // Iterate through spans to find the attributes
  for (const span of spans) {
    if (!span) {
      continue;
    }

    // Get trace ID from span context
    if (!traceId && span.spanContext) {
      traceId = span.spanContext()?.traceId || null;
    }

    // Extract attributes
    const attributes = span.attributes || {};
    
    // Look for service.name attribute
    if (!serviceName && attributes['service.name']) {
      serviceName = String(attributes['service.name']);
    }

    // Look for operation.name attribute
    if (!operationName && attributes['operation.name']) {
      operationName = String(attributes['operation.name']);
    }

    // Look for client.address attribute
    if (!clientAddress && attributes['client.address']) {
      clientAddress = String(attributes['client.address']);
    }

    // If we found all required attributes, we can stop
    if (serviceName && operationName && clientAddress && traceId) {
      break;
    }
  }

  // Return null if we couldn't find the essential information
  if (!serviceName || !operationName) {
    return null;
  }

  return {
    traceId: traceId || '',
    serviceName,
    operationName,
    clientAddress: clientAddress || 'unknown'
  };
}