package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevents a collision with Spring Boot's own requestContextFilter bean. */
class RequestContextFilterContractTest {
    @Test
    void customRequestContextFilterUsesUniqueBeanName() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/RequestContextFilter.java"));
        assertTrue(source.contains("@Component(\"lakhdatarRequestContextFilter\")"));
    }
}
