-- Сначала создаем пользователя-администратора
INSERT INTO users (first_name, last_name, email, password, phone, role, enabled, active, created_at, updated_at) VALUES 
('Super', 'Admin', 'admin@foodsave.kz', '$2a$10$N.zmdr9k7uOCQb0VEcs/aO/RTRcxAVR.J7nZN0fLFq9Bx6hzQrU6W', '+7 777 123 4567', 'SUPER_ADMIN', true, true, NOW(), NOW());

-- Создаем категории
INSERT INTO categories (name, description, image_url, active, created_at, updated_at) VALUES 
('Боксы с едой', 'Готовые боксы с разнообразными блюдами', 'https://images.unsplash.com/photo-1546039907-7fa05f864c02?w=400', true, NOW(), NOW()),
('Выпечка', 'Свежая выпечка и кондитерские изделия', 'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400', true, NOW(), NOW()),
('Мясо и деликатесы', 'Качественные мясные продукты', 'https://images.unsplash.com/photo-1529193591184-b1d58069ecdd?w=400', true, NOW(), NOW()),
('Молочные продукты', 'Свежие молочные продукты', 'https://images.unsplash.com/photo-1550989460-0adf9ea622e2?w=400', true, NOW(), NOW()),
('Фрукты и овощи', 'Свежие фрукты и овощи', 'https://images.unsplash.com/photo-1610832958506-aa56368176cf?w=400', true, NOW(), NOW());

-- Теперь создаем магазины
INSERT INTO stores (name, description, address, phone, email, logo_url, opening_hours, closing_hours, category, status, active, owner_id, created_at, updated_at) VALUES 
('FoodSave Central', 'Премиум боксы со скидкой до 70%. Широкий ассортимент качественных продуктов от ведущих производителей', 'г. Алматы, ул. Абая, 15', '+7 (727) 123-45-67', 'central@foodsave.kz', 'https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=400', '09:00', '21:00', 'Супермаркет', 'ACTIVE', true, 1, NOW(), NOW()),
('Eco Market', 'Экологически чистые боксы с органическими продуктами. Забота о здоровье и окружающей среде', 'г. Алматы, ул. Толе би, 89', '+7 (727) 234-56-78', 'eco@foodsave.kz', 'https://images.unsplash.com/photo-1542838132-92c53300491e?w=400', '08:00', '20:00', 'Эко-магазин', 'ACTIVE', true, 1, NOW(), NOW()),
('Fresh Bakery', 'Свежая выпечка и кондитерские изделия каждый день. Боксы с хлебобулочными изделиями', 'г. Алматы, пр. Достык, 234', '+7 (727) 345-67-89', 'bakery@foodsave.kz', 'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400', '07:00', '19:00', 'Пекарня', 'ACTIVE', true, 1, NOW(), NOW());

-- Создаем продукты (убираем discounted_price, используем только существующие колонки)
INSERT INTO products (name, description, price, quantity, image_url, featured, active, category_id, store_id, created_at, updated_at) VALUES 
('Микс бокс "Завтрак"', 'Сытный завтрак: каша, фрукты, йогурт, сок', 2500.00, 10, 'https://images.unsplash.com/photo-1551782450-17144efb9c50?w=400', true, true, 1, 1, NOW(), NOW()),
('Здоровый обед', 'Сбалансированный обед: салат, суп, основное блюдо', 3500.00, 15, 'https://images.unsplash.com/photo-1540420773420-3366772f4999?w=400', true, true, 1, 2, NOW(), NOW()),
('Эко бокс "Фреш"', 'Органические фрукты и овощи сезона', 4000.00, 8, 'https://images.unsplash.com/photo-1610832958506-aa56368176cf?w=400', true, true, 5, 2, NOW(), NOW()),
('Хлебная корзина', 'Ассорти свежей выпечки: хлеб, круассаны, булочки', 1800.00, 20, 'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400', false, true, 2, 3, NOW(), NOW());
