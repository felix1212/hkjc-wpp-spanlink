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
    private volatile Long lastResetTime = null; // null means no timer started yet
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private Tracer tracer;

    @Autowired
    public AggregationService(Tracer tracer) {
        this.tracer = tracer;
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

    public void recordIncomingRequest(SpanContext spanContext) {
        lock.lock();
        try {
            int count = requestCount.incrementAndGet();
            pendingSpanContexts.add(spanContext);
            
            // Start the timer on the first request
            if (lastResetTime == null) {
                lastResetTime = System.currentTimeMillis();
            }
            
            String traceId = spanContext.getTraceId();
            logger.info("Incoming request recorded. Trace ID: {}, Total count: {}, Timestamp: {}", 
                    traceId, count, Instant.now());

            if (count >= triggerCount) {
                triggerAction("count_threshold", pendingSpanContexts);
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
                    triggerAction("time_interval", new ArrayList<>(pendingSpanContexts));
                    reset();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void triggerAction(String reason, List<SpanContext> spanContexts) {
        // Create a span builder and add links for all pending spans
        var spanBuilder = tracer.spanBuilder("aggregated-action")
                .setSpanKind(SpanKind.INTERNAL);
        
        // Add links to all incoming request spans
        for (SpanContext spanContext : spanContexts) {
            spanBuilder.addLink(spanContext);
        }

        // Create a new span that links to all incoming request spans
        Span aggregatedSpan = spanBuilder.startSpan();

        try (Scope scope = aggregatedSpan.makeCurrent()) {
            aggregatedSpan.setAttribute("trigger.reason", reason);
            aggregatedSpan.setAttribute("trigger.count", spanContexts.size());
            aggregatedSpan.setAttribute("trigger.timestamp", Instant.now().toString());

            // Get master trace ID (from the aggregated span)
            String masterTraceId = aggregatedSpan.getSpanContext().getTraceId();
            
            // Collect trace IDs from all linked spans
            List<String> linkedTraceIds = new ArrayList<>();
            for (SpanContext spanContext : spanContexts) {
                linkedTraceIds.add(spanContext.getTraceId());
            }

            logger.info("Action triggered. Master Trace ID: {}, Linked Trace IDs: {}, Reason: {}, Linked spans count: {}, Timestamp: {}", 
                    masterTraceId, linkedTraceIds, reason, spanContexts.size(), Instant.now());
        } finally {
            aggregatedSpan.end();
        }
    }

    private void reset() {
        requestCount.set(0);
        pendingSpanContexts.clear();
        lastResetTime = null; // Reset timer - will start again on next request
    }
}

