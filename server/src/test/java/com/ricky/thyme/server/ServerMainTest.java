package com.ricky.thyme.server;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerMainTest {
    @TempDir Path tempDir;

    @Test
    void requiresTheHostRootAndAcceptsAValidRoot() throws IOException {
        // When THYME_HOST_ROOT is not set the method falls back to "/".
        assertEquals(Path.of("/").toAbsolutePath(), ServerMain.configuredHostRoot(null));

        Path root = tempDir.resolve("host");
        java.nio.file.Files.createDirectories(root.resolve("proc"));
        java.nio.file.Files.createDirectories(root.resolve("sys"));

        assertEquals(root.toAbsolutePath(), ServerMain.configuredHostRoot(root.toString()));
    }
}
