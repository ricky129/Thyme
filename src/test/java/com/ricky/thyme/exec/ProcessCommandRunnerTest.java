package com.ricky.thyme.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessCommandRunnerTest {
    private final ProcessCommandRunner runner = new ProcessCommandRunner();

    @Test
    void capturesOutputAndExitCode() throws Exception {
        var result = runner.run(List.of("/usr/bin/printf", "hello %s", "thyme"), Duration.ofSeconds(2));

        assertEquals(0, result.exitCode());
        assertEquals("hello thyme", result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void preservesNonZeroExitCodes() throws Exception {
        var result = runner.run(List.of("/usr/bin/false"), Duration.ofSeconds(2));

        assertEquals(1, result.exitCode());
        assertFalse(result.ok());
    }

    @Test
    void enforcesTimeout() {
        assertThrows(
                IOException.class,
                () -> runner.run(List.of("/usr/bin/sleep", "2"), Duration.ofMillis(50)));
    }

    @Test
    void capsCapturedOutputButDrainsTheRemainder() throws Exception {
        var result = runner.run(
                List.of("/usr/bin/head", "-c", "4194305", "/dev/zero"),
                Duration.ofSeconds(5));

        assertEquals(4 * 1024 * 1024, result.stdout().length());
    }

    @Test
    void rejectsEmptyCommandsAndNonPositiveTimeouts() {
        assertThrows(IllegalArgumentException.class, () -> runner.run(List.of(), Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> runner.run(List.of("/usr/bin/true"), Duration.ZERO));
    }
}
