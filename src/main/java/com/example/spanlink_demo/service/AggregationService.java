package com.example.spanlink_demo.service;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AggregationService {

    private static final Logger logger = LoggerFactory.getLogger(AggregationService.class);

    @Value("${aggregation.trigger.count:3}")
    private int triggerCount;

    @Value("${aggregation.trigger.interval.seconds:10}")
    private long triggerIntervalSeconds;

    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final ReentrantLock lock = new ReentrantLock();
    private final List<SpanContext> pendingSpanContexts = new ArrayList<>();

    /**
     * Rolling buffer of the x-request-id values associated with the pending requests.
     * At most 3 items are kept.
     */
    private final List<String> pendingRequestIds = new ArrayList<>();

    private volatile Long lastResetTime = null; // null means no timer started yet
    private volatile Instant firstRequestTimestamp = null; // Timestamp of the first request in the current batch
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final Tracer tracer;
    private final MongoContextService mongoContextService;

    @Autowired
    public AggregationService(Tracer tracer, MongoContextService mongoContextService) {
        this.tracer = tracer;
        this.mongoContextService = mongoContextService;
        // Schedule periodic check every second
        scheduler.scheduleAtFixedRate(this::checkTimeTrigger, 1, 1, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Called by the controller once per incoming request.
     *
     * @param spanContext span context of the incoming request span
     * @param requestId   x-request-id header value (already validated in controller)
     */
    public void recordIncomingRequest(SpanContext spanContext, String requestId) {
        lock.lock();
        try {
            /*
            *  -- NOTE --
            *  create pendingSpanContexts list object to store span context for every request, and pendingRequestIds list object
            *  write log for every request
            */
            int count = requestCount.incrementAndGet();
            pendingSpanContexts.add(spanContext);

            if (requestId != null && !requestId.isBlank()) {
                pendingRequestIds.add(requestId);
                // Keep all request IDs
                if (pendingRequestIds.size() > triggerCount) {
                    pendingRequestIds.remove(0);
                }
            }

            // Start the timer on the first request
            if (lastResetTime == null) {
                lastResetTime = System.currentTimeMillis();
                firstRequestTimestamp = Instant.now(); // Capture timestamp of the first request
            }

            String traceId = spanContext.getTraceId();
            logger.info(
                    "Incoming request recorded. Trace ID: {}, x-request-id(s): {}, Total count: {}, Timestamp: {}",
                    traceId,
                    String.join(", ", pendingRequestIds),
                    count,
                    Instant.now()
            );

            // Count-based trigger
            if (count >= triggerCount) {
                /*
                *  -- NOTE --
                *  call triggerAction when criteria fulfilled
                *  Send context arraylist and request ID arraylist
                */
                triggerAction(
                        "count_threshold",
                        new ArrayList<>(pendingSpanContexts),
                        new ArrayList<>(pendingRequestIds),
                        firstRequestTimestamp
                );
                reset();
            }
        } finally {
            lock.unlock();
        }
    }

    private void checkTimeTrigger() {
        lock.lock();
        try {
            // Only check if timer has started (first request received) and there are pending requests
            if (lastResetTime != null && !pendingSpanContexts.isEmpty()) {
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - lastResetTime;

                if (elapsed >= triggerIntervalSeconds * 1000) {
                    triggerAction(
                            "time_interval",
                            new ArrayList<>(pendingSpanContexts),
                            new ArrayList<>(pendingRequestIds),
                            firstRequestTimestamp
                    );
                    reset();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void triggerAction(String reason,
                               List<SpanContext> spanContexts,
                               List<String> requestIds,
                               Instant firstRequestTimestamp) {

        // Create a span builder and add links for all pending spans
        /*
        *  -- NOTE --
        *  Start span for outgoing action. In real case this will be writing trace context (as trace parent) to document and calling MongoDB.
        */
        var spanBuilder = tracer.spanBuilder("aggregated-action")
                .setSpanKind(SpanKind.INTERNAL);
        /*
        *  -- NOTE --
        *  IMPORTANT!
        *  Call addLink and input trace context as parameter, and start as usual
        *  Perform .addLink() before .startSpan()
        */
        for (SpanContext ctx : spanContexts) {
            spanBuilder.addLink(ctx);
        }

        // Create a new span that links to all incoming request spans
        Span aggregatedSpan = spanBuilder.startSpan();

        try (Scope scope = aggregatedSpan.makeCurrent()) {
            aggregatedSpan.setAttribute("trigger.reason", reason);
            aggregatedSpan.setAttribute("trigger.count", spanContexts.size());
            aggregatedSpan.setAttribute("trigger.timestamp", Instant.now().toString());
            aggregatedSpan.setAttribute("first.request.timestamp", firstRequestTimestamp != null ? firstRequestTimestamp.toString() : "unknown");

            // ----- x-request-id attributes (1..3 & combined) -----
            aggregatedSpan.setAttribute("x-request-id.count", requestIds.size());
            for (int i = 0; i < requestIds.size(); i++) {
                aggregatedSpan.setAttribute("x-request-id-" + (i + 1), requestIds.get(i));
            }
            aggregatedSpan.setAttribute("x-request-id.all", String.join(",", requestIds));

            // Get master trace ID (from the aggregated span)
            String masterTraceId = aggregatedSpan.getSpanContext().getTraceId();

            // Collect trace IDs from all linked spans
            List<String> linkedTraceIds = new ArrayList<>();
            for (SpanContext ctx : spanContexts) {
                linkedTraceIds.add(ctx.getTraceId());
            }

            logger.info(
                    "Action triggered. Master Trace ID: {}, Linked Trace IDs: {}, x-request-id(s): {}, Reason: {}, Linked spans count: {}, Timestamp: {}",
                    masterTraceId,
                    linkedTraceIds,
                    requestIds,
                    reason,
                    spanContexts.size(),
                    Instant.now()
            );

            /*
             *  -- NOTE --
             *  Write pendingRequestIds and pendingSpanContexts to MongoDB for downstream context propagation
             */
            try {
                mongoContextService.saveAggregatedContext(
                        reason,
                        spanContexts,
                        requestIds,
                        masterTraceId,
                        firstRequestTimestamp
                );
                aggregatedSpan.setAttribute("mongo.write.success", true);
                logger.info("Successfully wrote aggregated context to MongoDB for downstream processing");
            } catch (Exception e) {
                aggregatedSpan.setAttribute("mongo.write.success", false);
                aggregatedSpan.setAttribute("mongo.write.error", e.getMessage());
                logger.error("Failed to write aggregated context to MongoDB", e);
            }
        } finally {
            aggregatedSpan.end();
        }
    }

    private void reset() {
        requestCount.set(0);
        pendingSpanContexts.clear();
        pendingRequestIds.clear();
        lastResetTime = null; // Reset timer - will start again on next request
        firstRequestTimestamp = null; // Reset first request timestamp
    }
}
