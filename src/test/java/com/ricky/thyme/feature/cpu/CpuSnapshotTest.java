package com.ricky.thyme.feature.cpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import org.junit.jupiter.api.Test;

class CpuSnapshotTest {
    @Test
    void preservesPerCoreMetricsAndAllowsFirstSampleToHaveNoLoad() {
        var snapshot = new CpuSnapshot(
                "Example CPU",
                "amd64",
                2,
                null,
                List.of(
                        new CpuSnapshot.Core(0, null, 2_400_000_000L),
                        new CpuSnapshot.Core(1, 42.5, null)),
                1.0,
                0.8,
                0.5);

        assertEquals(2, snapshot.logicalProcessors());
        assertEquals(42.5, snapshot.cores().get(1).usagePercent());
        assertEquals(2_400_000_000L, snapshot.cores().getFirst().frequencyHz());
    }

    @Test
    void rejectsCoreCountsAndPercentagesOutsideTheirValidRanges() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CpuSnapshot("CPU", "amd64", 2, 30.0,
                        List.of(new CpuSnapshot.Core(0, 30.0, null)), null, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CpuSnapshot("CPU", "amd64", 1, 101.0,
                        List.of(new CpuSnapshot.Core(0, 30.0, null)), null, null, null));
    }
}
