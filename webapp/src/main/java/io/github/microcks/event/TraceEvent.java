package io.github.microcks.event;

public record TraceEvent(String traceId, String service, String operation) {
}
