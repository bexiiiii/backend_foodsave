-- Insert test stores with realistic data
INSERT INTO stores (name, description, address, phone, email, logo_url, opening_hours, closing_hours, category, status, active, owner_id, created_at, updated_at) VALUES
('FoodSave Central', 'Премиум боксы со скидкой до 70%. Широкий ассортимент качественных продуктов от ведущих производителей', 'г. Алматы, ул. Абая, 15', '+7 (727) 123-45-67', 'central@foodsave.kz', 'https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=400', '09:00', '21:00', 'Супермаркет', 'ACTIVE', true, 1, NOW(), NOW()),
('Eco Market', 'Экологически чистые боксы с органическими продуктами. Забота о здоровье и окружающей среде', 'г. Алматы, ул. Толе би, 89', '+7 (727) 234-56-78', 'eco@foodsave.kz', 'https://images.unsplash.com/photo-1542838132-92c53300491e?w=400', '08:00', '20:00', 'Эко-магазин', 'ACTIVE', true, 1, NOW(), NOW()),
('Fresh Bakery', 'Свежая выпечка и кондитерские изделия каждый день. Боксы с хлебобулочными изделиями', 'г. Алматы, пр. Достык, 234', '+7 (727) 345-67-89', 'bakery@foodsave.kz', 'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400', '07:00', '19:00', 'Пекарня', 'ACTIVE', true, 1, NOW(), NOW()),
('Meat Paradise', 'Качественное мясо и мясные деликатесы. Боксы с свежими мясными продуктами', 'г. Алматы, ул. Сейфуллина, 67', '+7 (727) 456-78-90', 'meat@foodsave.kz', 'https://images.unsplash.com/photo-1529193591184-b1d58069ecdd?w=400', '09:00', '18:00', 'Мясная лавка', 'ACTIVE', true, 1, NOW(), NOW()),
('Dairy Fresh', 'Молочные продукты высшего качества. Боксы с молочными деликатесами', 'г. Алматы, ул. Жандосова, 123', '+7 (727) 567-89-01', 'dairy@foodsave.kz', 'https://images.unsplash.com/photo-1550989460-0adf9ea622e2?w=400', '08:00', '20:00', 'Молочный магазин', 'ACTIVE', true, 1, NOW(), NOW()),
('Fruit Garden', 'Свежие фрукты и овощи напрямую с ферм. Боксы с сезонными продуктами', 'г. Алматы, ул. Розыбакиева, 45', '+7 (727) 678-90-12', 'fruits@foodsave.kz', 'https://images.unsplash.com/photo-1610832958506-aa56368176cf?w=400', '07:00', '21:00', 'Фрукты и овощи', 'ACTIVE', true, 1, NOW(), NOW());

-- Insert test categories
INSERT INTO categories (name, description, active, created_at, updated_at) VALUES
('Хлеб и выпечка', 'Свежий хлеб, булочки, торты и кондитерские изделия', true, NOW(), NOW()),
('Мясо и птица', 'Свежее мясо, птица и мясные деликатесы', true, NOW(), NOW()),
('Молочные продукты', 'Молоко, сыры, йогурты и другие молочные изделия', true, NOW(), NOW()),
('Фрукты и овощи', 'Свежие фрукты, овощи и зелень', true, NOW(), NOW()),
('Готовые блюда', 'Готовые к употреблению блюда и полуфабрикаты', true, NOW(), NOW()),
('Напитки', 'Соки, воды, газированные напитки', true, NOW(), NOW());

-- Insert test products for each store
-- FoodSave Central products
INSERT INTO products (name, description, price, original_price, category_id, store_id, stock_quantity, expiry_date, status, active, created_at, updated_at) VALUES
('Премиум бокс завтрак', 'Полный завтрак: круассаны, сыр, джем, сок', 1500.00, 2500.00, 1, 1, 20, '2025-07-20', 'ACTIVE', true, NOW(), NOW()),
('Семейный обеденный бокс', 'Готовые блюда на 4 персоны: суп, горячее, салат', 3500.00, 5000.00, 5, 1, 15, '2025-07-19', 'ACTIVE', true, NOW(), NOW()),
('Мясной деликатесный бокс', 'Отборные мясные изделия: стейк, колбасы, паштеты', 4200.00, 6000.00, 2, 1, 10, '2025-07-21', 'ACTIVE', true, NOW(), NOW()),

