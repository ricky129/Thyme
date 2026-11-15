package com.ricky.thyme.feature.containers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricky.thyme.exec.CommandRunner;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ContainerProvider {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ID = Pattern.compile("[0-9a-f]{12,64}");
    private final CommandRunner runner;

    public ContainerProvider(CommandRunner runner) {
        this.runner = runner;
    }

    public List<ContainerSnapshot> list() throws IOException, InterruptedException {
        var ps = run(List.of("docker", "ps", "--all", "--no-trunc", "--format", "{{json .}}"));
        Map<String, Stats> stats = parseStats(run(List.of(
                "docker", "stats", "--no-stream", "--format", "{{json .}}")).stdout());
        List<PsRow> rows = parsePs(ps.stdout());
        if (rows.isEmpty())
            return List.of();
        List<String> inspectCommand = new ArrayList<>(List.of("docker", "inspect", "--format"));
        inspectCommand.add("{{.Id}}|{{.RestartCount}}|{{.State.ExitCode}}");
        rows.forEach(row -> inspectCommand.add(row.id()));
        Map<String, Inspect> inspect = parseInspect(run(inspectCommand).stdout());
        return rows.stream().map(row -> {
            Stats containerStats = stats.getOrDefault(row.id(), stats.get(row.name()));
            Inspect containerInspect = inspect.getOrDefault(row.id(), new Inspect(0, null));
            return new ContainerSnapshot(
                    row.id(),
                    row.name(),
                    row.image(),
                    row.state(),
                    row.status(),
                    row.runningFor(),
                    containerInspect.restartCount(),
                    containerInspect.exitCode(),
                    containerStats == null ? null : containerStats.cpu(),
                    containerStats == null ? null : containerStats.memory(),
                    containerStats == null ? null : containerStats.memoryPercent(),
                    containerStats == null ? null : containerStats.networkIo(),
                    containerStats == null ? null : containerStats.blockIo(),
                    containerStats == null ? null : containerStats.processCount());
        }).toList();
    }

    public String logs(String id) throws IOException, InterruptedException {
        requireId(id);
        return run(List.of("docker", "logs", "--tail", "200", "--timestamps", id)).stdout();
    }

    public void start(String id) throws IOException, InterruptedException {
        action("start", id);
    }

    public void stop(String id) throws IOException, InterruptedException {
        action("stop", id);
    }

    public void restart(String id) throws IOException, InterruptedException {
        action("restart", id);
    }

    private void action(String action, String id) throws IOException, InterruptedException {
        requireId(id);
        run(List.of("docker", action, id));
    }

    private CommandRunner.Result run(List<String> command) throws IOException, InterruptedException {
        var result = runner.run(command, TIMEOUT);
        if (!result.ok())
            throw new IOException("docker: " + result.stderr().strip());
        return result;
    }

    private static void requireId(String id) {
        if (id == null || !ID.matcher(id).matches())
            throw new IllegalArgumentException("invalid container ID");
    }

    static List<PsRow> parsePs(String output) throws IOException {
        List<PsRow> rows = new ArrayList<>();
        for (String line : output.lines().toList()) {
            if (line.isBlank())
                continue;
            JsonNode json = MAPPER.readTree(line);
            String id = requiredText(json, "ID");
            requireId(id);
            rows.add(new PsRow(
                    id,
                    requiredText(json, "Names"),
                    optionalText(json, "Image"),
                    optionalText(json, "State"),
                    optionalText(json, "Status"),
                    optionalText(json, "RunningFor")));
        }
        rows.sort((left, right) -> {
            int stateOrder = Boolean.compare(!"running".equals(left.state()), !"running".equals(right.state()));
            return stateOrder == 0 ? left.name().compareToIgnoreCase(right.name()) : stateOrder;
        });
        return List.copyOf(rows);
    }

    static Map<String, Stats> parseStats(String output) throws IOException {
        Map<String, Stats> stats = new HashMap<>();
        for (String line : output.lines().toList()) {
            if (line.isBlank())
                continue;
            JsonNode json = MAPPER.readTree(line);
            String id = optionalText(json, "ID");
            String name = optionalText(json, "Name");
            Stats value = new Stats(
                    optionalText(json, "CPUPerc"),
                    optionalText(json, "MemUsage"),
                    optionalText(json, "MemPerc"),
                    optionalText(json, "NetIO"),
                    optionalText(json, "BlockIO"),
                    optionalText(json, "PIDs"));
            if (!id.isBlank())
                stats.put(id, value);
            if (!name.isBlank())
                stats.put(name, value);
        }
        return Map.copyOf(stats);
    }

    static Map<String, Inspect> parseInspect(String output) throws IOException {
        Map<String, Inspect> values = new HashMap<>();
        for (String line : output.lines().toList()) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 3 || !ID.matcher(parts[0]).matches())
                throw new IOException("invalid docker inspect output");
            try {
                int restarts = Integer.parseInt(parts[1]);
                Integer exitCode = parts[2].isBlank() ? null : Integer.valueOf(parts[2]);
                if (restarts < 0 || (exitCode != null && exitCode < 0))
                    throw new NumberFormatException();
                values.put(parts[0], new Inspect(restarts, exitCode));
            } catch (NumberFormatException e) {
                throw new IOException("invalid docker inspect counters", e);
            }
        }
        return Map.copyOf(values);
    }

    private static String requiredText(JsonNode node, String field) throws IOException {
        String value = optionalText(node, field);
        if (value.isBlank())
            throw new IOException("missing field in docker output: " + field);
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    record PsRow(String id, String name, String image, String state, String status, String runningFor) {}

    record Stats(String cpu, String memory, String memoryPercent, String networkIo, String blockIo, String processCount) {}

    record Inspect(int restartCount, Integer exitCode) {}
}
