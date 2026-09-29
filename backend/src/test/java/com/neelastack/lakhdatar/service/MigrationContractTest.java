package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MigrationContractTest {
    @Test
    void orderStatusConstraintMatchesDomainEnum() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V7__correct_order_status_constraint.sql"));
        int start = sql.indexOf("chk_order_status");
        org.junit.jupiter.api.Assertions.assertTrue(start >= 0, "order status constraint must exist in V7");
        int checkStart = sql.indexOf("CHECK (status IN (", start);
        int end = sql.indexOf("));", checkStart);
        org.junit.jupiter.api.Assertions.assertTrue(checkStart >= 0 && end > checkStart, "order status check expression must be parseable");
        String expression = sql.substring(checkStart, end);
        var names = Pattern.compile("'([A-Z_]+)'").matcher(expression);
        java.util.Set<String> actual = new java.util.HashSet<>();
        while (names.find()) actual.add(names.group(1));
        var expected = Arrays.stream(Enums.OrderStatus.values()).map(Enum::name).collect(Collectors.toSet());
        assertEquals(expected, actual);
    }
}
