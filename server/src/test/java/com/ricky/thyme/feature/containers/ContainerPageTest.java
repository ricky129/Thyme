package com.ricky.thyme.feature.containers;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerPageTest {

    @TempDir
    Path directory;

    private static final List<TerminalSize> SIZES = List.of(
            new TerminalSize(48, 15),
            new TerminalSize(80, 24),
            new TerminalSize(120, 35),
            new TerminalSize(200, 60));

    private static final String RUNNING_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String STOPPED_ID = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    @FunctionalInterface
    private interface ContainerAction {
        void run(String action, String id) throws IOException, InterruptedException;
    }

    private record StubProvider(
            Callable<List<ContainerSnapshot>> lister,
            Function<String, String> logger,
            ContainerAction actor) implements IContainerProvider {

        static StubProvider noop() {
            return new StubProvider(List::of, id -> "", (action, id) -> {
                // no-op
            });
        }

        @Override
        public List<ContainerSnapshot> list() throws IOException, InterruptedException {
            try {
                return lister.call();
            } catch (IOException | InterruptedException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        public String logs(String id) {
            return logger.apply(id);
        }

        @Override
        public void start(String id) throws IOException, InterruptedException {
            actor.run("start", id);
        }

        @Override
        public void stop(String id) throws IOException, InterruptedException {
            actor.run("stop", id);
        }

        @Override
        public void restart(String id) throws IOException, InterruptedException {
            actor.run("restart", id);
        }
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

        IContainerProvider dummyProvider = StubProvider.noop();
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
        String mockLogs = """
                          2026-11-22T10:00:00Z Starting nginx web server process on port 80...
                          2026-11-22T10:00:01Z Configuration loaded successfully from /etc/nginx/nginx.conf without errors or warnings and worker processes spawned.
                          2026-11-22T10:00:02Z Ready for connections.
                          """;

        IContainerProvider dummyProvider = new StubProvider(List::of, id -> mockLogs, (action, id) -> {
            // no-op
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
        IContainerProvider provider = new StubProvider(List::of, id -> "",
                (action, id) -> executed.add(List.of(action, id)));
        ContainerPage page = new ContainerPage(provider);
        page.onShow(Runnable::run);
        injectContainers(page, sampleList);

        // Navigation → (l / ArrowRight) moves to the second card
        page.handleKey(new KeyStroke('l', false, false));
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

    @Test
    void filtersContainersInteractively() throws IOException {
        List<ContainerSnapshot> sampleList = List.of(
                new ContainerSnapshot(RUNNING_ID, "web-app", "nginx:alpine", "running",
                        "Up 4 hours", "4 hours", 0, null, "1.5%", "45MiB / 1GiB",
                        "4.4%", "12kB / 45kB", "1.2MB / 0B", "4"),
                new ContainerSnapshot(STOPPED_ID, "db-store", "postgres:16", "exited",
                        "Exited (0) 2h ago", "2 hours", 3, 0, "n/a", "0B / 1GiB",
                        "0%", "0B / 0B", "0B / 0B", "0"),
                new ContainerSnapshot("112233445566", "cache-redis", "redis:7-alpine", "running",
                        "Up 1 hour", "1 hour", 0, null, "0.5%", "10MiB / 1GiB",
                        "1.0%", "1kB / 1kB", "0B / 0B", "1")
        );

        IContainerProvider dummyProvider = StubProvider.noop();
        ContainerPage page = new ContainerPage(dummyProvider);
        injectContainers(page, sampleList);

        // Press '/' to start filtering
        page.handleKey(new KeyStroke('/', false, false));
        assertTrue(page.capturesGlobalNavigation());

        // Type 'web'
        page.handleKey(new KeyStroke('w', false, false));
        page.handleKey(new KeyStroke('e', false, false));
        page.handleKey(new KeyStroke('b', false, false));

        assertEquals("web-app", textContainerName(page));

        // Press Enter to confirm query and navigate filtered list
        page.handleKey(new KeyStroke(KeyType.Enter));
        assertFalse(page.capturesGlobalNavigation()); // Returns to list navigation with active query
        assertEquals("web-app", textContainerName(page));

        // Press Esc to clear filter
        page.handleKey(new KeyStroke(KeyType.Escape));
        assertEquals("web-app", textContainerName(page)); // first of all 3
    }

    private static String id(int n) {
        return String.format("%012x", n + 1);
    }

    private static List<ContainerSnapshot> many(int count) {
        List<ContainerSnapshot> list = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            list.add(new ContainerSnapshot(id(index), "svc-" + index, "acme/svc" + index,
                    index % 3 == 2 ? "exited" : "running", "Up", "1h", index % 4, null,
                    "1.0%", "10MiB / 1GiB", "1%", "0B / 0B", "0B / 0B", "1"));
        }
        return list;
    }

    @Test
    void computesNonOverlappingCardsAndHitTestingAcrossBreakpointsAndScroll() {
        for (TerminalSize size : SIZES) {
            for (int count : List.of(1, 5, 13, 40)) {
                int cols = ContainerPage.gridColumns(size.getColumns());
                int rows = (count + cols - 1) / cols;
                int visible = ContainerPage.visibleRows(size);
                for (int scroll : List.of(0, Math.max(0, rows - visible))) {
                    List<ContainerPage.CardRect> cards = ContainerPage.computeCards(size, count, scroll);
                    assertFalse(cards.isEmpty());
                    for (int i = 0; i < cards.size(); i++) {
                        ContainerPage.CardRect card = cards.get(i);
                        assertTrue(card.width() >= 20, "card too narrow at " + size);
                        assertTrue(card.x() >= 0 && card.x() + card.width() <= size.getColumns());
                        assertTrue(card.y() >= 2 && card.y() + card.height() <= size.getRows() - 1);
                        assertEquals(card.containerIndex(), ContainerPage.hitTestCard(
                                size, count, scroll, card.x(), card.y()));
                        assertEquals(card.containerIndex(), ContainerPage.hitTestCard(size, count, scroll,
                                card.x() + card.width() - 1, card.y() + card.height() - 1));
                        for (int j = i + 1; j < cards.size(); j++) {
                            ContainerPage.CardRect other = cards.get(j);
                            boolean separated = card.x() + card.width() <= other.x()
                                    || other.x() + other.width() <= card.x()
                                    || card.y() + card.height() <= other.y()
                                    || other.y() + other.height() <= card.y();
                            assertTrue(separated, "cards overlap at " + size);
                        }
                    }
                }
            }
            assertEquals(-1, ContainerPage.hitTestCard(size, 5, 0, 0, 0));
        }
    }

    @Test
    void navigatesTheCardGridInTwoDimensions() throws IOException {
        ContainerPage page = new ContainerPage(StubProvider.noop());
        injectContainers(page, many(7));
        TerminalSize size = new TerminalSize(120, 35);
        writeSnapshot("containers-grid", size, page);

        page.handleKey(new KeyStroke(KeyType.ArrowRight));
        assertEquals("svc-1", textContainerName(page));
        page.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals("svc-5", textContainerName(page));
        page.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals("svc-5", textContainerName(page));
        page.handleKey(new KeyStroke(KeyType.ArrowUp));
        assertEquals("svc-1", textContainerName(page));
        page.handleKey(new KeyStroke(KeyType.ArrowLeft));
        assertEquals("svc-0", textContainerName(page));
        page.handleKey(new KeyStroke('j', false, false));
        assertEquals("svc-4", textContainerName(page));
        page.handleKey(new KeyStroke('l', false, false));
        assertEquals("svc-5", textContainerName(page));
        page.handleKey(new KeyStroke('k', false, false));
        assertEquals("svc-1", textContainerName(page));
        page.handleKey(new KeyStroke('h', false, false));
        assertEquals("svc-0", textContainerName(page));
    }

    @Test
    void scrollsTheGridToKeepTheSelectionVisible() throws IOException {
        ContainerPage page = new ContainerPage(StubProvider.noop());
        injectContainers(page, many(40));
        TerminalSize size = new TerminalSize(80, 24);
        writeSnapshot("containers-scroll", size, page);
        for (int index = 0; index < 10; index++) {
            page.handleKey(new KeyStroke(KeyType.ArrowDown));
            writeSnapshot("containers-scroll", size, page);
        }

        assertEquals("svc-30", textContainerName(page));
        String text = Files.readString(Path.of("target", "ui-snapshots", "containers-scroll-80x24.txt"));
        assertTrue(text.contains("svc-30"));
        assertFalse(text.contains("svc-0 "));
    }

    @Test
    void mouseClickOpensACardAndClickingOutsideOrOnTheBorderClosesIt() throws IOException {
        ContainerPage page = new ContainerPage(StubProvider.noop());
        page.onShow(Runnable::run);
        injectContainers(page, many(3));
        TerminalSize size = new TerminalSize(120, 35);
        writeSnapshot("containers-click", size, page);

        ContainerPage.CardRect card = ContainerPage.computeCards(size, 3, 0).get(1);
        page.handleKey(new MouseAction(MouseActionType.CLICK_DOWN, 1,
                new TerminalPosition(card.x() + 2, card.y() + 2)));
        assertTrue(page.capturesGlobalNavigation());
        assertEquals("svc-1", textContainerName(page));

        ContainerPage.OverlayRect overlay = ContainerPage.computeDetailRect(size);
        page.handleKey(new MouseAction(MouseActionType.CLICK_DOWN, 1,
                new TerminalPosition(overlay.x() + 3, overlay.y() + 3)));
        assertTrue(page.capturesGlobalNavigation(), "clicks inside the overlay keep it open");
        page.handleKey(new MouseAction(MouseActionType.CLICK_DOWN, 1,
                new TerminalPosition(overlay.x() + 3, overlay.y())));
        assertFalse(page.capturesGlobalNavigation());

        page.handleKey(new MouseAction(MouseActionType.CLICK_DOWN, 1,
                new TerminalPosition(card.x() + 2, card.y() + 2)));
        assertTrue(page.capturesGlobalNavigation());
        page.handleKey(new MouseAction(MouseActionType.CLICK_DOWN, 1, new TerminalPosition(0, 0)));
        assertFalse(page.capturesGlobalNavigation());

        // Right clicks and releases do nothing.
        page.handleKey(new MouseAction(MouseActionType.CLICK_DOWN, 3,
                new TerminalPosition(card.x() + 2, card.y() + 2)));
        page.handleKey(new MouseAction(MouseActionType.CLICK_RELEASE, 1,
                new TerminalPosition(card.x() + 2, card.y() + 2)));
        assertFalse(page.capturesGlobalNavigation());
        page.onHide();
    }

    @Test
    void convertsIconsForNewContainersWhileRunning() throws IOException {
        String svg = "<svg viewBox=\"0 0 24 24\"><path d=\"M0 0h24v24H0z\"/></svg>";
        List<String> fetched = new ArrayList<>();
        ContainerIconCache cache = new ContainerIconCache(directory.resolve("icons.json"), slug -> {
            fetched.add(slug);
            return "nginx".equals(slug) || "redis".equals(slug) ? Optional.of(svg) : Optional.empty();
        });
        AtomicReference<List<ContainerSnapshot>> docker = new AtomicReference<>(List.of(
                new ContainerSnapshot(id(0), "web", "nginx:alpine", "running", "Up", "1h", 0,
                        null, "1%", "1MiB", "1%", "0B", "0B", "1"),
                new ContainerSnapshot(id(1), "mystery", "acme/mystery", "running", "Up", "1h", 0,
                        null, "1%", "1MiB", "1%", "0B", "0B", "1")));
        IContainerProvider provider = new StubProvider(docker::get, id -> "", (action, id) -> {
            // no-op
        });
        ContainerPage page = new ContainerPage(provider, cache);
        page.onShow(Runnable::run);

        assertFalse(cache.resolve(docker.get().get(0)).placeholder());
        assertTrue(cache.resolve(docker.get().get(1)).placeholder());
        TerminalSize size = new TerminalSize(120, 35);
        writeSnapshot("containers-icons", size, page);
        String text = Files.readString(Path.of("target", "ui-snapshots", "containers-icons-120x35.txt"));
        assertTrue(text.contains("⣿⣿⣿⣿⣿⣿") || text.contains("██████"), "converted icon is drawn");

        // A container started later is converted by the next refresh.
        List<ContainerSnapshot> grown = new ArrayList<>(docker.get());
        grown.add(new ContainerSnapshot(id(2), "cache", "redis:7", "running", "Up", "1h", 0,
                null, "1%", "1MiB", "1%", "0B", "0B", "1"));
        docker.set(grown);
        fetched.clear();
        page.refresh();
        assertEquals(List.of("redis"), fetched);
        assertFalse(cache.resolve(grown.get(2)).placeholder());
        page.onHide();
    }

    private static String textContainerName(ContainerPage page) {
        ContainerSnapshot c = page.selectedContainer();
        return c != null ? c.name() : null;
    }

    private static void injectContainers(ContainerPage page, List<ContainerSnapshot> list) {
        page.setContainers(list);
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
