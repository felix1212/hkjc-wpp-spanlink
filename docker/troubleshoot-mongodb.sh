#!/bin/bash
# MongoDB Change Stream Troubleshooting Script

echo "=== MongoDB Change Stream Troubleshooting ==="
echo ""

# Check if MongoDB is accessible
echo "1. Checking MongoDB connection..."
mongosh --host localhost:27017 --eval "db.runCommand('ping')" --quiet
if [ $? -eq 0 ]; then
    echo "✓ MongoDB is accessible"
else
    echo "✗ Cannot connect to MongoDB"
    exit 1
fi
echo ""

# Check replica set status
echo "2. Checking replica set status..."
mongosh --host localhost:27017 --eval "rs.status()" --quiet
if [ $? -eq 0 ]; then
    echo "✓ Replica set is initialized"
else
    echo "✗ Replica set is NOT initialized - this is required for change streams!"
    echo "  Run: mongosh --host localhost:27017 --eval \"rs.initiate({_id:'rs0',members:[{_id:0,host:'mongo1:27017'}]})\""
    exit 1
fi
echo ""

# Check if database exists
echo "3. Checking if database 'spanlink-demo' exists..."
mongosh --host localhost:27017 --eval "db.getMongo().getDBNames()" --quiet | grep -q "spanlink-demo"
if [ $? -eq 0 ]; then
    echo "✓ Database 'spanlink-demo' exists"
else
    echo "✗ Database 'spanlink-demo' does not exist"
fi
echo ""

# Check if collection exists and count documents
echo "4. Checking collection 'aggregated_contexts' and document count..."
DOC_COUNT=$(mongosh --host localhost:27017 spanlink-demo --eval "db.aggregated_contexts.countDocuments()" --quiet)
echo "  Document count: $DOC_COUNT"
if [ "$DOC_COUNT" -gt 0 ]; then
    echo "✓ Collection has documents"
    echo ""
    echo "5. Showing latest document..."
    mongosh --host localhost:27017 spanlink-demo --eval "db.aggregated_contexts.find().sort({_id:-1}).limit(1).pretty()" --quiet
else
    echo "✗ Collection is empty - no documents found"
fi
echo ""

# Test change stream manually
echo "6. Testing change stream (will wait 10 seconds for changes)..."
echo "   (In another terminal, insert a test document to see if change stream works)"
mongosh --host localhost:27017 spanlink-demo --eval "
var changeStream = db.aggregated_contexts.watch();
print('Change stream started. Waiting for changes...');
var startTime = new Date();
var timeout = 10000; // 10 seconds
var checkInterval = 1000; // 1 second

var interval = setInterval(function() {
    if (new Date() - startTime > timeout) {
        print('Timeout: No changes detected in 10 seconds');
        changeStream.close();
        clearInterval(interval);
        quit(0);
    }
}, checkInterval);

changeStream.on('change', function(change) {
    print('Change detected:');
    printjson(change);
    changeStream.close();
    clearInterval(interval);
    quit(0);
});
" --quiet

echo ""
echo "=== Troubleshooting Complete ==="

