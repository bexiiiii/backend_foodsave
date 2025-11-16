# 🚀 План Оптимизации FoodSave Backend

## ✅ Уже Реализовано

### 1. Redis Кеширование
- **Исправлена сериализация**: GenericJackson2JsonRedisSerializer с type information
- **Многоуровневое кеширование**:
  - 2 часа: categories, storesList (статические данные)
  - 30 минут: products, stores (динамические данные)
  - 5 минут: userOrders, orderStats (часто обновляемые)
  - 1 минута: productStock (реал-тайм данные)

### 2. Database Indexes
```sql
-- Уже созданы индексы в database_indexes.sql
CREATE INDEX idx_product_store_id ON products(store_id);
CREATE INDEX idx_product_category_id ON products(category_id);
CREATE INDEX idx_product_discount ON products(discount_percentage);
CREATE INDEX idx_product_expiry ON products(expiry_date);
CREATE INDEX idx_order_user_id ON orders(user_id);
CREATE INDEX idx_order_status ON orders(status);
CREATE INDEX idx_order_created_at ON orders(created_at);
```

### 3. JPA Оптимизации
- EntityGraph для избежания N+1 queries
- Batch fetching: `spring.jpa.properties.hibernate.default_batch_fetch_size=16`
- Connection pooling: HikariCP с 20 connections

### 4. GZIP Compression
```properties
server.compression.enabled=true
server.compression.mime-types=text/html,application/json,application/xml
server.compression.min-response-size=1024
```

## 🎯 Дополнительные Оптимизации (Рекомендации)

### 1. CDN для Статических Файлов
```yaml
# Использовать CloudFlare/AWS CloudFront для:
- Изображения продуктов (/uploads/products/*)
- Статические assets
- Frontend bundle
```

### 2. Database Query Optimization
```java
// Использовать проекции вместо полных Entity
public interface ProductSummary {
    Long getId();
    String getName();
    Double getPrice();
    String getFirstImage();
}

@Query("SELECT p.id as id, p.name as name, p.price as price, p.images[1] as firstImage FROM Product p")
List<ProductSummary> findAllSummaries();
```

### 3. Async Processing
```java
@Service
public class NotificationService {
    
    @Async
    public CompletableFuture<Void> sendOrderNotification(Order order) {
        // Асинхронная отправка уведомлений
        telegramService.sendNotification(order);
        return CompletableFuture.completedFuture(null);
    }
}
```

### 4. Response Pagination Optimization
```java
// Использовать cursor-based pagination для больших списков
@GetMapping("/products")
public ResponseEntity<List<ProductDTO>> getProducts(
    @RequestParam(required = false) Long cursor,
    @RequestParam(defaultValue = "20") int limit
) {
    List<ProductDTO> products = productService.getProductsAfterId(cursor, limit);
    return ResponseEntity.ok(products);
}
```

### 5. Cache Warming
```java
@Component
@RequiredArgsConstructor
public class CacheWarmer {
    
    private final StoreService storeService;
    private final ProductService productService;
    
    @Scheduled(cron = "0 0 */2 * * *") // Каждые 2 часа
    public void warmCache() {
        log.info("Warming up cache...");
        storeService.getActiveStores();
        productService.getAllCategories();
        log.info("Cache warmed up successfully");
    }
}
```

### 6. Database Connection Pool Monitoring
```properties
# Добавить метрики для мониторинга
spring.datasource.hikari.register-mbeans=true
management.metrics.enable.hikari=true
```

### 7. Frontend Optimization
```javascript
// Использовать React Query или SWR для кеширования на клиенте
const { data, error } = useSWR('/api/products', fetcher, {
  revalidateOnFocus: false,
  dedupingInterval: 60000, // 1 минута
  refreshInterval: 300000  // 5 минут
});
```

### 8. Image Optimization
```bash
# Автоматическое сжатие изображений при загрузке
- WebP формат вместо JPEG/PNG
- Ленивая загрузка (lazy loading)
- Разные размеры (thumbnail, medium, large)
```

### 9. Database Read Replicas
```yaml
# Для высоконагруженных систем
spring:
  datasource:
    primary:
      url: jdbc:postgresql://master:5432/foodsave
    readonly:
      url: jdbc:postgresql://replica:5432/foodsave
```

### 10. Rate Limiting
```java
@Component
public class RateLimitFilter implements Filter {
    
    private final RateLimiter rateLimiter = RateLimiter.create(100.0); // 100 req/sec
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        if (rateLimiter.tryAcquire()) {
            chain.doFilter(request, response);
        } else {
            ((HttpServletResponse) response).setStatus(429); // Too Many Requests
        }
    }
}
```

## 📊 Метрики Производительности

### Целевые Показатели:
- **API Response Time**: < 200ms (90th percentile)
- **Database Query Time**: < 50ms (average)
- **Cache Hit Rate**: > 80%
- **Page Load Time**: < 2 seconds
- **Time To First Byte (TTFB)**: < 100ms

## 🔍 Мониторинг

### Инструменты:
1. **Spring Boot Actuator**: `/actuator/metrics`
2. **Redis Monitor**: `redis-cli monitor`
3. **Database Slow Query Log**: PostgreSQL slow query log
4. **Application Performance Monitoring (APM)**: New Relic/DataDog

### Ключевые Метрики:
```bash
# Проверка Redis
redis-cli INFO stats
redis-cli INFO memory

# Проверка Database
SELECT * FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 10;

# Проверка HikariCP
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

## 🛠️ Шаги Деплоя Оптимизаций

1. **Очистить Redis кеш**:
```bash
redis-cli FLUSHDB
```

2. **Пересобрать проект**:
```bash
mvn clean package -DskipTests
```

3. **Деплой на сервер**:
```bash
scp target/backend-0.0.1-SNAPSHOT.jar server:/app/
```

4. **Перезапуск сервиса**:
```bash
sudo systemctl restart foodsave-backend
```

5. **Мониторинг логов**:
```bash
tail -f /var/log/foodsave/server.log
```

## 🎓 Best Practices

1. **Всегда чистите кеш** после изменения структуры DTO
2. **Используйте @Transactional(readOnly = true)** для read-only операций
3. **Избегайте N+1 queries** - используйте EntityGraph
4. **Логируйте медленные запросы** для анализа
5. **Мониторьте memory usage** Redis и Database

## 📈 Ожидаемые Результаты

После всех оптимизаций:
- ⚡ **50-70% быстрее** загрузка списка продуктов
- 💾 **80%+ cache hit rate** для популярных данных
- 🗄️ **Меньше нагрузки** на базу данных
- 🚀 **Лучше UX** для пользователей
