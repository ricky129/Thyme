package com.ricky.thyme.feature.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricky.thyme.exec.CommandRunner;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class ServiceProvider {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CommandRunner runner;

    public ServiceProvider(CommandRunner runner) {
        this.runner = runner;
    }

    private static String text(JsonNode node, String field) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual())
            throw new IOException("missing or non-text field in systemctl output: " + field);
        return value.asText();
    }

    public List<ServiceSnapshot> list() throws IOException, InterruptedException {
        var result = runner.run(
                List.of("systemctl", "list-units", "--type=service", "--all", "--no-pager", "--output=json"),
                TIMEOUT);
        if (!result.ok())
            throw new IOException("systemctl: " + result.stderr().strip());
        JsonNode root = MAPPER.readTree(result.stdout());
        if (root == null || !root.isArray())
            throw new IOException("systemctl output is not a JSON array");
        List<ServiceSnapshot> services = new ArrayList<>();
        for (JsonNode service : root) {
            if (!service.isObject())
                throw new IOException("systemctl record is not a JSON object");
            services.add(new ServiceSnapshot(
                    text(service, "unit"),
                    text(service, "load"),
                    text(service, "active"),
                    text(service, "sub"),
                    text(service, "description")));
        }
        return List.copyOf(services);
    }
}
