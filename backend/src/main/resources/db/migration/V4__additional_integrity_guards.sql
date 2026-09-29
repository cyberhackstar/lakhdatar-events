-- Defense-in-depth business invariants for production data integrity.
ALTER TABLE orders ADD CONSTRAINT chk_order_total_positive CHECK (total_minor_units > 0);
ALTER TABLE order_items ADD CONSTRAINT chk_order_item_prices_non_negative CHECK (unit_price_minor >= 0 AND subtotal_minor >= 0);
ALTER TABLE ticket_reservations ADD CONSTRAINT chk_reservation_expiry_valid CHECK (expires_at > created_at);
ALTER TABLE ticket_types ADD CONSTRAINT chk_ticket_total_quantity_positive CHECK (total_quantity > 0);
ALTER TABLE ticket_types ADD CONSTRAINT chk_ticket_balance_non_negative CHECK (reserved_quantity >= 0 AND sold_quantity >= 0);
ALTER TABLE payments ADD CONSTRAINT chk_payment_currency_present CHECK (length(trim(currency)) > 0);
