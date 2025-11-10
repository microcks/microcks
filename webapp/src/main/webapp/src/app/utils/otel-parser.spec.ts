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
import {parseReadableSpan, parseReadableSpans} from './otel-parser';
import {SpanKind, SpanStatusCode, TraceFlags} from '@opentelemetry/api';

describe('otel-parser', () => {
  let testTraces: any;

  beforeAll(async () => {
    // Dynamically import test data only in test environment
    const testData = await import('../../test-data/test-traces.json');
    testTraces = testData.default;
  });

  describe('parseReadableSpan', () => {
    let sampleSpan: any;

    beforeEach(() => {
      // Use the first span from the first trace in test data
      sampleSpan = testTraces[0][0];
    });

    it('should parse basic span properties', () => {
      const result = parseReadableSpan(sampleSpan);

      expect(result.name).toBe('computeDispatchCriteria');
      expect(result.kind).toBe(SpanKind.INTERNAL);
      expect(result.ended).toBe(true);
    });

    it('should parse span context correctly', () => {
      const result = parseReadableSpan(sampleSpan);
      const spanContext = result.spanContext();

      expect(spanContext.traceId).toBe('69b17bb78c12965f2549e38450271363');
      expect(spanContext.spanId).toBe('8cbf86cc28908805');
      expect(spanContext.traceFlags).toBe(TraceFlags.SAMPLED);
      expect(spanContext.isRemote).toBe(false);
    });

    it('should parse parent span context', () => {
      const result = parseReadableSpan(sampleSpan);

      expect(result.parentSpanContext).toBeDefined();
      expect(result.parentSpanContext!.traceId).toBe('69b17bb78c12965f2549e38450271363');
      expect(result.parentSpanContext!.spanId).toBe('4563beff7e194620');
      expect(result.parentSpanContext!.traceFlags).toBe(TraceFlags.SAMPLED);
    });

    it('should parse time fields correctly', () => {
      const result = parseReadableSpan(sampleSpan);

      // Start time should be valid
      expect(result.startTime).toBeDefined();
      expect(result.startTime[0]).toBeGreaterThan(0); // seconds
      expect(result.startTime[1]).toBeGreaterThan(0); // nanoseconds

      // End time should be valid
      expect(result.endTime).toBeDefined();
      expect(result.endTime[0]).toBeGreaterThan(0);
      expect(result.endTime[1]).toBeGreaterThan(0);

      // Duration should be calculated correctly
      expect(result.duration).toBeDefined();
      expect(result.duration[0]).toBeGreaterThanOrEqual(0);
      expect(result.duration[1]).toBeGreaterThanOrEqual(0);
    });

    it('should parse attributes with alternating key-value pairs', () => {
      const result = parseReadableSpan(sampleSpan);

      expect(result.attributes['explain-trace']).toBe(true);
    });

    it('should parse events correctly', () => {
      const result = parseReadableSpan(sampleSpan);

      expect(result.events).toBeDefined();
      expect(result.events.length).toBe(1);

      const event = result.events[0];
      expect(event.name).toBe('dispatch_criteria_result');
      expect(event.time).toBeDefined();
      expect(event.attributes).toBeDefined();

      // Check event attributes
      expect(event.attributes).toBeDefined();
      expect(event.attributes!['message']).toBe('Computed dispatch criteria using URI parts');
      expect(event.attributes!['dispatch.type']).toBe('URI_PARTS');
    });

    it('should parse resource attributes', () => {
      const result = parseReadableSpan(sampleSpan);

      expect(result.resource.attributes).toBeDefined();
      expect(result.resource.attributes['host.arch']).toBe('amd64');
      expect(result.resource.attributes['service.name']).toBe('microcks');
      expect(result.resource.attributes['telemetry.sdk.name']).toBe('opentelemetry');
    });

    it('should parse instrumentation scope', () => {
      const result = parseReadableSpan(sampleSpan);

      expect(result.instrumentationScope).toBeDefined();
      expect(result.instrumentationScope.name).toBe('io.github.microcks.web.RestInvocationProcessor');
    });

    it('should parse span status', () => {
      const result = parseReadableSpan(sampleSpan);

      expect(result.status).toBeDefined();
      expect(result.status.code).toBe(SpanStatusCode.UNSET);
      expect(result.status.message).toBe('');
    });

    it('should parse dropped counts', () => {
      const result = parseReadableSpan(sampleSpan);

      expect(result.droppedAttributesCount).toBe(0);
      expect(result.droppedEventsCount).toBe(0);
      expect(result.droppedLinksCount).toBe(0);
    });
  });

  describe('parseReadableSpan - Different Span Types', () => {
    it('should parse SERVER span kind correctly', () => {
      // Use the third span from first trace which is a SERVER span
      const serverSpan = testTraces[0][2];
      const result = parseReadableSpan(serverSpan);

      expect(result.name).toBe('GET /rest/{service}/{version}/**');
      expect(result.kind).toBe(SpanKind.SERVER);
      expect(result.attributes['http.request.method']).toBe('GET');
      expect(result.attributes['server.port']).toBe(8080);
    });

    it('should handle spans with no parent correctly', () => {
      const serverSpan = testTraces[0][2]; // This is a root span
      const result = parseReadableSpan(serverSpan);

      // Root spans should have no parent or parent with all-zero spanId
      expect(result.parentSpanContext).toBeUndefined();
    });

    it('should parse spans from different traces', () => {
      // Test spans from different traces have different trace IDs
      const trace1Span = testTraces[0][0];
      const trace2Span = testTraces[1][0];

      const result1 = parseReadableSpan(trace1Span);
      const result2 = parseReadableSpan(trace2Span);

      expect(result1.spanContext().traceId).not.toBe(result2.spanContext().traceId);
      expect(result1.spanContext().traceId).toBe('69b17bb78c12965f2549e38450271363');
      expect(result2.spanContext().traceId).toBe('fa9a91889a5cf5478265d2f156b3eb57');
    });
  });

  describe('parseReadableSpan - Edge Cases', () => {
    it('should handle span with minimal data', () => {
      const minimalSpan = {
        name: 'test-span',
        spanContext: {
          traceId: '12345',
          spanId: '67890',
        }
      };

      const result = parseReadableSpan(minimalSpan);

      expect(result.name).toBe('test-span');
      expect(result.spanContext().traceId).toBe('12345');
      expect(result.spanContext().spanId).toBe('67890');
      expect(result.kind).toBe(SpanKind.INTERNAL); // default
      expect(result.attributes).toEqual({});
      expect(result.events).toEqual([]);
    });

    it('should handle null/undefined input gracefully', () => {
      const result1 = parseReadableSpan(null);
      const result2 = parseReadableSpan(undefined);
      const result3 = parseReadableSpan({});

      [result1, result2, result3].forEach(result => {
        expect(result.name).toBe('');
        expect(result.spanContext().traceId).toBe('');
        expect(result.spanContext().spanId).toBe('');
        expect(result.attributes).toEqual({});
      });
    });

    it('should handle different attribute formats', () => {
      const spanWithPlainAttrs = {
        name: 'test-span',
        attributes: {
          'simple.key': 'simple.value',
          'number.key': 42,
          'boolean.key': true
        }
      };

      const result = parseReadableSpan(spanWithPlainAttrs);

      expect(result.attributes['simple.key']).toBe('simple.value');
      expect(result.attributes['number.key']).toBe(42);
      expect(result.attributes['boolean.key']).toBe(true);
    });
  });

  describe('parseReadableSpans', () => {
    it('should parse multiple spans from a single trace', () => {
      const trace = testTraces[0]; // First trace has 3 spans
      const results = parseReadableSpans(trace);

      expect(results.length).toBe(3);

      // All spans should have the same trace ID
      const traceId = results[0].spanContext().traceId;
      results.forEach(span => {
        expect(span.spanContext().traceId).toBe(traceId);
      });

      // Verify span names
      expect(results[0].name).toBe('computeDispatchCriteria');
      expect(results[1].name).toBe('processInvocation');
      expect(results[2].name).toBe('GET /rest/{service}/{version}/**');
    });

    it('should handle empty array', () => {
      const results = parseReadableSpans([]);
      expect(results).toEqual([]);
    });

    it('should handle null/undefined input', () => {
      const results1 = parseReadableSpans(null as any);
      const results2 = parseReadableSpans(undefined as any);

      expect(results1).toEqual([]);
      expect(results2).toEqual([]);
    });
  });

  describe('Integration Tests with Test Data', () => {
    it('should successfully parse all test trace data', () => {
      let totalSpans = 0;
      const totalTraces = testTraces.length;

      testTraces.forEach((trace: any) => {
        const spans = parseReadableSpans(trace);
        totalSpans += spans.length;

        spans.forEach(span => {
          // Basic validation that all required fields are present
          expect(span.name).toBeDefined();
          expect(span.spanContext).toBeDefined();
          expect(span.startTime).toBeDefined();
          expect(span.endTime).toBeDefined();
          expect(span.attributes).toBeDefined();
          expect(span.events).toBeDefined();
          expect(span.status).toBeDefined();
        });
      });

      expect(totalSpans).toBeGreaterThan(0);
      expect(totalTraces).toBeGreaterThan(0);
    });

    it('should maintain parent-child relationships within traces', () => {
      testTraces.forEach((trace: any) => {
        const spans = parseReadableSpans(trace);

        spans.forEach(span => {
          if (span.parentSpanContext) {
            // Find parent span in the same trace
            const parentSpan = spans.find(s =>
              s.spanContext().spanId === span.parentSpanContext!.spanId
            );

            if (parentSpan) {
              expect(parentSpan.spanContext().traceId).toBe(span.spanContext().traceId);
            }
          }
        });
      });
    });
  });
});
