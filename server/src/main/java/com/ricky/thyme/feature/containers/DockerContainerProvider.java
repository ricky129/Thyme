package com.ricky.thyme.feature.containers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Stub: Docker Engine API provider backed by {@link IDockerEngineClient}. */
public final class DockerContainerProvider implements IContainerProvider {

    private final IDockerEngineClient client;

    public DockerContainerProvider() {
        this(new UnixSocketDockerClient());
    }

    public DockerContainerProvider(Path socketPath) {
        this(new UnixSocketDockerClient(socketPath));
    }

    public DockerContainerProvider(IDockerEngineClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    IDockerEngineClient client() {
        return client;
    }

    @Override
    public List<ContainerSnapshot> list() throws IOException, InterruptedException {
        // TODO: GET /containers/json?all=1, GET /containers/{id}/json,
        //       and GET /containers/{id}/stats?stream=false -> List<ContainerSnapshot>
        throw new UnsupportedOperationException("TODO: implement container listing via Docker Engine API");
    }

    @Override
    public String logs(String id) throws IOException, InterruptedException {
        // TODO: validate id, GET /containers/{id}/logs?stdout=1&stderr=1&timestamps=1&tail=200,
        //       and demultiplex 8-byte stream frames when container TTY is false
        throw new UnsupportedOperationException("TODO: implement container logs via Docker Engine API");
    }

    @Override
    public void start(String id) throws IOException, InterruptedException {
        // TODO: validate id and POST /containers/{id}/start
        throw new UnsupportedOperationException("TODO: implement container start via Docker Engine API");
    }

    @Override
    public void stop(String id) throws IOException, InterruptedException {
        // TODO: validate id and POST /containers/{id}/stop
        throw new UnsupportedOperationException("TODO: implement container stop via Docker Engine API");
    }

    @Override
    public void restart(String id) throws IOException, InterruptedException {
        // TODO: validate id and POST /containers/{id}/restart
        throw new UnsupportedOperationException("TODO: implement container restart via Docker Engine API");
    }
}
