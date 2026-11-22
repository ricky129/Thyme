package com.ricky.thyme.server;

import com.ricky.thyme.exec.ProcessCommandRunner;
import com.ricky.thyme.feature.containers.ContainerPage;
import com.ricky.thyme.feature.containers.ContainerProvider;
import com.ricky.thyme.feature.cpu.OshiCpuProvider;
import com.ricky.thyme.feature.dashboard.DashboardPage;
import com.ricky.thyme.feature.disk.LinuxDiskProvider;
import com.ricky.thyme.feature.gpu.LinuxGpuProvider;
import com.ricky.thyme.feature.host.OshiHostProvider;
import com.ricky.thyme.feature.memory.OshiMemoryProvider;
import com.ricky.thyme.feature.services.ServiceProvider;
import com.ricky.thyme.ui.Shell;
import com.ricky.thyme.ui.PageRegistry;
import oshi.util.GlobalConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) {
        if (args.length != 0) {
            System.err.println("thyme-server: this command does not accept arguments.");
            System.exit(2);
        }
        try {
            runDashboard();
        } catch (IOException e) {
            System.err.println("thyme-server: could not start the dashboard: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runDashboard() throws IOException {
        Path hostRoot = configuredHostRoot(System.getenv("THYME_HOST_ROOT"));
        Path sysRoot = Path.of("/sys");
        GlobalConfig.set(GlobalConfig.OSHI_UTIL_PROC_PATH, hostRoot.resolve("proc"));
        GlobalConfig.set(GlobalConfig.OSHI_UTIL_SYS_PATH, sysRoot);

        var runner = new ProcessCommandRunner();
        var diskProvider = new LinuxDiskProvider(hostRoot, sysRoot, runner);
        var gpuProvider = new LinuxGpuProvider(sysRoot, hostRoot.resolve("proc/driver/nvidia/gpus"));
        var serviceProvider = new ServiceProvider(runner);
        var dashboard = new DashboardPage(
                new OshiCpuProvider(),
                new OshiMemoryProvider(),
                gpuProvider, diskProvider, serviceProvider, new OshiHostProvider());
        var containers = new ContainerPage(new ContainerProvider(runner));
        new Shell(new PageRegistry(java.util.List.of(
                new PageRegistry.Entry("overview", dashboard),
                new PageRegistry.Entry("containers", containers)))).run();
    }

    static Path configuredHostRoot(String hostRootValue) throws IOException {
        // If the environment variable is missing, fall back to the real filesystem root ("/")
        // which always contains the host's "proc" and "sys" directories. This preserves the
        // original behaviour when the server was started via the wrapper scripts that
        // exported THYME_HOST_ROOT=$(pwd).
        if (hostRootValue == null || hostRootValue.isBlank()) {
            hostRootValue = "/";
        }
        Path hostRoot = Path.of(hostRootValue).toAbsolutePath().normalize();
        Path proc = hostRoot.resolve("proc");
        Path sys = hostRoot.resolve("sys");
        // Verify that the required proc and sys directories are present.
        if (!Files.isDirectory(proc)
                || !Files.isDirectory(sys)
                || !Files.isDirectory(Path.of("/sys")))
            throw new IOException("THYME_HOST_ROOT does not expose the host's proc and sys: " + hostRoot);
        return hostRoot;
    }
}
