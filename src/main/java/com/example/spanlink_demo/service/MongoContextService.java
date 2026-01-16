package com.example.spanlink_demo.service;

import com.example.spanlink_demo.model.AggregatedContextDocument;
import com.example.spanlink_demo.repository.AggregatedContextRepository;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MongoContextService {

    private static final Logger logger = LoggerFactory.getLogger(MongoContextService.class);

    private final AggregatedContextRepository repository;

    @Autowired
    public MongoContextService(AggregatedContextRepository repository) {
        this.repository = repository;
    }

    /**
     * Saves aggregated context data to MongoDB for downstream context propagation
     *
     * @param triggerReason        reason for the trigger (e.g., "count_threshold", "time_interval")
     * @param spanContexts         list of span contexts to be propagated
     * @param requestIds           list of request IDs
     * @param masterTraceId        the master trace ID from the aggregated span
     * @param firstRequestTimestamp timestamp of the first request in the batch (for end-to-end time measurement)
     * @return the saved document
     */
    public AggregatedContextDocument saveAggregatedContext(
            String triggerReason,
            List<SpanContext> spanContexts,
            List<String> requestIds,
            String masterTraceId,
            Instant firstRequestTimestamp) {
        /*
        *  -- NOTE --
        *  IMPORTANT!
        *  Process SpanContext before sending to MongoDB
        */
        // Convert SpanContext objects to SpanContextData with traceparent headers
        List<AggregatedContextDocument.SpanContextData> spanContextDataList = new ArrayList<>();
        TextMapSetter<Map<String, String>> mapSetter = Map::put;

        for (SpanContext spanContext : spanContexts) {
            // Create a context with the span context for propagation purpose
            Context context = Context.current().with(io.opentelemetry.api.trace.Span.wrap(spanContext));
            
            // Inject trace context into a carrier map to get traceparent
            Map<String, String> carrier = new HashMap<>();
            GlobalOpenTelemetry.getPropagators()
                    .getTextMapPropagator()
                    .inject(context, carrier, mapSetter);

            String traceparent = carrier.get("traceparent");

            // Create SpanContextData with all relevant information
            AggregatedContextDocument.SpanContextData spanContextData =
                    new AggregatedContextDocument.SpanContextData(
                            spanContext.getTraceId(),
                            spanContext.getSpanId(),
                            String.valueOf(spanContext.getTraceFlags().asByte()),
                            spanContext.getTraceState().isEmpty() ? null : spanContext.getTraceState().toString(),
                            traceparent
                    );

            spanContextDataList.add(spanContextData);
        }

        // Create and save the document
        AggregatedContextDocument document = new AggregatedContextDocument(
                triggerReason,
                new ArrayList<>(requestIds),
                spanContextDataList,
                masterTraceId,
                firstRequestTimestamp
        );

        AggregatedContextDocument saved = repository.save(document);
        logger.info("Saved aggregated context to MongoDB. Document ID: {}, Master Trace ID: {}, Request IDs: {}, Database: spanlink-demo, Collection: aggregated_contexts",
                saved.getId(), masterTraceId, requestIds);
        
        // Verify the document was actually saved
        if (saved.getId() == null) {
            logger.error("WARNING: Document saved but ID is null - document may not have been persisted!");
        } else {
            logger.debug("Document verification: ID={}, Timestamp={}", saved.getId(), saved.getTimestamp());
        }

        return saved;
    }
}

