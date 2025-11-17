package com.example.spanlink_demo.controller;

import com.example.spanlink_demo.service.AggregationService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/incoming")
public class IncomingController {

    private static final Logger log = LoggerFactory.getLogger(IncomingController.class);

    private final Tracer tracer;
    private final AggregationService aggregationService;

    /*
     *  -- NOTE --
     *  Inject tracer dependency
     */
    public IncomingController(Tracer tracer, AggregationService aggregationService) {
        this.tracer = tracer;
        this.aggregationService = aggregationService;
    }

    @PostMapping
    public ResponseEntity<?> handleIncoming(
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestBody(required = false) String body) {

        // 1) Validate required header
        if (requestId == null || requestId.isBlank()) {
            // No span is created; we fail fast
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Missing required header: x-request-id"
            ));
        }

        /*
        *  -- NOTE --
        *  Start span when requests go to /incoming
        *  Add span tags
        */
        // 2) Create a SERVER span for this endpoint
        Span span = tracer.spanBuilder("handleIncomingMethod")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        // Put span in scope for this method
        try (Scope scope = span.makeCurrent()) {
            // 3) Tag the span with x-request-id
            span.setAttribute("x-request-id", requestId);
            span.setAttribute("http.method", "POST");
            span.setAttribute("http.route", "/incoming");
            span.setAttribute("request.timestamp", Instant.now().toString());

            /*
            *  -- NOTE --
            *  log request IDs and trace IDs
            *  Send context and request IDs to recordIncomingRequest once every incoming request
            */

            // 4) Log requestId together with traceId
            SpanContext ctx = span.getSpanContext();
            String traceId = ctx.getTraceId();
            log.info("Received HTTP POST request at /incoming endpoint. x-request-id={} traceId={}", requestId, traceId);

            // Your existing aggregation logic (kept as-is)
            aggregationService.recordIncomingRequest(ctx, requestId);

            return ResponseEntity.ok(Map.of(
                    "status", "received",
                    "timestamp", Instant.now().toString()
            ));
        } finally {
            span.end();
        }
    }
}
