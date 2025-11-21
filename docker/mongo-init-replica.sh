#!/bin/bash
set -e

# Start mongod in the background
mongod --replSet rs0 --bind_ip_all --port 27017 &
MONGO_PID=$!

# Function to cleanup on exit
cleanup() {
  echo "Shutting down MongoDB..."
  kill $MONGO_PID 2>/dev/null || true
  wait $MONGO_PID 2>/dev/null || true
}
trap cleanup EXIT

# Wait for MongoDB to be ready
echo "Waiting for MongoDB to start..."
for i in {1..30}; do
  if mongo --eval "db.runCommand('ping').ok" localhost:27017/test --quiet 2>/dev/null; then
    echo "MongoDB is ready!"
    break
  fi
  if [ $i -eq 30 ]; then
    echo "MongoDB failed to start within 60 seconds"
    exit 1
  fi
  echo "MongoDB is not ready yet... ($i/30)"
  sleep 2
done

# Initialize replica set if not already initialized
echo "Checking replica set status..."
RS_STATUS=$(mongo --eval "try { rs.status().ok } catch(e) { 0 }" localhost:27017/test --quiet 2>/dev/null || echo "0")

if [ "$RS_STATUS" != "1" ]; then
  echo "Initializing replica set..."
  mongo --eval "rs.initiate({_id: 'rs0', members: [{ _id: 0, host: 'localhost:27017'}]})" localhost:27017/test
  echo "Waiting for replica set to be ready..."
  sleep 5
  mongo --eval "rs.status()" localhost:27017/test
  echo "Replica set initialized!"
else
  echo "Replica set already initialized."
fi

# Keep mongod running in the foreground
wait $MONGO_PID

