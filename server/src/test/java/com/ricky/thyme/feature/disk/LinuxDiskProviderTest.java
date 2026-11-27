package com.ricky.thyme.feature.disk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.ricky.thyme.exec.ICommandRunner;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinuxDiskProviderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void skipsBootAndEfiMountsBeforeReadingSpace() throws Exception {
        Path diskStats = temporaryDirectory.resolve("system-diskstats");
        Path blockRoot = temporaryDirectory.resolve("system-block");
        Path mounts = temporaryDirectory.resolve("system-mounts");
        Files.createDirectories(blockRoot.resolve("sda1"));
        Files.writeString(blockRoot.resolve("sda1/size"), "1000\n");
        Files.writeString(diskStats, "");
        Files.writeString(mounts,
                "/dev/sda1 /boot ext4 rw 0 0\n"
                        + "/dev/sda2 /boot/efi vfat rw 0 0\n"
                        + "/dev/sda3 /efi vfat rw 0 0\n"
                        + "tmpfs /run tmpfs rw 0 0\n");

        var snapshot = new LinuxDiskProvider(diskStats, blockRoot, mounts).read();

        assertTrue(snapshot.filesystems().isEmpty());
    }

    @Test
    void keepsOnlyRealFilesystemsFromTheHost() throws Exception {
        Path diskStats = temporaryDirectory.resolve("diskstats");
        Path blockRoot = temporaryDirectory.resolve("block");
        Path mounts = temporaryDirectory.resolve("mounts");
        Path root = temporaryDirectory.resolve("root");
        Files.createDirectories(root.resolve("mnt/media_drive"));
        Files.createDirectories(blockRoot.resolve("nvme0n1p2"));
        Files.createDirectories(blockRoot.resolve("sda1"));
        Files.writeString(diskStats, "");
        Files.writeString(mounts,
                "/dev/nvme0n1p2 / ext4 rw 0 0\n"
                        + "/dev/sda1 /mnt/media_drive btrfs rw 0 0\n"
                        + "efivarfs /sys/firmware/efi/efivars efivarfs rw 0 0\n"
                        + "bpf /sys/fs/bpf bpf rw 0 0\n"
                        + "binfmt_misc /proc/sys/fs/binfmt_misc binfmt_misc rw 0 0\n"
                        + "tmpfs /run tmpfs rw 0 0\n");

        var snapshot = new LinuxDiskProvider(diskStats, blockRoot, mounts, root).read();

        assertEquals(List.of("/", "/mnt/media_drive"),
                snapshot.filesystems().stream()
                        .map(DiskSnapshot.Filesystem::mountPoint).toList());
    }

    @Test
    void reportsIoRatesAndMountedFilesystemUsage() throws Exception {
        Path diskStats = temporaryDirectory.resolve("diskstats");
        Path blockRoot = temporaryDirectory.resolve("block");
        Path mounts = temporaryDirectory.resolve("mounts");
        Path disk = blockRoot.resolve("sda");
        Path partition = disk.resolve("sda1");
        Path mountPoint = temporaryDirectory.resolve("mounted disk");
        Files.createDirectories(partition);
        Files.createDirectories(mountPoint);
        Files.writeString(disk.resolve("size"), "1000\n");
        Files.writeString(partition.resolve("size"), "400\n");
        Files.writeString(partition.resolve("partition"), "1\n");
        Files.writeString(mounts,
                "/dev/sda " + mountPoint.toString().replace(" ", "\\040") + " ext4 rw 0 0\n"
                        + "tmpfs /run tmpfs rw 0 0\n");

        Files.writeString(diskStats,
                "8 0 sda 10 0 100 50 20 0 200 40 0 60 90\n"
                        + "8 1 sda1 5 0 50 25 10 0 100 20 0 30 45\n");
        var provider = new LinuxDiskProvider(diskStats, blockRoot, mounts);
        var first = provider.read();
        assertNull(first.devices().getFirst().readBytesPerSecond());
        assertEquals(1000L * 512, first.devices().getFirst().sizeBytes());
        assertEquals("sda1", first.devices().get(1).name());
        assertEquals(400L * 512, first.devices().get(1).sizeBytes());

        Files.writeString(diskStats,
                "8 0 sda 14 0 500 90 23 0 600 70 0 120 160\n"
                        + "8 1 sda1 7 0 250 55 12 0 300 40 0 60 95\n");
        var next = provider.read();
        var device = next.devices().getFirst();
        assertTrue(device.readBytesPerSecond() > 0);
        assertTrue(device.writeBytesPerSecond() > 0);
        assertTrue(device.utilizationPercent() > 0);
        assertEquals(10.0, device.averageReadLatencyMillis());
        assertEquals(10.0, device.averageWriteLatencyMillis());
        assertTrue(next.devices().get(1).readBytesPerSecond() > 0);
        assertEquals(1, next.filesystems().size());
        assertEquals(mountPoint.toString(), next.filesystems().getFirst().mountPoint());
    }

    @Test
    void skipsPseudoDevicesAndDecodesEscapedMountFields() throws Exception {
        var counters = LinuxDiskProvider.parseDiskStats(
                "7 0 loop0 1 0 2 3 4 0 5 6 0 7 8\n"
                        + "259 0 nvme0n1 1 0 2 3 4 0 5 6 0 7 8\n");

        assertEquals(1, counters.size());
        assertTrue(counters.containsKey("nvme0n1"));
    }

    @Test
    void resolvesHostMountsInsideConfiguredHostRoot() throws Exception {
        Path hostRoot = temporaryDirectory.resolve("host");
        Path proc = hostRoot.resolve("proc");
        Path sysRoot = temporaryDirectory.resolve("host-sys");
        Path sysBlock = sysRoot.resolve("block");
        Files.createDirectories(proc.resolve("1"));
        Path device = sysBlock.resolve("sda");
        Files.createDirectories(device);
        Files.createDirectories(hostRoot.resolve("home"));
        Files.writeString(device.resolve("size"), "20\n");
        Files.writeString(proc.resolve("diskstats"), "8 0 sda 1 0 2 3 4 0 5 6 0 7 8\n");
        Files.writeString(proc.resolve("1/mounts"), "/dev/root / ext4 rw 0 0\n");

        var snapshot = new LinuxDiskProvider(hostRoot, sysRoot).read();

        assertEquals(1, snapshot.filesystems().size());
        assertEquals("/", snapshot.filesystems().getFirst().mountPoint());
        assertEquals(Files.getFileStore(hostRoot).getTotalSpace(), snapshot.filesystems().getFirst().totalBytes());
        assertEquals(20L * 512, snapshot.devices().getFirst().sizeBytes());
    }

    @Test
    void mapsBtrfsJbodMountToAllBackingDevices() throws Exception {
        Path diskStats = temporaryDirectory.resolve("btrfs-diskstats");
        Path blockRoot = temporaryDirectory.resolve("btrfs-block");
        Path mounts = temporaryDirectory.resolve("btrfs-mounts");
        Path mountPoint = temporaryDirectory.resolve("media_drive");
        Files.createDirectories(blockRoot.resolve("sda").resolve("sda1"));
        Files.createDirectories(blockRoot.resolve("sdb").resolve("sdb1"));
        Files.createDirectories(mountPoint);
        Files.writeString(blockRoot.resolve("sda").resolve("sda1/partition"), "1");
        Files.writeString(blockRoot.resolve("sdb").resolve("sdb1/partition"), "1");
        Files.writeString(mounts, "/dev/sda1 " + mountPoint + " btrfs rw 0 0\n");
        Files.writeString(diskStats,
                "8 1 sda1 10 0 100 50 20 0 200 40 0 60 90\n"
                        + "8 17 sdb1 10 0 100 50 20 0 200 40 0 60 90\n");
        ICommandRunner btrfs = (command, timeout) -> new ICommandRunner.Result(
                0,
                "Label: none uuid: abcd\n"
                        + "Total devices 2 FS bytes used 123\n"
                        + "devid 1 size 100000 used 1000 path /dev/sda1\n"
                        + "devid 2 size 100000 used 1000 path /dev/sdb1\n",
                "");
        var provider = new LinuxDiskProvider(diskStats, blockRoot, mounts, Path.of("/"), btrfs);
        provider.read();
        Files.writeString(diskStats,
                "8 1 sda1 12 0 300 70 22 0 400 60 0 100 150\n"
                        + "8 17 sdb1 13 0 400 80 24 0 500 70 0 110 160\n");

        var filesystem = provider.read().filesystems().getFirst();

        assertEquals(mountPoint.toString(), filesystem.mountPoint());
        assertEquals(List.of("sda1", "sdb1"), filesystem.backingDevices());
        assertTrue(filesystem.readBytesPerSecond() > 0);
        assertTrue(filesystem.writeBytesPerSecond() > 0);
        assertTrue(filesystem.utilizationPercent() > 0);
        assertTrue(filesystem.averageReadLatencyMillis() > 0);
        assertTrue(filesystem.averageWriteLatencyMillis() > 0);
    }
}
