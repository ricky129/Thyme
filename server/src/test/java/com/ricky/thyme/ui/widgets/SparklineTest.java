package com.ricky.thyme.ui.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SparklineTest {

    // ── buildBlock ──────────────────────────────────────────────────────────

    @Test
    void blockExactWidth() {
        double[] h = {0, 25, 50, 75, 100};
        String s = Sparkline.buildBlock(h, 5);
        assertEquals(5, s.length());
        assertEquals(Sparkline.BLOCK[0], s.charAt(0));  // 0 → ▁
        assertEquals(Sparkline.BLOCK[Sparkline.BLOCK.length - 1], s.charAt(4)); // 100 → █
    }

    @Test
    void blockPadsLeftWhenFewerSamplesThanWidth() {
        double[] h = {100};
        String s = Sparkline.buildBlock(h, 5);
        assertEquals(5, s.length());
        // First 4 chars should be the lowest glyph (pad)
        for (int i = 0; i < 4; i++)
            assertEquals(Sparkline.BLOCK[0], s.charAt(i));
        assertEquals(Sparkline.BLOCK[Sparkline.BLOCK.length - 1], s.charAt(4));
    }

    @Test
    void blockTruncatesOldSamples() {
        double[] h = {0, 100, 50};
        String s = Sparkline.buildBlock(h, 2); // only last 2 samples
        assertEquals(2, s.length());
        // 100 → █, 50 → ▄
        assertEquals(Sparkline.BLOCK[Sparkline.BLOCK.length - 1], s.charAt(0));
        assertEquals(Sparkline.BLOCK[4], s.charAt(1)); // 50/100 * 7 ≈ 3.5 → 4
    }

    @Test
    void blockClampsOutOfRangeValues() {
        double[] h = {-50, 150};
        String s = Sparkline.buildBlock(h, 2);
        assertEquals(2, s.length());
        assertEquals(Sparkline.BLOCK[0], s.charAt(0)); // clamped to 0 → ▁
        assertEquals(Sparkline.BLOCK[Sparkline.BLOCK.length - 1], s.charAt(1)); // clamped to 100 → █
    }

    @Test
    void blockEmptyHistoryReturnsSpaces() {
        String s = Sparkline.buildBlock(new double[]{}, 5);
        assertEquals(5, s.length());
    }

    @Test
    void blockZeroWidthReturnsEmpty() {
        String s = Sparkline.buildBlock(new double[]{50}, 0);
        assertEquals("", s);
    }

    // ── buildBraille ────────────────────────────────────────────────────────

    @Test
    void brailleReturnsBrailleCodePoints() {
        double[] h = {0, 50, 100};
        String s = Sparkline.buildBraille(h, 2);
        assertEquals(2, s.codePointCount(0, s.length()),
                "braille output should have exactly 'width' code points");
        // All chars should be in the braille Unicode block U+2800–U+28FF
        s.codePoints().forEach(cp ->
                assertTrue(cp >= 0x2800 && cp <= 0x28FF,
                        "code point " + cp + " not in braille block"));
    }

    @Test
    void brailleZeroWidthReturnsEmpty() {
        String s = Sparkline.buildBraille(new double[]{50}, 0);
        assertEquals("", s);
    }

    @Test
    void brailleEmptyHistoryReturnsSpaces() {
        String s = Sparkline.buildBraille(new double[]{}, 3);
        assertEquals(3, s.length());
    }

    // ── StatCard.buildSparkline (also uses BLOCK) ───────────────────────────

    @Test
    void statCardSparklineWidth() {
        double[] h = {10, 20, 30, 40, 50};
        String s = StatCard.buildSparkline(h, 10);
        assertEquals(10, s.length());
    }

    @Test
    void statCardSparklineZeroWidth() {
        String s = StatCard.buildSparkline(new double[]{50}, 0);
        assertEquals("", s);
    }
}
