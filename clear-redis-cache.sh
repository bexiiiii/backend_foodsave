#!/bin/bash

# Clear Redis cache script
# This script clears all cached data to fix stale cache issues

echo "🔄 Clearing Redis cache..."

# Check if redis-cli is available
if ! command -v redis-cli &> /dev/null; then
    echo "❌ redis-cli not found. Please install redis-tools:"
    echo "   Ubuntu/Debian: sudo apt-get install redis-tools"
    echo "   macOS: brew install redis"
    exit 1
fi

# Get Redis connection info from environment or use defaults
REDIS_HOST=${REDIS_HOST:-localhost}
REDIS_PORT=${REDIS_PORT:-6379}
REDIS_PASSWORD=${REDIS_PASSWORD:-}

# Build redis-cli command
REDIS_CMD="redis-cli -h $REDIS_HOST -p $REDIS_PORT"
if [ -n "$REDIS_PASSWORD" ]; then
    REDIS_CMD="$REDIS_CMD -a $REDIS_PASSWORD"
fi

# Test connection
echo "📡 Testing Redis connection..."
if ! $REDIS_CMD ping > /dev/null 2>&1; then
    echo "❌ Cannot connect to Redis at $REDIS_HOST:$REDIS_PORT"
    exit 1
fi

echo "✅ Connected to Redis"

# Clear all cache keys
echo "🗑️  Clearing all cache keys..."
$REDIS_CMD KEYS "products::*" | xargs -r $REDIS_CMD DEL
$REDIS_CMD KEYS "productsByStore::*" | xargs -r $REDIS_CMD DEL
$REDIS_CMD KEYS "featuredProducts::*" | xargs -r $REDIS_CMD DEL
$REDIS_CMD KEYS "discountedProducts::*" | xargs -r $REDIS_CMD DEL
$REDIS_CMD KEYS "categories::*" | xargs -r $REDIS_CMD DEL
$REDIS_CMD KEYS "stores::*" | xargs -r $REDIS_CMD DEL
$REDIS_CMD KEYS "userOrders::*" | xargs -r $REDIS_CMD DEL
$REDIS_CMD KEYS "orderStats::*" | xargs -r $REDIS_CMD DEL

echo "✅ Cache cleared successfully!"
echo ""
echo "📊 Current cache statistics:"
$REDIS_CMD INFO stats | grep keyspace
$REDIS_CMD DBSIZE

echo ""
echo "✅ Done! Please restart your application for changes to take effect."
