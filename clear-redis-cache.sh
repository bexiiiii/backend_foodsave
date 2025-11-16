#!/bin/bash

# Clear Redis cache script - SIMPLE VERSION
# Just flush all keys from current database

echo "🔄 Clearing Redis cache..."

# Try to connect and flush
redis-cli FLUSHDB

if [ $? -eq 0 ]; then
    echo "✅ Cache cleared successfully!"
else
    echo "❌ Failed to clear cache. Make sure Redis is running."
    echo "Try manually: redis-cli FLUSHDB"
fi

