package com.example.spanlink_demo.controller;

import com.example.spanlink_demo.service.AggregationService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping
public class IncomingController {

    private static final Logger logger = LoggerFactory.getLogger(IncomingController.class);

    @Autowired
    private Tracer tracer;

    @Autowired
    private AggregationService aggregationService;

    @PostMapping("/incoming")
    public ResponseEntity<Map<String, String>> handleIncoming(@RequestBody(required = false) Map<String, Object> body) {
        // Create a span for the incoming request
        Span span = tracer.spanBuilder("incoming-request")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("http.method", "POST");
            span.setAttribute("http.route", "/incoming");
            span.setAttribute("request.timestamp", Instant.now().toString());

            // Get the span context to link later
            SpanContext spanContext = span.getSpanContext();
            String traceId = spanContext.getTraceId();

            logger.info("Received HTTP POST request at /incoming endpoint. Trace ID: {}, Timestamp: {}", 
                    traceId, Instant.now());

            // Record the incoming request in the aggregation service
            aggregationService.recordIncomingRequest(spanContext);

            return ResponseEntity.ok(Map.of(
                    "status", "received",
                    "timestamp", Instant.now().toString()
            ));
        } finally {
            span.end();
        }
    }
}

