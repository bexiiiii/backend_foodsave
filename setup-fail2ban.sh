#!/bin/bash

# 🛡️ Fail2ban Configuration для защиты FoodSave от атак
# Этот скрипт настраивает Fail2ban для блокировки вредоносных IP

echo "🛡️ Настройка Fail2ban для защиты FoodSave Backend"
echo "=================================================="

# Проверка что скрипт запущен от root
if [ "$EUID" -ne 0 ]; then 
    echo "❌ Запустите скрипт от root: sudo ./setup-fail2ban.sh"
    exit 1
fi

# Установка Fail2ban если не установлен
if ! command -v fail2ban-client &> /dev/null; then
    echo "📦 Установка Fail2ban..."
    apt-get update
    apt-get install -y fail2ban
fi

# Создание фильтра для RTSP атак
echo "📝 Создание фильтра для RTSP/DDoS атак..."
cat > /etc/fail2ban/filter.d/foodsave-attack.conf << 'EOF'
[Definition]
# Фильтр для блокировки RTSP атак и DDoS
failregex = ^.*Invalid character found in the HTTP protocol.*from IP <HOST>.*$
            ^.*Rate limit exceeded for IP: <HOST>.*$
            ^.*Blocked suspicious protocol from IP <HOST>.*$
            ^.*java\.lang\.IllegalArgumentException.*<HOST>.*$

ignoreregex =
EOF

# Создание jail для FoodSave
echo "🔒 Создание jail для FoodSave..."
cat > /etc/fail2ban/jail.d/foodsave.conf << 'EOF'
[foodsave-ddos]
enabled = true
port = 8080
protocol = tcp
filter = foodsave-attack
logpath = /var/log/foodsave/backend.log
maxretry = 10
findtime = 60
bantime = 3600
action = iptables-multiport[name=FoodSave, port="8080", protocol=tcp]

[foodsave-rtsp]
enabled = true
port = 8080
protocol = tcp
filter = foodsave-attack
logpath = /var/log/foodsave/backend.log
maxretry = 3
findtime = 300
bantime = 86400
action = iptables-multiport[name=FoodSave-RTSP, port="8080", protocol=tcp]
EOF

# Перезапуск Fail2ban
echo "🔄 Перезапуск Fail2ban..."
systemctl restart fail2ban
systemctl enable fail2ban

# Проверка статуса
echo ""
echo "✅ Fail2ban настроен!"
echo ""
echo "📊 Проверка статуса:"
fail2ban-client status

echo ""
echo "🔍 Проверить заблокированные IP для FoodSave:"
echo "   fail2ban-client status foodsave-ddos"
echo "   fail2ban-client status foodsave-rtsp"

echo ""
echo "📝 Разблокировать IP:"
echo "   fail2ban-client set foodsave-ddos unbanip <IP_ADDRESS>"

echo ""
echo "🎯 НАСТРОЙКИ:"
echo "   - DDoS защита: max 10 запросов/минуту → бан на 1 час"
echo "   - RTSP атаки: max 3 попытки/5 минут → бан на 24 часа"
echo "   - Логи: /var/log/foodsave/backend.log"
