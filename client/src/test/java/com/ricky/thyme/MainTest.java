package com.ricky.thyme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {
    @TempDir Path tempDir;

    @Test
    void requiresRemoteHostInsteadOfStartingDashboardLocally() {
        int result = Main.run(
                new String[0],
                (target, port, key, remoteDir) -> 0,
                Optional.empty());

        assertEquals(1, result);
    }

    @Test
    void passesHostToSshAndPropagatesItsExitCode() {
        AtomicReference<String> connectedHost = new AtomicReference<>();

        int result = Main.run(
                new String[] {"myserver@myserver.tail33f299.ts.net"},
                (target, port, key, remoteDir) -> {
                    connectedHost.set(target);
                    return 255;
                },
                Optional.empty());

        assertEquals(255, result);
        assertEquals("myserver@myserver.tail33f299.ts.net", connectedHost.get());
    }

    @Test
    void reportsInvalidHostWithoutConnecting() {
        int result = Main.run(
                new String[] {"myserver@100.999.45.88"},
                (target, port, key, remoteDir) -> {
                    throw new AssertionError("invalid hosts must not start SSH");
                },
                Optional.empty());

        assertEquals(2, result);
    }

    @Test
    void requiresUsernameInSshTarget() {
        int result = Main.run(
                new String[] {"host.example"},
                (target, port, key, remoteDir) -> {
                    throw new AssertionError("target without username must not start SSH");
                },
                Optional.empty());

        assertEquals(2, result);
    }

    @Test
    void noArgumentsUsesConfiguredTargetAndSshOptions() throws IOException {
        Path config = tempDir.resolve("config.properties");
        Files.writeString(
                config,
                """
                deploy.user=myserver
                server.tailscale=http://100.73.45.88:7000
                deploy.port=2222
                deploy.key=~/.ssh/home key
                deploy.remote_dir=~/docker/thyme
                """);
        AtomicReference<String> targetSeen = new AtomicReference<>();
        AtomicReference<String> portSeen = new AtomicReference<>();
        AtomicReference<String> keySeen = new AtomicReference<>();
        AtomicReference<String> directorySeen = new AtomicReference<>();

        int result = Main.run(
                new String[0],
                (target, port, key, remoteDir) -> {
                    targetSeen.set(target);
                    portSeen.set(port);
                    keySeen.set(key);
                    directorySeen.set(remoteDir);
                    return 0;
                },
                Optional.of(config));

        assertEquals(0, result);
        assertEquals("myserver@100.73.45.88", targetSeen.get());
        assertEquals("2222", portSeen.get());
        assertEquals("~/.ssh/home key", keySeen.get());
        assertEquals("~/docker/thyme", directorySeen.get());
    }
}
