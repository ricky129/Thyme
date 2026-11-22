package com.ricky.thyme.feature.containers;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.ricky.thyme.exec.CommandRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerPageTest {
    private static final List<TerminalSize> SIZES = List.of(
            new TerminalSize(48, 15),
            new TerminalSize(80, 24),
            new TerminalSize(120, 35),
            new TerminalSize(200, 60));

    private static final String RUNNING_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String STOPPED_ID = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    private static CommandRunner.Result ok(String stdout) {
        return new CommandRunner.Result(0, stdout, "");
    }

    @Test
    void rendersResponsiveContainerPageSnapshots() throws IOException {
        List<ContainerSnapshot> sampleList = List.of(
                new ContainerSnapshot(RUNNING_ID, "web-app", "nginx:alpine", "running",
                        "Up 4 hours", "4 hours", 0, null, "1.5%", "45MiB / 1GiB",
                        "4.4%", "12kB / 45kB", "1.2MB / 0B", "4"),
                new ContainerSnapshot(STOPPED_ID, "db-store", "postgres:16", "exited",
                        "Exited (0) 2h ago", "2 hours", 3, 0, "n/a", "0B / 1GiB",
                        "0%", "0B / 0B", "0B / 0B", "0")
        );

        ContainerProvider dummyProvider = new ContainerProvider((cmd, timeout) -> ok(""));
        ContainerPage page = new ContainerPage(dummyProvider);
        injectContainers(page, sampleList);

        for (TerminalSize size : SIZES) {
            writeSnapshot("containers-list", size, page);
        }

        Path standard = Path.of("target", "ui-snapshots", "containers-list-120x35.txt");
        assertTrue(Files.exists(standard));
        String text = Files.readString(standard);
        assertTrue(text.contains("web-app"));
        assertTrue(text.contains("db-store"));
        assertTrue(text.contains("Total"));
        assertTrue(text.contains("Running"));
        assertTrue(text.contains("Containers"));
    }

    @Test
    void rendersDetailWithAutoLoadedLogsAndExpandableLines() throws IOException {
        List<ContainerSnapshot> sampleList = List.of(
                new ContainerSnapshot(RUNNING_ID, "web-app", "nginx:alpine", "running",
                        "Up 4 hours", "4 hours", 0, null, "1.5%", "45MiB / 1GiB",
                        "4.4%", "12kB / 45kB", "1.2MB / 0B", "4"),
                new ContainerSnapshot(STOPPED_ID, "db-store", "postgres:16", "exited",
                        "Exited (0) 2h ago", "2 hours", 3, 0, "n/a", "0B / 1GiB",
                        "0%", "0B / 0B", "0B / 0B", "0")
        );
        String mockLogs = "2026-11-22T10:00:00Z Starting nginx web server process on port 80...\n"
                + "2026-11-22T10:00:01Z Configuration loaded successfully from /etc/nginx/nginx.conf without errors or warnings and worker processes spawned.\n"
                + "2026-11-22T10:00:02Z Ready for connections.\n";

        ContainerProvider dummyProvider = new ContainerProvider((cmd, timeout) -> {
            if ("logs".equals(cmd.get(1))) {
                return ok(mockLogs);
            }
            return ok("");
        });
        ContainerPage page = new ContainerPage(dummyProvider);
        page.onShow(Runnable::run);
        injectContainers(page, sampleList);

        // Open details with Enter -> immediately auto-loads logs
        page.handleKey(new KeyStroke(KeyType.Enter));
        assertTrue(page.capturesGlobalNavigation());

        // Move to line 1 with ArrowDown to test selection and expansion of long line
        page.handleKey(new KeyStroke(KeyType.ArrowDown));

        for (TerminalSize size : SIZES) {
            writeSnapshot("containers-detail", size, page);
        }

        Path detailStandard = Path.of("target", "ui-snapshots", "containers-detail-120x35.txt");
        assertTrue(Files.exists(detailStandard));
        String text = Files.readString(detailStandard);
        assertTrue(text.contains("web-app"));
        assertTrue(text.contains("nginx:alpine"));
        assertTrue(text.contains("Esc close"));
        assertTrue(text.contains("› 2026-11-22T10:00:01Z Configuration loaded"));

        // Switch container with ArrowRight -> moves to db-store
        page.handleKey(new KeyStroke(KeyType.ArrowRight));
        assertEquals("db-store", textContainerName(page));

        // Switch back with ArrowLeft -> moves back to web-app
        page.handleKey(new KeyStroke(KeyType.ArrowLeft));
        assertEquals("web-app", textContainerName(page));

        // Request action to verify confirm overlay
        page.handleKey(new KeyStroke('s', false, false));
        for (TerminalSize size : SIZES) {
            writeSnapshot("containers-confirm", size, page);
        }

        Path confirmStandard = Path.of("target", "ui-snapshots", "containers-confirm-120x35.txt");
        assertTrue(Files.exists(confirmStandard));
        String confirmText = Files.readString(confirmStandard);
        assertTrue(confirmText.contains("Confirm Action"));
        assertTrue(confirmText.contains("web-app"));

        // Cancel action with Esc
        page.handleKey(new KeyStroke(KeyType.Escape));
        assertTrue(page.capturesGlobalNavigation(), "detail overlay should remain open after cancelling action");

        // Close detail with Esc
        page.handleKey(new KeyStroke(KeyType.Escape));
        assertFalse(page.capturesGlobalNavigation(), "overlay closed, global navigation released");
        page.onHide();
    }

    @Test
    void handlesKeyboardNavigationAndActions() {
        List<ContainerSnapshot> sampleList = List.of(
                new ContainerSnapshot(RUNNING_ID, "web-app", "nginx:alpine", "running",
                        "Up 4 hours", "4 hours", 0, null, "1.5%", "45MiB / 1GiB",
                        "4.4%", "12kB / 45kB", "1.2MB / 0B", "4"),
                new ContainerSnapshot(STOPPED_ID, "db-store", "postgres:16", "exited",
                        "Exited (0) 2h ago", "2 hours", 3, 0, "n/a", "0B / 1GiB",
                        "0%", "0B / 0B", "0B / 0B", "0")
        );
        List<List<String>> executed = new ArrayList<>();
        ContainerProvider provider = new ContainerProvider((cmd, timeout) -> {
            executed.add(cmd);
            return ok("");
        });
        ContainerPage page = new ContainerPage(provider);
        page.onShow(Runnable::run);
        injectContainers(page, sampleList);

        // Navigation ↓ (j / ArrowDown)
        page.handleKey(new KeyStroke('j', false, false));
        // Open details
        page.handleKey(new KeyStroke(KeyType.Enter));
        assertTrue(page.capturesGlobalNavigation());

        // 'a' start db-store
        page.handleKey(new KeyStroke('a', false, false));
        // Confirm with 'y'
        page.handleKey(new KeyStroke('y', false, false));

        assertTrue(executed.stream().anyMatch(cmd -> cmd.contains("start") && cmd.contains(STOPPED_ID)));
        page.onHide();
    }

    private static String textContainerName(ContainerPage page) {
        try {
            var method = ContainerPage.class.getDeclaredMethod("selectedContainer");
            method.setAccessible(true);
            ContainerSnapshot c = (ContainerSnapshot) method.invoke(page);
            return c != null ? c.name() : null;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void injectContainers(ContainerPage page, List<ContainerSnapshot> list) {
        try {
            var field = ContainerPage.class.getDeclaredField("containers");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var ref = (AtomicReference<List<ContainerSnapshot>>) field.get(page);
            ref.set(list);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeSnapshot(String prefix, TerminalSize size, ContainerPage page) throws IOException {
        Path dir = Path.of("target", "ui-snapshots");
        Files.createDirectories(dir);
        var terminal = new DefaultVirtualTerminal(size);
        try (Screen screen = new TerminalScreen(terminal)) {
            screen.startScreen();
            page.render(screen.newTextGraphics(), size);
            screen.refresh();
            StringBuilder sb = new StringBuilder();
            for (int row = 0; row < size.getRows(); row++) {
                for (int col = 0; col < size.getColumns(); col++) {
                    sb.append(terminal.getCharacter(col, row).getCharacter());
                }
                sb.append('\n');
            }
            Files.writeString(dir.resolve(prefix + "-" + size.getColumns() + "x" + size.getRows() + ".txt"), sb.toString());
        }
    }
}
