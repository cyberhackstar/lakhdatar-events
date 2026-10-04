package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DeploymentDotenvContractTest {
    @Test
    void deployScriptDoesNotSourceTheFullDotenvFile() throws Exception {
        String deploy = Files.readString(Path.of("../infra/deploy/deploy.sh"));
        String env = Files.readString(Path.of("../.env.example"));
        assertFalse(deploy.contains(". \"$ROOT/.env\""));
        assertTrue(deploy.contains("dotenv_get()"));
        assertTrue(env.contains("MAIL_FROM=\"Neelastack Events <tickets@yourdomain.com>\""));
    }
}
