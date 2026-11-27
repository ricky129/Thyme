package com.ricky.thyme.feature.containers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Low-level HTTP transport contract for the Docker Engine API. */
public interface IDockerEngineClient {

    Response request(String method, String pathAndQuery) throws IOException, InterruptedException;

    record Response(int statusCode, Map<String, String> headers, byte[] body) {
        public String bodyString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
