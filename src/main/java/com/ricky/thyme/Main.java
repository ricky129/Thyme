package com.ricky.thyme;

import com.ricky.thyme.exec.ProcessCommandRunner;
import com.ricky.thyme.feature.containers.ContainerPage;
import com.ricky.thyme.feature.containers.ContainerProvider;
import com.ricky.thyme.feature.cpu.OshiCpuProvider;
import com.ricky.thyme.feature.dashboard.DashboardPage;
import com.ricky.thyme.feature.disk.LinuxDiskProvider;
import com.ricky.thyme.feature.gpu.LinuxGpuProvider;
import com.ricky.thyme.feature.memory.OshiMemoryProvider;
import com.ricky.thyme.feature.services.ServiceProvider;
import com.ricky.thyme.remote.SshSessionLauncher;
import com.ricky.thyme.remote.SshTarget;
import com.ricky.thyme.remote.ThymeConfig;
import com.ricky.thyme.ui.Shell;
import oshi.util.GlobalConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class Main {
    private static final String REMOTE_SESSION = "--remote-session";

    private Main() {
    }

    static void main(String[] args) {
        int exitCode = run(args, new SshSessionLauncher(), Main::runDashboard);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, SshSessionLauncher ssh, DashboardRunner dashboard) {
        return run(args, ssh::launch, dashboard, ThymeConfig.findConfigFile());
    }

    static int run(String[] args, RemoteLauncher ssh, DashboardRunner dashboard, Optional<Path> configPath) {
        if (args.length == 1 && REMOTE_SESSION.equals(args[0])) {
            try {
                dashboard.run();
                return 0;
            } catch (IOException e) {
                System.err.println("thyme: impossibile avviare la dashboard: " + e.getMessage());
                return 1;
            }
        }

        if (args.length > 1 || (args.length == 1 && !SshTarget.isValid(args[0]))) {
            System.err.println("Uso: thyme <utente>@<host>");
            System.err.println("Esempio: thyme myserver@100.73.45.88");
            System.err.println("Oppure configura config.properties e avvia semplicemente: thyme");
            System.err.println("Il server deve essere raggiungibile via SSH e avere Java 25 o superiore.");
            return args.length == 1 && "--help".equals(args[0]) ? 0 : 2;
        }

        try {
            ThymeConfig config = configPath.isPresent()
                    ? ThymeConfig.load(configPath.get())
                    : ThymeConfig.empty();
            String target = args.length == 1 ? args[0] : config.sshTarget();
            return ssh.launch(target, config.sshPort(), config.sshKey(), config.remoteDirectory());
        } catch (IOException e) {
            System.err.println("thyme: configurazione o sessione SSH non valida: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        }
    }

    private static void runDashboard() throws IOException {
        Path hostRoot = configuredHostRoot(System.getenv("THYME_HOST_ROOT"));
        Path sysRoot = Path.of("/sys");
        GlobalConfig.set(GlobalConfig.OSHI_UTIL_PROC_PATH, hostRoot.resolve("proc"));
        GlobalConfig.set(GlobalConfig.OSHI_UTIL_SYS_PATH, sysRoot);

        var runner = new ProcessCommandRunner();
        var dashboard = new DashboardPage(
                new OshiCpuProvider(),
                new OshiMemoryProvider(),
                new LinuxGpuProvider(runner, sysRoot),
                new LinuxDiskProvider(hostRoot, sysRoot, runner),
                new ServiceProvider(runner));
        new Shell(dashboard, new ContainerPage(new ContainerProvider(runner))).run();
    }

    static Path configuredHostRoot(String hostRootValue) throws IOException {
        if (hostRootValue == null || hostRootValue.isBlank()) {
            throw new IOException("la dashboard può essere avviata solo nella sessione remota");
        }
        Path hostRoot = Path.of(hostRootValue).toAbsolutePath().normalize();
        Path proc = hostRoot.resolve("proc");
        Path sys = hostRoot.resolve("sys");
        if (!Files.isDirectory(proc)
                || !Files.isDirectory(sys)
                || !Files.isDirectory(Path.of("/sys"))) {
            throw new IOException("THYME_HOST_ROOT non espone proc e sys dell'host: " + hostRoot);
        }
        return hostRoot;
    }

    @FunctionalInterface
    interface DashboardRunner {
        void run() throws IOException;
    }

    @FunctionalInterface
    interface RemoteLauncher {
        int launch(String target, String port, String key, String remoteDirectory)
                throws IOException, InterruptedException;
    }
}
