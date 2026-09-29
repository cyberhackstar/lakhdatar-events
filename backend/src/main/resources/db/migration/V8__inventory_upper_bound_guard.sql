-- Prevent an application defect from creating inventory above configured capacity.
ALTER TABLE ticket_types
    ADD CONSTRAINT chk_ticket_inventory_not_over_capacity
    CHECK (reserved_quantity + sold_quantity <= total_quantity);
