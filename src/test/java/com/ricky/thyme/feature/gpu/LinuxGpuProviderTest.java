package com.ricky.thyme.feature.gpu;

import com.ricky.thyme.exec.CommandRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinuxGpuProviderTest {
    @TempDir Path tempDir;

    @Test
    void discoversNvidiaGpuFromLoadedDriverWithoutDrmCard() throws Exception {
        Path drm = tempDir.resolve("sys/class/drm");
        Path nvidiaProc = tempDir.resolve("proc/driver/nvidia/gpus");
        Files.createDirectories(drm);
        Files.createDirectories(nvidiaProc);
        CommandRunner runner = (command, timeout) -> {
            assertEquals("nvidia-smi", command.getFirst());
            return new CommandRunner.Result(
                    0,
                    "NVIDIA GeForce GTX 1050, 00000000:01:00.0, 5, 4096, 512, 42, 35, 75, 1354, 3504\n",
                    "");
        };

        GpuReading reading = new LinuxGpuProvider(runner, tempDir.resolve("sys"), nvidiaProc).read();

        assertEquals(1, reading.devices().size());
        assertEquals("NVIDIA GeForce GTX 1050", reading.devices().getFirst().name());
        assertTrue(reading.errors().isEmpty());
    }
}
