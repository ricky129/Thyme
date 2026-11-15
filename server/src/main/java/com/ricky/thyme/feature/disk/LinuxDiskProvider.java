package com.ricky.thyme.feature.disk;

import com.ricky.thyme.exec.CommandRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class LinuxDiskProvider implements DiskProvider {
    private static final long BYTES_PER_SECTOR = 512;
    private static final Pattern PSEUDO_DISK = Pattern.compile("^(loop|ram|zram)[0-9]*.*$");
    private final Path diskStatsPath;
    private final Path blockRoot;
    private final Path mountsPath;
    private final Path filesystemRoot;
    private final CommandRunner commandRunner;
    private Map<String, Counters> previousCounters = Map.of();
    private final Map<String, List<String>> btrfsDevices = new HashMap<>();
    private final Map<String, Long> btrfsDeviceReadNanos = new HashMap<>();
    private long previousSampleNanos;

    public LinuxDiskProvider() {
        this(Path.of("/"));
    }

    public LinuxDiskProvider(Path hostRoot) {
        this(hostRoot, hostRoot.resolve("sys"));
    }

    public LinuxDiskProvider(Path hostRoot, Path sysRoot) {
        this(hostRoot, sysRoot, (CommandRunner) null);
    }

    public LinuxDiskProvider(Path hostRoot, Path sysRoot, CommandRunner commandRunner) {
        this(
                hostRoot.resolve("proc/diskstats"),
                sysRoot.resolve("block"),
                hostRoot.equals(Path.of("/"))
                        ? hostRoot.resolve("proc/mounts")
                        : hostRoot.resolve("proc/1/mounts"),
                hostRoot,
                commandRunner);
    }

    LinuxDiskProvider(Path diskStatsPath, Path blockRoot, Path mountsPath) {
        this(diskStatsPath, blockRoot, mountsPath, Path.of("/"));
    }

    LinuxDiskProvider(Path diskStatsPath, Path blockRoot, Path mountsPath, Path filesystemRoot) {
        this(diskStatsPath, blockRoot, mountsPath, filesystemRoot, null);
    }

    LinuxDiskProvider(
            Path diskStatsPath, Path blockRoot, Path mountsPath,
            Path filesystemRoot, CommandRunner commandRunner) {
        this.diskStatsPath = diskStatsPath;
        this.blockRoot = blockRoot;
        this.mountsPath = mountsPath;
        this.filesystemRoot = filesystemRoot.toAbsolutePath().normalize();
        this.commandRunner = commandRunner;
    }

    @Override
    public synchronized DiskSnapshot read() throws IOException {
        long nowNanos = System.nanoTime();
        Map<String, Counters> currentCounters = parseDiskStats(Files.readString(diskStatsPath));
        List<DiskSnapshot.Device> devices = readDevices(currentCounters, nowNanos);
        List<DiskSnapshot.Filesystem> filesystems = readFilesystems(currentCounters, nowNanos);
        previousCounters = currentCounters;
        previousSampleNanos = nowNanos;
        return new DiskSnapshot(devices, filesystems);
    }

    static Map<String, Counters> parseDiskStats(String contents) throws IOException {
        Map<String, Counters> counters = new HashMap<>();
        for (String line : contents.lines().toList()) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 14)
                throw new IOException("incomplete /proc/diskstats row: " + line);
            String name = fields[2];
            if (PSEUDO_DISK.matcher(name).matches())
                continue;
            try {
                counters.put(name, new Counters(
                        Long.parseLong(fields[3]),
                        Long.parseLong(fields[5]),
                        Long.parseLong(fields[6]),
                        Long.parseLong(fields[7]),
                        Long.parseLong(fields[9]),
                        Long.parseLong(fields[10]),
                        Long.parseLong(fields[12])));
            } catch (NumberFormatException e) {
                throw new IOException("non-numeric /proc/diskstats counters: " + line, e);
            }
        }
        return Map.copyOf(counters);
    }

    static DiskSnapshot.Device calculateDevice(
            String name, long sizeBytes, Counters previous, Counters current, long elapsedNanos) {
        if (previous == null || elapsedNanos <= 0 || !current.dominates(previous))
            return new DiskSnapshot.Device(name, sizeBytes, null, null, null, null, null, null, null);
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        long readOperations = current.readsCompleted - previous.readsCompleted;
        long writeOperations = current.writesCompleted - previous.writesCompleted;
        long readSectors = current.sectorsRead - previous.sectorsRead;
        long writeSectors = current.sectorsWritten - previous.sectorsWritten;
        long readMillis = current.readMillis - previous.readMillis;
        long writeMillis = current.writeMillis - previous.writeMillis;
        long ioMillis = current.ioMillis - previous.ioMillis;
        return new DiskSnapshot.Device(
                name,
                sizeBytes,
                readSectors * (double) BYTES_PER_SECTOR / elapsedSeconds,
                writeSectors * (double) BYTES_PER_SECTOR / elapsedSeconds,
                readOperations / elapsedSeconds,
                writeOperations / elapsedSeconds,
                Math.min(100, ioMillis * 1_000_000.0 / elapsedNanos * 100),
                readOperations == 0 ? null : readMillis / (double) readOperations,
                writeOperations == 0 ? null : writeMillis / (double) writeOperations);
    }

    private List<DiskSnapshot.Device> readDevices(Map<String, Counters> current, long nowNanos)
            throws IOException {
        if (!Files.isDirectory(blockRoot))
            throw new IOException("sysfs block directory is unavailable: " + blockRoot);
        long elapsedNanos = previousSampleNanos == 0 ? 0 : nowNanos - previousSampleNanos;
        List<DiskSnapshot.Device> devices = new ArrayList<>();
        try (Stream<Path> paths = Files.list(blockRoot)) {
            for (Path devicePath : paths.sorted().toList()) {
                String name = devicePath.getFileName().toString();
                if (PSEUDO_DISK.matcher(name).matches())
                    continue;
                Counters counters = current.get(name);
                if (counters == null)
                    continue;
                devices.add(calculateDevice(
                        name,
                        readDeviceSize(devicePath.resolve("size")),
                        previousCounters.get(name),
                        counters,
                        elapsedNanos));
                try (Stream<Path> children = Files.list(devicePath)) {
                    for (Path partitionPath : children.sorted().toList()) {
                        if (!Files.isRegularFile(partitionPath.resolve("partition")))
                            continue;
                        String partitionName = partitionPath.getFileName().toString();
                        Counters partitionCounters = current.get(partitionName);
                        if (partitionCounters == null)
                            continue;
                        devices.add(calculateDevice(
                                partitionName,
                                readDeviceSize(partitionPath.resolve("size")),
                                previousCounters.get(partitionName),
                                partitionCounters,
                                elapsedNanos));
                    }
                }
            }
        }
        return List.copyOf(devices);
    }

    private List<DiskSnapshot.Filesystem> readFilesystems(Map<String, Counters> current, long nowNanos)
            throws IOException {
        Set<String> seenMountPoints = new HashSet<>();
        List<DiskSnapshot.Filesystem> filesystems = new ArrayList<>();
        for (String line : Files.readAllLines(mountsPath)) {
            String[] fields = line.split("\\s+");
            if (fields.length < 3)
                throw new IOException("invalid /proc/mounts row: " + line);
            String source = unescapeMountField(fields[0]);
            String mountPoint = unescapeMountField(fields[1]);
            String type = unescapeMountField(fields[2]);
            if (isPseudoFilesystem(type) || !seenMountPoints.add(mountPoint))
                continue;
            Path mount = Path.of(mountPoint).normalize();
            if (!mount.isAbsolute())
                throw new IOException("mount point is not absolute: " + mountPoint);
            Path mountPath = filesystemRoot.resolve(mount.toString().substring(1)).normalize();
            if (!mountPath.startsWith(filesystemRoot))
                throw new IOException("mount point is outside the host root: " + mountPoint);
            if (!Files.isDirectory(mountPath))
                continue;
            try {
                var fileStore = Files.getFileStore(mountPath);
                long total = Math.max(0, fileStore.getTotalSpace());
                long available = Math.min(total, Math.max(0, fileStore.getUsableSpace()));
                long used = Math.min(total, Math.max(0, total - fileStore.getUnallocatedSpace()));
                List<String> backing = backingDevices(source, mountPoint, type);
                filesystems.add(filesystemSnapshot(
                        source, mountPoint, type, total, available, used, backing, current, nowNanos));
            } catch (IOException | SecurityException e) {
                throw new IOException("cannot read filesystem " + mountPoint, e);
            }
        }
        return List.copyOf(filesystems);
    }

    private DiskSnapshot.Filesystem filesystemSnapshot(
            String source,
            String mountPoint,
            String type,
            long total,
            long available,
            long used,
            List<String> backing,
            Map<String, Counters> current,
            long nowNanos) {
        if (backing.isEmpty() || previousSampleNanos == 0 || nowNanos <= previousSampleNanos) {
            return new DiskSnapshot.Filesystem(source, mountPoint, type, total, available, used,
                    backing, null, null, null, null, null);
        }
        long elapsed = nowNanos - previousSampleNanos;
        double readRate = 0;
        double writeRate = 0;
        double utilization = 0;
        long readOps = 0;
        long writeOps = 0;
        long readMillis = 0;
        long writeMillis = 0;
        int measured = 0;
        for (String device : backing) {
            Counters before = previousCounters.get(device);
            Counters after = current.get(device);
            if (before == null || after == null || !after.dominates(before))
                continue;
            DiskSnapshot.Device sample = calculateDevice(device, 0, before, after, elapsed);
            readRate += sample.readBytesPerSecond() == null ? 0 : sample.readBytesPerSecond();
            writeRate += sample.writeBytesPerSecond() == null ? 0 : sample.writeBytesPerSecond();
            utilization += sample.utilizationPercent() == null ? 0 : sample.utilizationPercent();
            readOps += after.readsCompleted - before.readsCompleted;
            writeOps += after.writesCompleted - before.writesCompleted;
            readMillis += after.readMillis - before.readMillis;
            writeMillis += after.writeMillis - before.writeMillis;
            measured++;
        }
        if (measured == 0) {
            return new DiskSnapshot.Filesystem(source, mountPoint, type, total, available, used,
                    backing, null, null, null, null, null);
        }
        return new DiskSnapshot.Filesystem(
                source, mountPoint, type, total, available, used, backing,
                readRate, writeRate, utilization / measured,
                readOps == 0 ? null : readMillis / (double) readOps,
                writeOps == 0 ? null : writeMillis / (double) writeOps);
    }

    private List<String> backingDevices(String source, String mountPoint, String type) throws IOException {
        if ("btrfs".equals(type) && commandRunner != null) {
            long now = System.nanoTime();
            Long previous = btrfsDeviceReadNanos.get(mountPoint);
            if (previous != null && now - previous < Duration.ofSeconds(10).toNanos())
                return btrfsDevices.getOrDefault(mountPoint, List.of());
            try {
                var result = commandRunner.run(
                        List.of("btrfs", "filesystem", "show", "--raw", mountPoint),
                        Duration.ofSeconds(3));
                if (result.ok()) {
                    List<String> devices = result.stdout().lines()
                            .map(String::strip)
                            .filter(line -> line.contains(" path "))
                            .map(line -> line.substring(line.lastIndexOf(" path ") + 6).strip())
                            .map(LinuxDiskProvider::blockName)
                            .filter(name -> isBlockDevice(name))
                            .distinct()
                            .toList();
                    btrfsDeviceReadNanos.put(mountPoint, now);
                    btrfsDevices.put(mountPoint, devices);
                    if (!devices.isEmpty())
                        return devices;
                }
                btrfsDeviceReadNanos.put(mountPoint, now);
                btrfsDevices.put(mountPoint, List.of());
                return List.of();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Btrfs device read interrupted for " + mountPoint, e);
            } catch (IOException e) {
                return List.of();
            }
        }
        String deviceName = source.startsWith("/dev/") ? blockName(source) : "";
        if (deviceName.isEmpty() || !isBlockDevice(deviceName))
            return List.of();
        return List.of(deviceName);
    }

    private static String blockName(String path) {
        Path devicePath = Path.of(path);
        if (!devicePath.isAbsolute())
            return "";
        try {
            if (Files.exists(devicePath))
                return devicePath.toRealPath().getFileName().toString();
        } catch (IOException ignored) {
            return devicePath.getFileName().toString();
        }
        return devicePath.getFileName().toString();
    }

    private boolean isBlockDevice(String name) {
        if (Files.exists(blockRoot.resolve(name)))
            return true;
        try (Stream<Path> devices = Files.list(blockRoot)) {
            return devices.anyMatch(device -> Files.exists(device.resolve(name).resolve("partition")));
        } catch (IOException e) {
            return false;
        }
    }

    private static long readDeviceSize(Path sizeFile) throws IOException {
        String text = Files.readString(sizeFile).strip();
        try {
            long sectors = Long.parseLong(text);
            return Math.multiplyExact(sectors, BYTES_PER_SECTOR);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IOException("invalid disk size in " + sizeFile, e);
        }
    }

    private static boolean isPseudoFilesystem(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "proc", "sysfs", "devtmpfs", "devpts", "tmpfs", "cgroup", "cgroup2",
                    "securityfs", "debugfs", "tracefs", "configfs", "mqueue", "fusectl",
                    "pstore", "hugetlbfs", "rpc_pipefs", "autofs", "squashfs", "overlay" -> true;
            default -> false;
        };
    }

    private static String unescapeMountField(String value) {
        return value.replace("\\040", " ")
                .replace("\\011", "\t")
                .replace("\\012", "\n")
                .replace("\\134", "\\");
    }

    private record Counters(
            long readsCompleted,
            long sectorsRead,
            long readMillis,
            long writesCompleted,
            long sectorsWritten,
            long writeMillis,
            long ioMillis) {
        boolean dominates(Counters other) {
            return readsCompleted >= other.readsCompleted
                    && sectorsRead >= other.sectorsRead
                    && readMillis >= other.readMillis
                    && writesCompleted >= other.writesCompleted
                    && sectorsWritten >= other.sectorsWritten
                    && writeMillis >= other.writeMillis
                    && ioMillis >= other.ioMillis;
        }
    }
}
