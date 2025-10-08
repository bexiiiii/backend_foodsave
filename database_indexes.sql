-- FoodSave Database Optimization Indexes
-- Выполнить этот скрипт для добавления критичных индексов

-- Orders table indexes
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_store_id ON orders(store_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);
CREATE INDEX IF NOT EXISTS idx_orders_order_number ON orders(order_number);
CREATE INDEX IF NOT EXISTS idx_orders_user_created ON orders(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_store_status ON orders(store_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_store_created ON orders(store_id, created_at);

-- Products table indexes  
CREATE INDEX IF NOT EXISTS idx_products_store_id ON products(store_id);
CREATE INDEX IF NOT EXISTS idx_products_category_id ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_status ON products(status);
CREATE INDEX IF NOT EXISTS idx_products_active ON products(active);
CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);
CREATE INDEX IF NOT EXISTS idx_products_price ON products(price);
CREATE INDEX IF NOT EXISTS idx_products_discount ON products(discount_percentage);
CREATE INDEX IF NOT EXISTS idx_products_stock ON products(stock_quantity);
CREATE INDEX IF NOT EXISTS idx_products_store_active ON products(store_id, active);
CREATE INDEX IF NOT EXISTS idx_products_store_category ON products(store_id, category_id);
CREATE INDEX IF NOT EXISTS idx_products_search ON products USING GIN(to_tsvector('russian', name || ' ' || COALESCE(description, '')));

-- Order Items table indexes
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items(product_id);

-- Users table indexes
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_telegram_id ON users(telegram_id);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_active ON users(active);

-- Stores table indexes
CREATE INDEX IF NOT EXISTS idx_stores_owner_id ON stores(owner_id);
CREATE INDEX IF NOT EXISTS idx_stores_manager_id ON stores(manager_id);
CREATE INDEX IF NOT EXISTS idx_stores_active ON stores(active);
CREATE INDEX IF NOT EXISTS idx_stores_location ON stores USING GIST(ST_Point(longitude, latitude)) WHERE longitude IS NOT NULL AND latitude IS NOT NULL;

-- Categories table indexes
CREATE INDEX IF NOT EXISTS idx_categories_name ON categories(name);
CREATE INDEX IF NOT EXISTS idx_categories_active ON categories(active);

-- Reviews table indexes (если есть)
CREATE INDEX IF NOT EXISTS idx_reviews_product_id ON reviews(product_id) WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'reviews');
CREATE INDEX IF NOT EXISTS idx_reviews_user_id ON reviews(user_id) WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'reviews');
CREATE INDEX IF NOT EXISTS idx_reviews_rating ON reviews(rating) WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'reviews');

-- Cart table indexes (если есть)
CREATE INDEX IF NOT EXISTS idx_cart_user_id ON cart(user_id) WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'cart');
CREATE INDEX IF NOT EXISTS idx_cart_items_cart_id ON cart_items(cart_id) WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'cart_items');

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_products_search_combo ON products(store_id, active, category_id) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_orders_user_status_date ON orders(user_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_products_store_stock_active ON products(store_id, stock_quantity, active) WHERE active = true;

-- Partial indexes for better performance
CREATE INDEX IF NOT EXISTS idx_products_low_stock ON products(stock_quantity) WHERE stock_quantity <= 10 AND active = true;
CREATE INDEX IF NOT EXISTS idx_products_discounted ON products(discount_percentage) WHERE discount_percentage > 0 AND active = true;
CREATE INDEX IF NOT EXISTS idx_orders_recent ON orders(created_at) WHERE created_at >= CURRENT_DATE - INTERVAL '30 days';

-- Full-text search optimization
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_products_name_trgm ON products USING GIN(name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_products_description_trgm ON products USING GIN(description gin_trgm_ops);

COMMENT ON INDEX idx_orders_user_id IS 'Быстрый поиск заказов пользователя';
COMMENT ON INDEX idx_products_search IS 'Полнотекстовый поиск по продуктам';
COMMENT ON INDEX idx_products_search_combo IS 'Комбинированный индекс для фильтрации продуктов';