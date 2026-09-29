package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InventoryConstraintContractTest {
    @Test
    void inventoryCeilingGuardIsPresent() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V8__inventory_upper_bound_guard.sql"));
        assertTrue(sql.contains("chk_ticket_inventory_not_over_capacity"));
        assertTrue(sql.contains("reserved_quantity + sold_quantity <= total_quantity"));
    }
}
