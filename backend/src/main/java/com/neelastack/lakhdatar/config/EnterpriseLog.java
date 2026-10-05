package com.neelastack.lakhdatar.config;

import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.Locale;

/**
 * Small structured logging facade for operational/business events.
 *
 * <p>Use low-cardinality keys and never pass secrets, credentials, tokens,
 * full request bodies, customer contact details, QR credentials or provider
 * signatures. Spring Boot renders the structured event as ECS JSON.</p>
 *
 * <p>The facade also applies a defensive redaction/size boundary so a future
 * log statement cannot accidentally turn an operator log into a secret dump.</p>
 */
public final class EnterpriseLog {
    private static final int MAX_VALUE_LENGTH = 512;
    private EnterpriseLog() {}

    public static void info(Logger log, String action, Object... fields) {
        write(log.atInfo(), action, fields);
    }

    public static void debug(Logger log, String action, Object... fields) {
        write(log.atDebug(), action, fields);
    }

    public static void warn(Logger log, String action, Object... fields) {
        write(log.atWarn(), action, fields);
    }

    public static void error(Logger log, String action, Throwable error, Object... fields) {
        LoggingEventBuilder builder = log.atError().setMessage(action);
        builder.addKeyValue("event.action", action);
        add(builder, fields);
        if (error != null) builder.setCause(error);
        builder.log();
    }

    private static void write(LoggingEventBuilder builder, String action, Object... fields) {
        builder.setMessage(action);
        builder.addKeyValue("event.action", action);
        add(builder, fields);
        builder.log();
    }

    private static void add(LoggingEventBuilder builder, Object... fields) {
        if (fields == null) return;
        for (int i = 0; i + 1 < fields.length; i += 2) {
            Object keyObject = fields[i];
            if (keyObject == null) continue;
            String key = String.valueOf(keyObject);
            builder.addKeyValue(key, sanitize(key, fields[i + 1]));
        }
    }

    private static Object sanitize(String key, Object value) {
        if (value == null) return null;
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (normalized.contains("password") || normalized.contains("token") || normalized.contains("secret")
                || normalized.contains("authorization") || normalized.contains("cookie") || normalized.contains("signature")
                || normalized.contains("credential") || normalized.contains("email") || normalized.contains("phone")
                || normalized.contains("customer.name") || normalized.contains("attendee") || normalized.contains("error.message")) {
            return "[REDACTED]";
        }
        if (normalized.contains("request.body") || normalized.contains("response.body") || normalized.endsWith(".payload")) {
            return "[REDACTED]";
        }
        if (value instanceof CharSequence text) {
            return text.length() > MAX_VALUE_LENGTH ? text.subSequence(0, MAX_VALUE_LENGTH) + "…" : text.toString();
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character || value instanceof Enum<?>) {
            return value;
        }
        // Never serialize arbitrary objects/DTOs into operator logs. This prevents an
        // innocent future log call from dumping a request, entity, credential holder or token object.
        return "[REDACTED]";
    }
}
