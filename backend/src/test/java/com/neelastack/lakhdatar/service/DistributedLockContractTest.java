package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedLockContractTest {
    @Test
    void longRunningDistributedJobsRenewTheirLeaseWithoutUnlockingAnotherOwner() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/DistributedLockService.java"));
        assertTrue(source.contains("RENEW_SCRIPT"));
        assertTrue(source.contains("pexpire"));
        assertTrue(source.contains("redis.call('get', KEYS[1]) == ARGV[1]"));
        assertTrue(source.contains("lakhdatar-lock-renewer"));
    }
}
