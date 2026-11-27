package com.ricky.thyme.feature.containers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Stub: HTTP/1.1 client over {@link java.net.UnixDomainSocketAddress} and
 * {@link java.nio.channels.SocketChannel} for the local Docker daemon socket.
 */
public final class UnixSocketDockerClient implements IDockerEngineClient {

    public static final Path DEFAULT_SOCKET_PATH = Path.of("/var/run/docker.sock");

    private final Path socketPath;

    public UnixSocketDockerClient() {
        this(DEFAULT_SOCKET_PATH);
    }

    public UnixSocketDockerClient(Path socketPath) {
        this.socketPath = Objects.requireNonNull(socketPath, "socketPath");
    }

    public Path socketPath() {
        return socketPath;
    }

    @Override
    public Response request(String method, String pathAndQuery) throws IOException, InterruptedException {
        // TODO: open SocketChannel on UnixDomainSocketAddress.of(socketPath),
        //       send HTTP/1.1 request, and read status/headers/body (Content-Length or chunked)
        throw new UnsupportedOperationException("TODO: implement Unix socket HTTP request");
    }
}
