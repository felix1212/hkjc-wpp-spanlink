// MongoDB Change Stream Test Script
// Run with: mongosh --host localhost:27017 spanlink-demo test-change-stream.js

print("=== Testing MongoDB Change Stream ===");
print("");

// Check replica set status
print("1. Checking replica set status...");
try {
    var rsStatus = rs.status();
    print("✓ Replica set is initialized: " + rsStatus.set);
} catch (e) {
    print("✗ ERROR: Replica set is NOT initialized!");
    print("  Error: " + e.message);
    print("  Change streams require a replica set. Please initialize it first.");
    quit(1);
}
print("");

// Check collection
print("2. Checking collection 'aggregated_contexts'...");
var collection = db.aggregated_contexts;
var docCount = collection.countDocuments();
print("  Document count: " + docCount);
print("");

// Show latest document if exists
if (docCount > 0) {
    print("3. Latest document:");
    var latest = collection.find().sort({_id: -1}).limit(1).toArray()[0];
    printjson(latest);
    print("");
}

// Test change stream
print("4. Starting change stream listener (will wait 30 seconds for changes)...");
print("   Insert a test document in another terminal to test:");
print("   db.aggregated_contexts.insertOne({test: 'change stream test', timestamp: new Date()})");
print("");

var changeStream = collection.watch();
var startTime = new Date();
var timeout = 30000; // 30 seconds
var received = false;

changeStream.on('change', function(change) {
    received = true;
    print("=== CHANGE DETECTED ===");
    print("Operation Type: " + change.operationType);
    print("Full Document:");
    printjson(change);
    print("=======================");
    changeStream.close();
    quit(0);
});

// Timeout handler
setTimeout(function() {
    if (!received) {
        print("Timeout: No changes detected in 30 seconds.");
        print("This could mean:");
        print("  1. No documents were inserted during the test period");
        print("  2. Change stream is not working (check replica set)");
        print("  3. The downstream app might have the same issue");
        changeStream.close();
        quit(0);
    }
}, timeout);

print("Waiting for changes... (Press Ctrl+C to stop)");

