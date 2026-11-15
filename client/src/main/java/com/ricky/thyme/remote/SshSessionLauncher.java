package com.ricky.thyme.remote;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SshSessionLauncher {
    private static final String REMOTE_COMMAND_SUFFIX =
            "LC_ALL=C THYME_HOST_ROOT=/ \"%s/thyme-server-runtime/bin/java\" "
                    + "--enable-native-access=com.sun.jna "
                    + "--module com.ricky.thyme.server/com.ricky.thyme.server.ServerMain";

    private final ProcessStarter processStarter;

    public SshSessionLauncher() {
        this(command -> new ProcessBuilder(command).inheritIO().start());
    }

    SshSessionLauncher(ProcessStarter processStarter) {
        this.processStarter = processStarter;
    }

    public int launch(String target, String port, String key, String remoteDirectory)
            throws IOException, InterruptedException {
        if (!SshTarget.isValid(target))
            throw new IllegalArgumentException("invalid SSH target: " + target);
        Process process = processStarter.start(commandFor(target, port, key, remoteDirectory));
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw e;
        }
    }

    static List<String> commandFor(String target, String port, String key, String remoteDirectory) {
        if (remoteDirectory == null
                || !remoteDirectory.matches("(?:~/|/)[A-Za-z0-9_./~-]*")
                || remoteDirectory.contains("/../")
                || remoteDirectory.endsWith("/..")
                || "..".equals(remoteDirectory))
            throw new IllegalArgumentException("invalid remote directory: " + remoteDirectory);
        List<String> command = new ArrayList<>(List.of("ssh"));
        if (port != null && !port.isBlank())
            command.addAll(List.of("-p", port));
        if (key != null && !key.isBlank())
            command.addAll(List.of("-i", resolveHome(key)));
        String remotePath = remoteDirectory.startsWith("~/")
                ? "$HOME/" + remoteDirectory.substring(2)
                : remoteDirectory;
        String remoteCommand = String.format(REMOTE_COMMAND_SUFFIX, remotePath);
        command.addAll(List.of("-t", target, remoteCommand));
        return List.copyOf(command);
    }

    private static String resolveHome(String path) {
        if ("~".equals(path))
            return System.getProperty("user.home");
        if (path.startsWith("~/"))
            return Path.of(System.getProperty("user.home"), path.substring(2)).toString();
        return path;
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(List<String> command) throws IOException;
    }
}
