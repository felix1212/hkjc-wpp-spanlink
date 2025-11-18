package com.example.spanlink_demo.repository;

import com.example.spanlink_demo.model.AggregatedContextDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AggregatedContextRepository extends MongoRepository<AggregatedContextDocument, String> {
}

