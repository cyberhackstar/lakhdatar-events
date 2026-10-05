package com.neelastack.lakhdatar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Emits deploy/restart lifecycle markers that make rollout and crash diagnosis straightforward. */
@Component
public class ApplicationLifecycleLogging {
    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleLogging.class);

    @Value("${spring.application.version:unknown}") private String version;
    @Value("${app.worker.enabled:true}") private boolean workerEnabled;
    @Value("${app.observability.logs.enabled:true}") private boolean logsEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        EnterpriseLog.info(log, "application.ready", "event.category", "lifecycle",
                "application.version", version, "worker.enabled", workerEnabled, "structured_logs.enabled", logsEnabled);
    }

    @EventListener(ContextClosedEvent.class)
    public void stopping() {
        EnterpriseLog.info(log, "application.stopping", "event.category", "lifecycle", "application.version", version);
    }
}