-- Eco Market products
INSERT INTO products (name, description, price, original_price, category_id, store_id, stock_quantity, expiry_date, status, active, created_at, updated_at) VALUES
('Органический овощной бокс', 'Сезонные органические овощи: морковь, капуста, свекла', 2000.00, 3000.00, 4, 2, 25, '2025-07-22', 'ACTIVE', true, NOW(), NOW()),
('Эко молочный набор', 'Органические молочные продукты: молоко, творог, сметана', 1800.00, 2800.00, 3, 2, 18, '2025-07-20', 'ACTIVE', true, NOW(), NOW()),

-- Fresh Bakery products
INSERT INTO products (name, description, price, original_price, category_id, store_id, stock_quantity, expiry_date, status, active, created_at, updated_at) VALUES
('Хлебный бокс дня', 'Свежий хлеб разных сортов: белый, черный, с семенами', 800.00, 1200.00, 1, 3, 30, '2025-07-19', 'ACTIVE', true, NOW(), NOW()),
('Сладкий кондитерский бокс', 'Пирожные, торты, эклеры - микс кондитерских изделий', 2200.00, 3500.00, 1, 3, 12, '2025-07-19', 'ACTIVE', true, NOW(), NOW()),

-- Meat Paradise products
INSERT INTO products (name, description, price, original_price, category_id, store_id, stock_quantity, expiry_date, status, active, created_at, updated_at) VALUES
('Барбекю мясной бокс', 'Мясо для барбекю: шашлык, колбаски, стейки', 5500.00, 8000.00, 2, 4, 8, '2025-07-21', 'ACTIVE', true, NOW(), NOW()),
('Деликатесный мясной бокс', 'Изысканные мясные деликатесы и копчености', 3800.00, 5500.00, 2, 4, 12, '2025-07-20', 'ACTIVE', true, NOW(), NOW()),

-- Dairy Fresh products
INSERT INTO products (name, description, price, original_price, category_id, store_id, stock_quantity, expiry_date, status, active, created_at, updated_at) VALUES
('Сырный гурман бокс', 'Коллекция изысканных сыров: твердые, мягкие, с плесенью', 3200.00, 4800.00, 3, 5, 15, '2025-07-23', 'ACTIVE', true, NOW(), NOW()),
('Молочный семейный бокс', 'Основные молочные продукты для всей семьи', 2100.00, 3200.00, 3, 5, 22, '2025-07-21', 'ACTIVE', true, NOW(), NOW()),

-- Insert test users first
INSERT INTO users (first_name, last_name, email, password, phone, profile_picture, address, role, enabled, active, created_at, updated_at) VALUES
('Dev', 'User', 'dev@example.com', '$2a$10$dXJ3SW6G7P4gg5H4q9A2zO6e2A0U4.YdM..pKd/QUVpyYzGQ5gUQe', '+7-777-123-4567', null, 'Алматы, ул. Тестовая 1', 'STORE_MANAGER', true, true, NOW(), NOW()),
('Super', 'Admin', 'admin@example.com', '$2a$10$dXJ3SW6G7P4gg5H4q9A2zO6e2A0U4.YdM..pKd/QUVpyYzGQ5gUQe', '+7-777-234-5678', null, 'Алматы, ул. Админская 2', 'SUPER_ADMIN', true, true, NOW(), NOW()),
('Store', 'Owner', 'owner@example.com', '$2a$10$dXJ3SW6G7P4gg5H4q9A2zO6e2A0U4.YdM..pKd/QUVpyYzGQ5gUQe', '+7-777-345-6789', null, 'Алматы, ул. Владельческая 3', 'STORE_OWNER', true, true, NOW(), NOW());

-- Fruit Garden products
INSERT INTO products (name, description, price, original_price, category_id, store_id, stock_quantity, expiry_date, status, active, created_at, updated_at) VALUES
('Тропический фруктовый бокс', 'Экзотические фрукты: манго, ананас, папайя, маракуйя', 2800.00, 4200.00, 4, 6, 20, '2025-07-20', 'ACTIVE', true, NOW(), NOW()),
('Сезонный овощной бокс', 'Свежие сезонные овощи прямо с грядки', 1600.00, 2400.00, 4, 6, 35, '2025-07-22', 'ACTIVE', true, NOW(), NOW()),
('Зеленый салатный бокс', 'Различные виды салатов и зелени для здорового питания', 1200.00, 1800.00, 4, 6, 28, '2025-07-19', 'ACTIVE', true, NOW(), NOW());
