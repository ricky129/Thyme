package com.ricky.thyme.remote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SshSessionLauncherTest {
    @Test
    void startsDockerComposeInAnInteractiveSshSession() {
        assertEquals(
                List.of(
                        "ssh",
                        "-t",
                        "myserver@myserver.tail33f299.ts.net",
                        "LC_ALL=C THYME_HOST_ROOT=/ \"$HOME/docker/thyme/thyme-server-runtime/bin/java\" "
                                + "--enable-native-access=com.sun.jna "
                                + "--module com.ricky.thyme.server/com.ricky.thyme.server.ServerMain"),
                SshSessionLauncher.commandFor(
                        "myserver@myserver.tail33f299.ts.net", "", "", "~/docker/thyme"));
    }

    @Test
    void passesConfiguredSshKeyPortAndRemoteDirectory() {
        assertEquals(
                List.of(
                        "ssh",
                        "-p",
                        "2222",
                        "-i",
                        Path.of(System.getProperty("user.home"), ".ssh", "id_ed25519").toString(),
                        "-t",
                        "myserver@myserver.com",
                        "LC_ALL=C THYME_HOST_ROOT=/ \"/srv/thyme/thyme-server-runtime/bin/java\" "
                                + "--enable-native-access=com.sun.jna "
                                + "--module com.ricky.thyme.server/com.ricky.thyme.server.ServerMain"),
                SshSessionLauncher.commandFor(
                        "myserver@myserver.com", "2222", "~/.ssh/id_ed25519", "/srv/thyme"));
    }

    @Test
    void returnsRemoteSshExitCode() throws Exception {
        var launcher = new SshSessionLauncher(command -> new CompletedProcess(23));

        assertEquals(23, launcher.launch("myserver@100.73.45.88", "", "", "~/docker/thyme"));
    }

    @Test
    void propagatesSshStartupErrors() {
        var launcher = new SshSessionLauncher(command -> {
            throw new IOException("ssh executable missing");
        });

        IOException error = assertThrows(
                IOException.class,
                () -> launcher.launch("myserver@myserver.com", "", "", "~/docker/thyme"));

        assertEquals("ssh executable missing", error.getMessage());
    }

    @Test
    void rejectsInvalidTargetBeforeStartingSsh() {
        var launcher = new SshSessionLauncher(command -> {
            throw new AssertionError("invalid target must not start SSH");
        });

        assertThrows(
                IllegalArgumentException.class,
                () -> launcher.launch("myserver.com", "", "", "~/docker/thyme"));
    }

    @Test
    void rejectsUnsafeRemoteDirectory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SshSessionLauncher.commandFor(
                        "myserver@myserver.com", "", "", "~/docker/../other"));
    }

    private static final class CompletedProcess extends Process {
        private final int exitCode;

        private CompletedProcess(int exitCode) {
            this.exitCode = exitCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {}
    }
}
