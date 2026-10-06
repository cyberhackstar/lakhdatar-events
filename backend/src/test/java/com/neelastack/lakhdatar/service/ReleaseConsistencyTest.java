package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseConsistencyTest {
    @Test
    void repositoryVersionMatchesBuildManifests() throws Exception {
        String version = Files.readString(Path.of("../VERSION")).trim();
        String pom = Files.readString(Path.of("pom.xml"));
        String packageJson = Files.readString(Path.of("../frontend/package.json"));
        String pomVersion = Pattern.compile("<artifactId>lakhdatar-events</artifactId>\\s*<version>([^<]+)</version>", Pattern.DOTALL)
                .matcher(pom).results().findFirst().orElseThrow().group(1);
        String npmVersion = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(packageJson).results().findFirst().orElseThrow().group(1);
        String e2ePackageJson = Files.readString(Path.of("../e2e/package.json"));
        String e2eVersion = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(e2ePackageJson).results().findFirst().orElseThrow().group(1);
        String e2eLock = Files.readString(Path.of("../e2e/package-lock.json"));
        String e2eLockVersion = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(e2eLock).results().findFirst().orElseThrow().group(1);
        assertEquals(version, pomVersion);
        assertEquals(version, npmVersion);
        assertEquals(version, e2eVersion);
        assertEquals(version, e2eLockVersion);
    }
}
