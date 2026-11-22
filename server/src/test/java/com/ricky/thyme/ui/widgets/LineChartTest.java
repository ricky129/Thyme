package com.ricky.thyme.ui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.ricky.thyme.ui.Theme;
import java.util.List;
import org.junit.jupiter.api.Test;

class LineChartTest {

    private static final int CHART_X = 5;
    private static final int CHART_Y = 1;
    private static final int CHART_W = 35;
    private static final int CHART_H = 10;

    @Test
    void braillePlotDrawsADotMatrixInsteadOfSolidBlocks() throws Exception {
        var terminal = new DefaultVirtualTerminal[1];
        try (TerminalScreen screen = screen(40, 12, terminal)) {
            LineChart.draw(screen.newTextGraphics(), 0, 0, 40, 12, "CPU", "%",
                    ramp(40), 100, Theme.CPU_SERIES, -1, null, true);
            screen.refresh();

            int braille = 0;
            for (int row = CHART_Y; row < CHART_Y + CHART_H; row++) {
                for (int col = CHART_X; col < CHART_X + CHART_W; col++) {
                    char character = terminal[0].getCharacter(col, row).getCharacter();
                    if (character >= 0x2800 && character <= 0x28FF) {
                        braille++;
                    }
                    assertFalse(character == '█', "braille plot must not draw solid blocks");
                }
            }
            assertTrue(braille > 0, "braille plot should draw at least one dot cell");
        }
    }

    @Test
    void blockFallbackStillDrawsSolidColumns() throws Exception {
        var terminal = new DefaultVirtualTerminal[1];
        try (TerminalScreen screen = screen(40, 12, terminal)) {
            LineChart.draw(screen.newTextGraphics(), 0, 0, 40, 12, "CPU", "%",
                    ramp(40), 100, Theme.CPU_SERIES, -1, null, false);
            screen.refresh();

            boolean block = false;
            for (int row = CHART_Y; row < CHART_Y + CHART_H; row++) {
                for (int col = CHART_X; col < CHART_X + CHART_W; col++) {
                    block |= terminal[0].getCharacter(col, row).getCharacter() == '█';
                }
            }
            assertTrue(block, "block fallback should draw solid columns");
        }
    }

    @Test
    void zeroSeriesKeepsADottedBaseline() throws Exception {
        var terminal = new DefaultVirtualTerminal[1];
        try (TerminalScreen screen = screen(40, 12, terminal)) {
            double[] zeros = new double[40];
            LineChart.draw(screen.newTextGraphics(), 0, 0, 40, 12, "CPU", "%",
                    zeros, 100, Theme.CPU_SERIES, -1, null, true);
            screen.refresh();

            int bottomRow = CHART_Y + CHART_H - 1;
            boolean baseline = false;
            for (int col = CHART_X; col < CHART_X + CHART_W; col++) {
                char character = terminal[0].getCharacter(col, bottomRow).getCharacter();
                baseline |= character >= 0x2800 && character <= 0x28FF;
            }
            assertTrue(baseline, "a zero series should still draw its baseline");
        }
    }

    @Test
    void crosshairLandsOnTheNewestBrailleColumn() throws Exception {
        var terminal = new DefaultVirtualTerminal[1];
        try (TerminalScreen screen = screen(40, 12, terminal)) {
            double[] samples = ramp(10);
            LineChart.draw(screen.newTextGraphics(), 0, 0, 40, 12, "CPU", "%",
                    samples, 100, Theme.CPU_SERIES, samples.length - 1, null, true);
            screen.refresh();

            assertEquals('│',
                    terminal[0].getCharacter(CHART_X + CHART_W - 1, CHART_Y + 2).getCharacter());
        }
    }

    @Test
    void hiddenCursorDrawsNoCrosshairInMultiSeriesCharts() throws Exception {
        var terminal = new DefaultVirtualTerminal[1];
        try (TerminalScreen screen = screen(40, 12, terminal)) {
            double[] samples = ramp(20);
            LineChart.drawMulti(screen.newTextGraphics(), 0, 0, 40, 12, "RAM", "%",
                    List.of(new LineChart.Series("used", samples, Theme.MEMORY_SERIES, false),
                            new LineChart.Series("swap", samples, Theme.SWAP_SERIES, true)),
                    100, -1, null, true);
            screen.refresh();

            for (int row = 0; row < 12; row++) {
                for (int col = 0; col < 40; col++) {
                    assertFalse(terminal[0].getCharacter(col, row).getCharacter() == '│',
                            "a hidden cursor must not draw a crosshair");
                }
            }
        }
    }

    private static TerminalScreen screen(int width, int height, DefaultVirtualTerminal[] terminal)
            throws Exception {
        terminal[0] = new DefaultVirtualTerminal(new TerminalSize(width, height));
        TerminalScreen screen = new TerminalScreen(terminal[0]);
        screen.startScreen();
        return screen;
    }

    private static double[] ramp(int length) {
        double[] samples = new double[length];
        for (int index = 0; index < length; index++) {
            samples[index] = index * 100.0 / Math.max(1, length - 1);
        }
        return samples;
    }
}
