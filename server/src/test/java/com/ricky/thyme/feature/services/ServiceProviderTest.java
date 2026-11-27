package com.ricky.thyme.feature.services;

import com.ricky.thyme.exec.ICommandRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceProviderTest {
    @Test
    void listsServicesReadOnlyAndParsesJson() throws Exception {
        var invoked = new AtomicReference<List<String>>();
        ICommandRunner runner = (command, timeout) -> {
            invoked.set(command);
            return new ICommandRunner.Result(
                    0,
                    """
                    [{"unit":"nginx.service","load":"loaded","active":"active","sub":"running","description":"Web server"}]
                    """,
                    "");
        };

        var services = new ServiceProvider(runner).list();

        assertEquals(List.of(
                "systemctl", "list-units", "--type=service", "--all", "--no-pager", "--output=json"),
                invoked.get());
        assertEquals(
                List.of(new ServiceSnapshot("nginx.service", "loaded", "active", "running", "Web server")),
                services);
    }

    @Test
    void propagatesCommandErrors() {
        ICommandRunner runner = (command, timeout) ->
                new ICommandRunner.Result(5, "", "service not found");

        assertThrows(IOException.class, () -> new ServiceProvider(runner).list());
    }

    @Test
    void rejectsMalformedServiceRecords() {
        ICommandRunner runner = (command, timeout) ->
                new ICommandRunner.Result(0, "[{\"unit\":\"nginx.service\"}]", "");

        assertThrows(IOException.class, () -> new ServiceProvider(runner).list());
    }
}
