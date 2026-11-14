package com.ricky.thyme;

import com.ricky.thyme.remote.SshSessionLauncher;
import com.ricky.thyme.remote.SshTarget;
import com.ricky.thyme.remote.ThymeConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, new SshSessionLauncher(), ThymeConfig.findConfigFile());
        if (exitCode != 0)
            System.exit(exitCode);
    }

    static int run(String[] args, SshSessionLauncher ssh, Optional<Path> configPath) {
        return run(args, ssh::launch, configPath);
    }

    static int run(String[] args, RemoteLauncher ssh, Optional<Path> configPath) {
        if (args.length > 1 || (args.length == 1 && !SshTarget.isValid(args[0]))) {
            System.err.println("Usage: thyme <user>@<host>");
            System.err.println("Example: thyme myserver@100.73.45.88");
            System.err.println("Or configure config.properties and simply run: thyme");
            System.err.println("The server must be reachable over SSH and have Thyme installed.");
            return args.length == 1 && "--help".equals(args[0]) ? 0 : 2;
        }

        try {
            ThymeConfig config = configPath.isPresent()
                    ? ThymeConfig.load(configPath.get())
                    : ThymeConfig.empty();
            String target = args.length == 1 ? args[0] : config.sshTarget();
            if (target == null || target.isBlank()) {
                System.err.println("thyme: configure deploy.user and deploy.host or provide user@host.");
                return 1;
            }
            return ssh.launch(target, config.sshPort(), config.sshKey(), config.remoteDirectory());
        } catch (IOException e) {
            System.err.println("thyme: invalid configuration or SSH session: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        }
    }

    @FunctionalInterface
    interface RemoteLauncher {
        int launch(String target, String port, String key, String remoteDirectory)
                throws IOException, InterruptedException;
    }
}
