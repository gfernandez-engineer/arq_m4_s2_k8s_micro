-- Datos de prueba
INSERT INTO orders (order_number, user_id, status, total_amount) VALUES
('ORD-2025-001', 1, 'CONFIRMED', 2849.97),
('ORD-2025-002', 2, 'PENDING', 1199.98),
('ORD-2025-003', 1, 'SHIPPED', 149.99);

INSERT INTO order_items (order_id, product_id, quantity, unit_price, subtotal) VALUES
(1, 1, 1, 1299.99, 1299.99),
(1, 2, 1, 999.99, 999.99),
(1, 3, 1, 399.99, 399.99),
(2, 4, 1, 799.99, 799.99),
(2, 5, 1, 399.00, 399.00),
(3, 7, 1, 149.99, 149.99);
