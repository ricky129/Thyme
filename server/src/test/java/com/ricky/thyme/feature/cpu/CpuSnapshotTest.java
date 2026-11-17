package com.ricky.thyme.feature.cpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import org.junit.jupiter.api.Test;

class CpuSnapshotTest {
    @Test
    void preservesPerCoreMetrics() {
        var snapshot = new CpuSnapshot(
                "Example CPU",
                "amd64",
                2,
                null,
                58.5,
                List.of(
                        new CpuSnapshot.Core(0, null, 2_400_000_000L),
                        new CpuSnapshot.Core(1, 42.5, null)));

        assertEquals(2, snapshot.logicalProcessors());
        assertEquals(58.5, snapshot.temperatureCelsius());
        assertEquals(42.5, snapshot.cores().get(1).usagePercent());
        assertEquals(2_400_000_000L, snapshot.cores().getFirst().frequencyHz());
    }

    @Test
    void rejectsCoreCountsAndPercentagesOutsideTheirValidRanges() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CpuSnapshot("CPU", "amd64", 2, 30.0, null,
                        List.of(new CpuSnapshot.Core(0, 30.0, null))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CpuSnapshot("CPU", "amd64", 1, 101.0, null,
                        List.of(new CpuSnapshot.Core(0, 30.0, null))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CpuSnapshot("CPU", "amd64", 1, 30.0, Double.NaN,
                        List.of(new CpuSnapshot.Core(0, 30.0, null))));
    }
}
