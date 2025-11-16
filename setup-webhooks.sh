#!/bin/bash

echo "=== Setting up Telegram Webhooks ==="

# Client bot webhook
echo "Setting up client bot webhook..."
curl -X POST "https://api.telegram.org/bot7773680612:AAFO9qHZyUxhR03o11__IN2N3BhNzuLa2Ek/setWebhook" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://foodsave.kz/api/telegram/webhook"}' | python3 -m json.tool

echo ""
echo "Client bot webhook info:"
curl -s "https://api.telegram.org/bot7773680612:AAFO9qHZyUxhR03o11__IN2N3BhNzuLa2Ek/getWebhookInfo" | python3 -m json.tool

echo ""
echo "==="
echo ""

# Manager bot webhook
echo "Setting up manager bot webhook..."
curl -X POST "https://api.telegram.org/bot8489367964:AAFuCIQxj-jPJJgEjYqtOH72e0rbv6iB11E/setWebhook" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://foodsave.kz/api/telegram/webhook/manager"}' | python3 -m json.tool

echo ""
echo "Manager bot webhook info:"
curl -s "https://api.telegram.org/bot8489367964:AAFuCIQxj-jPJJgEjYqtOH72e0rbv6iB11E/getWebhookInfo" | python3 -m json.tool

echo ""
echo "=== Setup complete ==="
