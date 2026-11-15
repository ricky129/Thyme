package com.ricky.thyme.remote;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public final class ThymeConfig {
    private final Properties properties;

    private ThymeConfig(Properties properties) {
        this.properties = properties;
    }

    public static Optional<Path> findConfigFile() {
        String configuredPath = System.getenv("THYME_CONFIG");
        if (configuredPath != null && !configuredPath.isBlank())
            return Optional.of(Path.of(configuredPath).toAbsolutePath().normalize());

        Path userConfig = Path.of(System.getProperty("user.home"), ".config", "thyme", "config.properties");
        if (Files.isRegularFile(userConfig))
            return Optional.of(userConfig);
        Path projectConfig = Path.of("config.properties").toAbsolutePath().normalize();
        return Files.isRegularFile(projectConfig) ? Optional.of(projectConfig) : Optional.empty();
    }

    public static ThymeConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        return new ThymeConfig(properties);
    }

    public static ThymeConfig empty() {
        return new ThymeConfig(new Properties());
    }

    public String get(String key) {
        String value = properties.getProperty(key);
        return value == null ? "" : value.trim();
    }

    public String sshTarget() throws IOException {
        String user = get("deploy.user");
        String host = get("deploy.host");
        if (host.isEmpty())
            host = hostFromUrl(get("server.tailscale"));
        if (host.isEmpty())
            host = hostFromUrl(get("server.local"));
        String target = user + "@" + host;
        if (user.isEmpty() || !SshTarget.isValid(target))
            throw new IOException("configure deploy.user and deploy.host (or server.tailscale) in config.properties");
        return target;
    }

    public String sshPort() throws IOException {
        String port = get("deploy.port");
        if (port.isEmpty())
            return "";
        try {
            int value = Integer.parseInt(port);
            if (value < 1 || value > 65535)
                throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new IOException("deploy.port must be between 1 and 65535", e);
        }
        return port;
    }

    public String sshKey() {
        return get("deploy.key");
    }

    public String remoteDirectory() throws IOException {
        String directory = get("deploy.remote_dir");
        if (directory.isEmpty())
            directory = "~/docker/thyme";
        if (!directory.matches("(?:~/|/)[A-Za-z0-9_./~-]*")
                || directory.contains("/../")
                || directory.endsWith("/..")
                || "..".equals(directory))
            throw new IOException("deploy.remote_dir contains invalid characters or path segments");
        return directory;
    }

    private static String hostFromUrl(String value) {
        if (value.isEmpty())
            return "";
        String host = value.replaceFirst("^https?://", "");
        int end = host.length();
        int slash = host.indexOf('/');
        int colon = host.indexOf(':');
        if (slash >= 0)
            end = Math.min(end, slash);
        if (colon >= 0)
            end = Math.min(end, colon);
        return host.substring(0, end);
    }
}
