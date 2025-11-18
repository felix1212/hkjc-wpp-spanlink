package com.example.spanlink_demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "aggregated_contexts")
public class AggregatedContextDocument {

    @Id
    private String id;

    private String triggerReason;
    private List<String> pendingRequestIds;
    private List<SpanContextData> pendingSpanContexts;
    private String masterTraceId;
    private Instant timestamp;

    public AggregatedContextDocument() {
    }

    public AggregatedContextDocument(String triggerReason, List<String> pendingRequestIds,
                                     List<SpanContextData> pendingSpanContexts, String masterTraceId) {
        this.triggerReason = triggerReason;
        this.pendingRequestIds = pendingRequestIds;
        this.pendingSpanContexts = pendingSpanContexts;
        this.masterTraceId = masterTraceId;
        this.timestamp = Instant.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTriggerReason() {
        return triggerReason;
    }

    public void setTriggerReason(String triggerReason) {
        this.triggerReason = triggerReason;
    }

    public List<String> getPendingRequestIds() {
        return pendingRequestIds;
    }

    public void setPendingRequestIds(List<String> pendingRequestIds) {
        this.pendingRequestIds = pendingRequestIds;
    }

    public List<SpanContextData> getPendingSpanContexts() {
        return pendingSpanContexts;
    }

    public void setPendingSpanContexts(List<SpanContextData> pendingSpanContexts) {
        this.pendingSpanContexts = pendingSpanContexts;
    }

    public String getMasterTraceId() {
        return masterTraceId;
    }

    public void setMasterTraceId(String masterTraceId) {
        this.masterTraceId = masterTraceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Nested class to store span context data
     */
    public static class SpanContextData {
        private String traceId;
        private String spanId;
        private String traceFlags;
        private String traceState;
        private String traceparent; // W3C traceparent header value for context propagation

        public SpanContextData() {
        }

        public SpanContextData(String traceId, String spanId, String traceFlags, String traceState, String traceparent) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.traceFlags = traceFlags;
            this.traceState = traceState;
            this.traceparent = traceparent;
        }

        // Getters and Setters
        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public String getSpanId() {
            return spanId;
        }

        public void setSpanId(String spanId) {
            this.spanId = spanId;
        }

        public String getTraceFlags() {
            return traceFlags;
        }

        public void setTraceFlags(String traceFlags) {
            this.traceFlags = traceFlags;
        }

        public String getTraceState() {
            return traceState;
        }

        public void setTraceState(String traceState) {
            this.traceState = traceState;
        }

        public String getTraceparent() {
            return traceparent;
        }

        public void setTraceparent(String traceparent) {
            this.traceparent = traceparent;
        }
    }
}

