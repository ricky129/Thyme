package com.ricky.thyme.feature.containers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class ContainerProviderTest {

    @Test
    void wiresSocketPathsAndEngineClient() {
        UnixSocketDockerClient defaultClient = new UnixSocketDockerClient();
        assertEquals(UnixSocketDockerClient.DEFAULT_SOCKET_PATH, defaultClient.socketPath());

        Path customSocket = Path.of("/tmp/test-docker.sock");
        DockerContainerProvider pathProvider = new DockerContainerProvider(customSocket);
        assertNotNull(pathProvider.client());
        assertNotNull(new DockerContainerProvider().client());

        IDockerEngineClient.Response ok = new IDockerEngineClient.Response(
                200, Map.of(), "{}".getBytes(StandardCharsets.UTF_8));
        assertEquals("{}", ok.bodyString());

        IDockerEngineClient fakeClient = (method, path) -> ok;
        DockerContainerProvider provider = new DockerContainerProvider(fakeClient);
        assertSame(fakeClient, provider.client());
    }

    @Test
    @Disabled("TODO: enable once DockerContainerProvider.list() is implemented")
    void listsRunningAndStoppedContainersWithStatsAndRestartCounts() {
        // TODO: test GET /containers/json?all=1, inspect, and stats mapping
    }

    @Test
    @Disabled("TODO: enable once DockerContainerProvider.list() is implemented")
    void readsTheIconLabelsFromInspectWithThymeFirst() {
        // TODO: test thyme.icon precedence over homepage.icon
    }

    @Test
    @Disabled("TODO: enable once lifecycle and log methods are implemented")
    void executesValidatedContainerLifecycleAndLogRequests() {
        // TODO: test start, stop, restart, and logs over IDockerEngineClient
    }

    @Test
    @Disabled("TODO: enable once container ID validation is implemented")
    void refusesNonIdInputsBeforeCallingDockerSocket() {
        // TODO: test IllegalArgumentException on malformed container IDs
    }
}
