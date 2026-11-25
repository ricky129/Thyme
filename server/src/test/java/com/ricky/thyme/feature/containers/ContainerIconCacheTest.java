package com.ricky.thyme.feature.containers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerIconCacheTest {
    private static final String SVG =
            "<svg viewBox=\"0 0 24 24\"><path d=\"M0 0h24v24H0z\"/></svg>";

    @TempDir
    Path directory;

    private static ContainerSnapshot container(String id, String name, String image, String icon) {
        return new ContainerSnapshot(id, name, image, "running", "Up", "1h", 0, null,
                "1%", "1MiB", "1%", "0B", "0B", "1", icon);
    }

    @Test
    void prefersTheLabelThenTheImageThenTheName() {
        assertEquals("nginx", ContainerIconCache.candidateSlugs(
                container("0123456789ab", "bar", "foo", "si:Nginx")).getFirst());

        List<String> jellyfin = ContainerIconCache.candidateSlugs(container(
                "0123456789ab", "media", "lscr.io/linuxserver/jellyfin:latest", null));
        assertEquals("jellyfin", jellyfin.getFirst());
        assertFalse(jellyfin.contains("linuxserver"));

        assertEquals("postgresql", ContainerIconCache.candidateSlugs(
                container("0123456789ab", "db", "postgres:16", null)).getFirst());
        assertEquals("redis", ContainerIconCache.candidateSlugs(
                container("0123456789ab", "cache", "valkey/valkey@sha256:abcd", null)).getFirst());

        List<String> byName = ContainerIconCache.candidateSlugs(
                container("0123456789ab", "grafana-main", "acme/dashboard", null));
        assertTrue(byName.indexOf("grafana") > byName.indexOf("dashboard"));
        assertFalse(byName.contains("main"));

        assertTrue(ContainerIconCache.candidateSlugs(container("0123456789ab", "x",
                "ghcr.io/immich-app/immich-server:release", null)).contains("immich"));
    }

    @Test
    void convertsExistingContainersPersistsThemAndOnlyConvertsNewOnesLater() throws Exception {
        Path file = directory.resolve("cache").resolve("icons.json");
        List<String> calls = new ArrayList<>();
        ContainerIconCache.SvgFetcher fetcher = slug -> {
            calls.add(slug);
            return Set.of("nginx", "redis").contains(slug) ? Optional.of(SVG) : Optional.empty();
        };
        ContainerSnapshot web = container("0123456789ab", "web", "nginx:alpine", null);
        ContainerSnapshot odd = container("abcdef012345", "zzzz", "acme/zzz", null);

        ContainerIconCache cache = new ContainerIconCache(file, fetcher);
        assertSame(ContainerIconCache.PLACEHOLDER, cache.resolve(web));
        assertTrue(cache.needsSync(List.of(web, odd)));
        assertTrue(cache.sync(List.of(web, odd)));
        assertFalse(cache.resolve(web).placeholder());
        assertEquals("⣿⣿⣿⣿⣿⣿", cache.resolve(web).rows(true)[1]);
        assertSame(ContainerIconCache.PLACEHOLDER, cache.resolve(odd));
        assertTrue(Files.isRegularFile(file));
        assertEquals(1, calls.stream().filter("nginx"::equals).count());

        // A later run loads the file: no fetches, unknown slugs stay negatively cached.
        ContainerIconCache reloaded = new ContainerIconCache(file, slug -> {
            throw new AssertionError("must not fetch " + slug);
        });
        assertFalse(reloaded.resolve(web).placeholder());
        assertFalse(reloaded.needsSync(List.of(web, odd)));
        assertFalse(reloaded.sync(List.of(web, odd)));

        // A container added later is the only one converted.
        calls.clear();
        ContainerIconCache growing = new ContainerIconCache(file, fetcher);
        ContainerSnapshot cache2 = container("112233445566", "cache", "redis:7-alpine", null);
        assertTrue(growing.needsSync(List.of(web, odd, cache2)));
        assertTrue(growing.sync(List.of(web, odd, cache2)));
        assertEquals(List.of("redis"), calls);
        assertFalse(growing.resolve(cache2).placeholder());
    }

    @Test
    void ignoresACorruptCacheFile() throws Exception {
        Path file = directory.resolve("icons.json");
        Files.writeString(file, "not json");

        ContainerIconCache cache = new ContainerIconCache(file, slug -> Optional.empty());

        assertSame(ContainerIconCache.PLACEHOLDER,
                cache.resolve(container("0123456789ab", "web", "nginx", null)));
    }

    @Test
    void inMemoryCacheNeverFindsIcons() {
        ContainerIconCache cache = ContainerIconCache.inMemory();
        ContainerSnapshot web = container("0123456789ab", "web", "nginx", null);

        assertFalse(cache.sync(List.of(web)));
        assertSame(ContainerIconCache.PLACEHOLDER, cache.resolve(web));
        assertEquals(3, ContainerIconCache.PLACEHOLDER.rows(true).length);
        assertEquals(3, ContainerIconCache.PLACEHOLDER.rows(false).length);
    }
}
