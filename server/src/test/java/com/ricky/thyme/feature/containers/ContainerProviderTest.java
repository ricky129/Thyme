package com.ricky.thyme.feature.containers;

import com.ricky.thyme.exec.CommandRunner;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ContainerProviderTest {

    private static final String RUNNING_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String STOPPED_ID = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @Test
    void listsRunningAndStoppedContainersWithStatsAndRestartCounts() throws Exception {
        List<List<String>> calls = new ArrayList<>();
        ContainerProvider provider = new ContainerProvider((command, timeout) -> {
            calls.add(command);
            return switch (command.get(1)) {
                case "ps" ->
                    ok(
                    "{\"ID\":\"" + STOPPED_ID + "\",\"Names\":\"db\",\"Image\":\"postgres\","
                    + "\"State\":\"exited\",\"Status\":\"Exited (1) 2 hours ago\","
                    + "\"RunningFor\":\"3 days ago\"}\n"
                    + "{\"ID\":\"" + RUNNING_ID + "\",\"Names\":\"web\",\"Image\":\"nginx\","
                    + "\"State\":\"running\",\"Status\":\"Up 4 hours\","
                    + "\"RunningFor\":\"4 hours\"}\n");
                case "stats" ->
                    ok("""
                                   {"Container":"0123456789ab","Name":"web","CPUPerc":"1.25%","MemUsage":"20MiB / 1GiB","MemPerc":"2%","NetIO":"1kB / 2kB","BlockIO":"3kB / 4kB","PIDs":"5"}
                                   """);
                case "inspect" ->
                    ok(RUNNING_ID + "|2|0\n" + STOPPED_ID + "|0|1\n");
                default ->
                    throw new AssertionError(command);
            };
        });

        List<ContainerSnapshot> containers = provider.list();

        assertEquals(List.of("web", "db"), containers.stream().map(ContainerSnapshot::name).toList());
        assertEquals("1.25%", containers.getFirst().cpuPercent());
        assertEquals(2, containers.getFirst().restartCount());
        assertEquals("20MiB / 1GiB", containers.getFirst().memoryUsage());
        assertEquals(1, containers.get(1).exitCode());
        assertTrue(calls.stream().allMatch(command -> "docker".equals(command.getFirst())));
    }

    @Test
    void readsTheIconLabelsFromInspectWithThymeFirst() throws Exception {
        assertEquals("si:nginx", iconFor(RUNNING_ID + "|0|0|si:nginx|si:jellyfin\n"));
        assertEquals("si:jellyfin", iconFor(RUNNING_ID + "|0|0||si:jellyfin\n"));
        assertEquals(null, iconFor(RUNNING_ID + "|0|0||\n"));
        assertEquals(null, iconFor(RUNNING_ID + "|0|0\n"));
    }

    private static String iconFor(String inspectOutput) throws Exception {
        ContainerProvider provider = new ContainerProvider((command, timeout) -> switch (command.get(1)) {
            case "ps" -> ok("{\"ID\":\"" + RUNNING_ID + "\",\"Names\":\"web\",\"Image\":\"nginx\","
                    + "\"State\":\"running\",\"Status\":\"Up\",\"RunningFor\":\"1 hour\"}\n");
            case "stats" -> ok("");
            case "inspect" -> ok(inspectOutput);
            default -> throw new AssertionError(command);
        });
        return provider.list().getFirst().icon();
    }

    @Test
    void executesValidatedContainerLifecycleAndLogCommands() throws Exception {
        List<List<String>> calls = new ArrayList<>();
        ContainerProvider provider = new ContainerProvider((command, timeout) -> {
            calls.add(command);
            return "logs".equals(command.get(1))
                    ? ok("2026-01-01T00:00:00Z error: failed to start\n")
                    : ok("");
        });

        provider.stop(RUNNING_ID);
        provider.start(STOPPED_ID);
        provider.restart(RUNNING_ID);
        assertTrue(provider.logs(RUNNING_ID).contains("error: failed"));

        assertEquals(List.of(
                List.of("docker", "stop", RUNNING_ID),
                List.of("docker", "start", STOPPED_ID),
                List.of("docker", "restart", RUNNING_ID),
                List.of("docker", "logs", "--tail", "200", "--timestamps", RUNNING_ID)), calls);
    }

    @Test
    void refusesNonIdInputsBeforeInvokingDocker() {
        ContainerProvider provider = new ContainerProvider((command, timeout) -> {
            throw new AssertionError("invalid id must not invoke Docker");
        });

        assertThrows(IllegalArgumentException.class, () -> provider.stop("web;id"));
        assertThrows(IllegalArgumentException.class, () -> provider.logs("--all"));
    }

    private static CommandRunner.Result ok(String stdout) {
        return new CommandRunner.Result(0, stdout, "");
    }
}
