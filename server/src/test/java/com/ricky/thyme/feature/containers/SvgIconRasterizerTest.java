package com.ricky.thyme.feature.containers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SvgIconRasterizerTest {

    private static String svg(String path) {
        return "<svg role=\"img\" viewBox=\"0 0 24 24\" xmlns=\"http://www.w3.org/2000/svg\">"
                + "<title>T</title><path d=\"" + path + "\"/></svg>";
    }

    private static void assertShape(SvgIconRasterizer.Sprite sprite) {
        assertNotNull(sprite);
        assertEquals(3, sprite.brailleRows().length);
        assertEquals(3, sprite.blockRows().length);
        for (int row = 0; row < 3; row++) {
            assertEquals(6, sprite.brailleRows()[row].length());
            assertEquals(6, sprite.blockRows()[row].length());
        }
    }

    @Test
    void fillsTheWholeSpriteForASolidSquare() {
        SvgIconRasterizer.Sprite sprite = SvgIconRasterizer.rasterize(svg("M0 0h24v24H0z"));

        assertShape(sprite);
        assertEquals("⣿⣿⣿⣿⣿⣿", sprite.brailleRows()[1]);
        assertEquals("██████", sprite.blockRows()[1]);
    }

    @Test
    void leavesHolesEmptyWithTheEvenOddRule() {
        SvgIconRasterizer.Sprite sprite = SvgIconRasterizer.rasterize(
                svg("M0 0h24v24H0zM6 6v12h12V6z"));

        assertShape(sprite);
        assertEquals(' ', sprite.brailleRows()[1].charAt(2));
        assertEquals(' ', sprite.brailleRows()[1].charAt(3));
        assertEquals('⣿', sprite.brailleRows()[1].charAt(0));
        assertEquals(' ', sprite.blockRows()[1].charAt(2));
    }

    @Test
    void parsesMinifiedArcsWithCompactFlags() {
        SvgIconRasterizer.Sprite sprite = SvgIconRasterizer.rasterize(
                svg("M12 2a10 10 0 100 20 10 10 0 100-20z"));

        assertShape(sprite);
        assertEquals('⣿', sprite.brailleRows()[1].charAt(2));
        assertNotEquals('⣿', sprite.brailleRows()[0].charAt(0));
    }

    @Test
    void handlesCurvesRelativeCommandsAndImplicitLineTos() {
        assertShape(SvgIconRasterizer.rasterize(svg("M2 22C2 12 12 2 22 2L22 22Z")));
        assertShape(SvgIconRasterizer.rasterize(svg("M0 24Q12 0 24 24Z")));
        assertShape(SvgIconRasterizer.rasterize(svg("M0 24Q12 0 24 24T0 24z")));
        assertShape(SvgIconRasterizer.rasterize(svg("m0 0 24 0 0 24-24 0z")));
        assertShape(SvgIconRasterizer.rasterize(svg("M2 22c0-10 10-20 20-20s0 20-20 20z")));
        assertShape(SvgIconRasterizer.rasterize(svg("M.5.5H23.5V2e1H.5z")));
    }

    @Test
    void honoursANonDefaultViewBox() {
        String small = "<svg viewBox=\"0 0 100 100\"><path d=\"M0 0h100v100H0z\"/></svg>";

        assertEquals("⣿⣿⣿⣿⣿⣿", SvgIconRasterizer.rasterize(small).brailleRows()[2]);
    }

    @Test
    void returnsNullWithoutUsablePathData() {
        assertNull(SvgIconRasterizer.rasterize(null));
        assertNull(SvgIconRasterizer.rasterize(" "));
        assertNull(SvgIconRasterizer.rasterize("<svg viewBox=\"0 0 24 24\"></svg>"));
        assertNull(SvgIconRasterizer.rasterize(svg("Mfoo")));
        assertNull(SvgIconRasterizer.rasterize(svg("M0 0")));
    }

    @Test
    void buildsSpritesFromReadableBitmaps() {
        assertShape(ContainerIconCache.PLACEHOLDER.brailleRows().length == 3
                ? new SvgIconRasterizer.Sprite(ContainerIconCache.PLACEHOLDER.brailleRows(),
                        ContainerIconCache.PLACEHOLDER.blockRows())
                : null);
        assertTrue(ContainerIconCache.PLACEHOLDER.placeholder());
    }
}
