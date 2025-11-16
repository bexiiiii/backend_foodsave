#!/bin/bash
set -e

echo "=== Deploying FoodSave Backend ==="

# Upload JAR
echo "Uploading JAR..."
scp target/backend-0.0.1-SNAPSHOT.jar root@136.243.45.111:/var/www/foodsave/

# Restart service
echo "Restarting backend service..."
ssh root@136.243.45.111 "systemctl restart foodsave-backend"

# Wait and check status
sleep 3
ssh root@136.243.45.111 "systemctl status foodsave-backend --no-pager -l"

echo "=== Deployment complete ==="
echo "Check logs: ssh root@136.243.45.111 'journalctl -u foodsave-backend -f'"
