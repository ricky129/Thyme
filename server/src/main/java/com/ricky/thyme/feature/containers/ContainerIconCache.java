package com.ricky.thyme.feature.containers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Container icons converted from Simple Icons SVGs into 6x3 character sprites.
 *
 * <p>Converted sprites are kept in memory and in a JSON file, so icons known at
 * deploy time are available immediately and new containers are converted later
 * by {@link #sync(List)}. Slugs that cannot be fetched are remembered for 24
 * hours so they are not requested again on every pass.</p>
 */
public final class ContainerIconCache {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long MISSING_TTL_SECONDS = Duration.ofHours(24).toSeconds();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(3);
    private static final Set<String> GENERIC_ORGS = Set.of(
            "library", "linuxserver", "bitnami", "official", "bitnamilegacy");
    private static final Set<String> GENERIC_TOKENS = Set.of(
            "app", "server", "web", "db", "database", "proxy", "latest", "alpine", "slim",
            "stable", "main", "release", "data", "api", "worker", "backend", "frontend",
            "service", "docker", "container");
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("postgres", "postgresql"),
            Map.entry("postgis", "postgresql"),
            Map.entry("timescaledb", "postgresql"),
            Map.entry("mongo", "mongodb"),
            Map.entry("valkey", "redis"),
            Map.entry("traefik", "traefikproxy"),
            Map.entry("node", "nodedotjs"),
            Map.entry("hass", "homeassistant"),
            Map.entry("adguardhome", "adguard"),
            Map.entry("cloudflared", "cloudflare"),
            Map.entry("wgeasy", "wireguard"),
            Map.entry("bitwarden", "vaultwarden"),
            Map.entry("forgejo", "gitea"),
            Map.entry("java", "openjdk"));

    /** Placeholder cube shown while a container has no matching icon. */
    public static final Icon PLACEHOLDER = placeholder();

    /** A resolved sprite. */
    public record Icon(String slug, boolean placeholder, String[] brailleRows, String[] blockRows) {
        /** The three sprite rows for the given terminal capability. */
        public String[] rows(boolean braille) {
            return braille ? brailleRows : blockRows;
        }
    }

    /** Source of SVG documents, injectable so tests never touch the network. */
    @FunctionalInterface
    public interface SvgFetcher {
        Optional<String> fetch(String slug);
    }

    private final Path cacheFile;
    private final SvgFetcher fetcher;
    private final Map<String, Icon> icons = new ConcurrentHashMap<>();
    private final Map<String, Long> missing = new ConcurrentHashMap<>();

    public ContainerIconCache(Path cacheFile, SvgFetcher fetcher) {
        this.cacheFile = cacheFile;
        this.fetcher = fetcher;
        load();
    }

    /** Disk-backed cache that fetches from the Simple Icons CDN. */
    public static ContainerIconCache defaultCache() {
        String configured = System.getenv("THYME_ICON_CACHE");
        Path file = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home", "/tmp"), ".cache", "thyme",
                        "container-icons.json")
                : Path.of(configured);
        return new ContainerIconCache(file, httpFetcher());
    }

    /** Cache that never touches the disk or the network. */
    public static ContainerIconCache inMemory() {
        return new ContainerIconCache(null, slug -> Optional.empty());
    }

    private static SvgFetcher httpFetcher() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        return slug -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://cdn.simpleicons.org/" + slug))
                        .timeout(HTTP_TIMEOUT).GET().build();
                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("<svg")) {
                    return Optional.of(response.body());
                }
                return Optional.empty();
            } catch (IOException | IllegalArgumentException e) {
                return Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        };
    }

    private static Icon placeholder() {
        SvgIconRasterizer.Sprite sprite = SvgIconRasterizer.fromBitmap(new String[]{
            "............",
            ".##########.",
            ".#........#.",
            ".##########.",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            ".#........#.",
            ".##########.",
            "............"});
        return new Icon("placeholder", true, sprite.brailleRows(), sprite.blockRows());
    }

    // ------------------------------------------------------------------
    // Slug resolution
    // ------------------------------------------------------------------

    /** Slugs to try for a container, best match first. */
    public static List<String> candidateSlugs(ContainerSnapshot container) {
        Set<String> slugs = new LinkedHashSet<>();
        String label = container.icon();
        if (label != null && !label.isBlank()) {
            String trimmed = label.strip();
            if (trimmed.regionMatches(true, 0, "si:", 0, 3)) {
                trimmed = trimmed.substring(3);
            }
            addSlug(slugs, trimmed);
        }
        addImageSlugs(slugs, container.image());
        String name = container.name();
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        addNameSlugs(slugs, name);
        return List.copyOf(slugs);
    }

    private static void addImageSlugs(Set<String> slugs, String image) {
        if (image == null || image.isBlank()) {
            return;
        }
        String reference = image;
        int digest = reference.indexOf('@');
        if (digest >= 0) {
            reference = reference.substring(0, digest);
        }
        String[] segments = reference.split("/");
        int start = 0;
        if (segments.length > 1 && (segments[0].contains(".") || segments[0].contains(":")
                || "localhost".equals(segments[0]))) {
            start = 1;
        }
        String repo = segments[segments.length - 1];
        int colon = repo.indexOf(':');
        if (colon >= 0) {
            repo = repo.substring(0, colon);
        }
        addNameSlugs(slugs, repo);
        for (int index = start; index < segments.length - 1; index++) {
            if (!GENERIC_ORGS.contains(segments[index])) {
                addSlug(slugs, segments[index]);
            }
        }
    }

    private static void addNameSlugs(Set<String> slugs, String name) {
        addSlug(slugs, name);
        for (String token : name.split("[-_.]")) {
            if (token.length() >= 2 && !GENERIC_TOKENS.contains(token.toLowerCase(Locale.ROOT))) {
                addSlug(slugs, token);
            }
        }
    }

    private static void addSlug(Set<String> slugs, String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (normalized.isEmpty() || GENERIC_TOKENS.contains(normalized)) {
            return;
        }
        slugs.add(ALIASES.getOrDefault(normalized, normalized));
    }

    // ------------------------------------------------------------------
    // Lookup and sync
    // ------------------------------------------------------------------

    /** The cached icon for a container, or {@link #PLACEHOLDER}. */
    public Icon resolve(ContainerSnapshot container) {
        for (String slug : candidateSlugs(container)) {
            Icon icon = icons.get(slug);
            if (icon != null) {
                return icon;
            }
        }
        return PLACEHOLDER;
    }

    /** Whether {@link #sync(List)} could still find an icon for some container. */
    public boolean needsSync(List<ContainerSnapshot> containers) {
        for (ContainerSnapshot container : containers) {
            if (resolve(container) == PLACEHOLDER && hasFetchableSlug(container)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFetchableSlug(ContainerSnapshot container) {
        for (String slug : candidateSlugs(container)) {
            if (!isFreshlyMissing(slug)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFreshlyMissing(String slug) {
        Long since = missing.get(slug);
        return since != null && Instant.now().getEpochSecond() - since < MISSING_TTL_SECONDS;
    }

    /**
     * Converts icons for containers that do not have one yet.
     *
     * @return {@code true} when at least one new icon is now available
     */
    public boolean sync(List<ContainerSnapshot> containers) {
        boolean iconsChanged = false;
        boolean dirty = false;
        for (ContainerSnapshot container : containers) {
            if (resolve(container) != PLACEHOLDER) {
                continue;
            }
            for (String slug : candidateSlugs(container)) {
                if (isFreshlyMissing(slug)) {
                    continue;
                }
                SvgIconRasterizer.Sprite sprite = fetcher.fetch(slug)
                        .map(SvgIconRasterizer::rasterize).orElse(null);
                dirty = true;
                if (sprite != null) {
                    icons.put(slug, new Icon(slug, false, sprite.brailleRows(), sprite.blockRows()));
                    missing.remove(slug);
                    iconsChanged = true;
                    break;
                }
                missing.put(slug, Instant.now().getEpochSecond());
            }
        }
        if (dirty) {
            save();
        }
        return iconsChanged;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private void load() {
        if (cacheFile == null || !Files.isRegularFile(cacheFile)) {
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(Files.readString(cacheFile));
            JsonNode iconNodes = root.path("icons");
            Iterator<Map.Entry<String, JsonNode>> fields = iconNodes.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String[] braille = rows(entry.getValue().path("braille"));
                String[] block = rows(entry.getValue().path("block"));
                if (braille != null && block != null) {
                    icons.put(entry.getKey(), new Icon(entry.getKey(), false, braille, block));
                }
            }
            Iterator<Map.Entry<String, JsonNode>> missingFields = root.path("missing").fields();
            while (missingFields.hasNext()) {
                Map.Entry<String, JsonNode> entry = missingFields.next();
                missing.put(entry.getKey(), entry.getValue().asLong());
            }
        } catch (IOException | RuntimeException e) {
            icons.clear();
            missing.clear();
        }
    }

    private static String[] rows(JsonNode node) {
        if (!node.isArray() || node.size() != 3) {
            return null;
        }
        String[] rows = new String[3];
        for (int index = 0; index < 3; index++) {
            rows[index] = node.get(index).asText("");
            if (rows[index].length() != 6) {
                return null;
            }
        }
        return rows;
    }

    private boolean save() {
        if (cacheFile == null) {
            return false;
        }
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode iconNodes = root.putObject("icons");
        for (Map.Entry<String, Icon> entry : new java.util.TreeMap<>(icons).entrySet()) {
            ObjectNode node = iconNodes.putObject(entry.getKey());
            ArrayNode braille = node.putArray("braille");
            for (String row : entry.getValue().brailleRows()) {
                braille.add(row);
            }
            ArrayNode block = node.putArray("block");
            for (String row : entry.getValue().blockRows()) {
                block.add(row);
            }
        }
        ObjectNode missingNodes = root.putObject("missing");
        for (Map.Entry<String, Long> entry : new java.util.TreeMap<>(missing).entrySet()) {
            missingNodes.put(entry.getKey(), entry.getValue());
        }
        try {
            Path directory = cacheFile.toAbsolutePath().getParent();
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "container-icons", ".tmp");
            Files.writeString(temporary, MAPPER.writeValueAsString(root));
            try {
                Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            // The cache is an optimisation: keep running with the in-memory copy.
            return false;
        }
    }
}
