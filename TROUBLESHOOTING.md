# MongoDB Change Stream Troubleshooting Guide

## Issue: Downstream app not receiving change stream events

### Quick Verification Steps

#### 1. Verify Replica Set is Initialized
Change streams **require** a replica set, even for single-node setups.

```bash
# Connect to MongoDB
mongosh --host localhost:27017

# Check replica set status
rs.status()
```

**Expected output:** Should show replica set `rs0` with status `PRIMARY`

**If not initialized:**
```bash
rs.initiate({_id:'rs0',members:[{_id:0,host:'mongo1:27017'}]})
```

#### 2. Verify Documents are Being Written

```bash
# Connect to the database
mongosh --host localhost:27017 spanlink-demo

# Check document count
db.aggregated_contexts.countDocuments()

# View latest documents
db.aggregated_contexts.find().sort({_id:-1}).limit(5).pretty()
```

#### 3. Test Change Stream Manually

**Option A: Using the test script**
```bash
mongosh --host localhost:27017 spanlink-demo docker/test-change-stream.js
```

**Option B: Manual test in mongosh**
```javascript
// In mongosh
use spanlink-demo

// Start watching
var changeStream = db.aggregated_contexts.watch();

// In another terminal, insert a test document
db.aggregated_contexts.insertOne({test: "change stream test", timestamp: new Date()})

// You should see the change event in the first terminal
```

#### 4. Verify Downstream App Configuration

Check that your downstream app is:
- ✅ Connected to the correct MongoDB instance (`localhost:27017` or `mongo1:27017` in Docker)
- ✅ Watching the correct database: `spanlink-demo`
- ✅ Watching the correct collection: `aggregated_contexts`
- ✅ Using a replica set connection string (not standalone)
- ✅ Started **before** documents are written (or using `startAfter`/`resumeAfter`)

**Example correct connection string:**
```
mongodb://localhost:27017/spanlink-demo?replicaSet=rs0
```

**Example change stream code (Node.js):**
```javascript
const collection = db.collection('aggregated_contexts');
const changeStream = collection.watch();
changeStream.on('change', (change) => {
    console.log('Change detected:', change);
});
```

**Example change stream code (Java/Spring):**
```java
MongoCollection<Document> collection = mongoDatabase.getCollection("aggregated_contexts");
MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = 
    collection.watch().cursor();
while (cursor.hasNext()) {
    ChangeStreamDocument<Document> change = cursor.next();
    System.out.println("Change detected: " + change);
}
```

### Common Issues and Solutions

#### Issue 1: Replica Set Not Initialized
**Symptom:** Change stream throws error about replica set

**Solution:**
```bash
# In Docker, check if mongo-init service ran successfully
docker-compose logs mongo-init

# Manually initialize if needed
docker exec -it mongo1 mongosh --eval "rs.initiate({_id:'rs0',members:[{_id:0,host:'mongo1:27017'}]})"
```

#### Issue 2: Downstream App Started After Documents Were Written
**Symptom:** App starts but doesn't see existing documents

**Solution:** Change streams only capture **new** changes. To process existing documents:
- Use `startAfter` with the latest resume token
- Or query existing documents separately, then start change stream

#### Issue 3: Wrong Database/Collection Name
**Symptom:** No errors but no events received

**Solution:** Verify exact names:
- Database: `spanlink-demo` (case-sensitive)
- Collection: `aggregated_contexts` (case-sensitive)

#### Issue 4: Connection String Issues
**Symptom:** Cannot connect or change stream fails

**Solution:** Ensure connection string includes replica set:
```
mongodb://localhost:27017/spanlink-demo?replicaSet=rs0
```

Not:
```
mongodb://localhost:27017/spanlink-demo  ❌ (missing replicaSet parameter)
```

### Debugging Commands

#### Check if documents exist:
```bash
mongosh --host localhost:27017 spanlink-demo --eval "db.aggregated_contexts.find().count()"
```

#### View latest document structure:
```bash
mongosh --host localhost:27017 spanlink-demo --eval "db.aggregated_contexts.find().sort({_id:-1}).limit(1).pretty()"
```

#### Monitor change stream in real-time:
```bash
mongosh --host localhost:27017 spanlink-demo --eval "
var stream = db.aggregated_contexts.watch();
stream.on('change', function(change) { printjson(change); });
"
```

#### Check MongoDB logs:
```bash
docker logs mongo1
```

### Verification Checklist

- [ ] Replica set is initialized (`rs.status()` shows PRIMARY)
- [ ] Documents exist in `spanlink-demo.aggregated_contexts`
- [ ] Manual change stream test works
- [ ] Downstream app uses correct connection string with `replicaSet=rs0`
- [ ] Downstream app watches correct database and collection
- [ ] Downstream app started before or uses resume tokens
- [ ] Network connectivity between apps and MongoDB is working

### Still Not Working?

1. **Enable detailed logging** in your downstream app
2. **Check MongoDB logs** for errors: `docker logs mongo1`
3. **Verify network connectivity** between apps
4. **Test with a simple insert** to isolate the issue
5. **Check MongoDB version compatibility** with change streams

