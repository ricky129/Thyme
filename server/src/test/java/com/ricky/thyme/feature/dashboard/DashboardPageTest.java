package com.ricky.thyme.feature.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import org.junit.jupiter.api.Test;

class DashboardPageTest {
    @Test
    void directionalKeysMoveAcrossKpiCardsWithoutChangingPage() {
        DashboardPage page = new DashboardPage(null, null, null, null, null);

        page.handleKey(new KeyStroke(KeyType.ArrowRight));
        page.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(2, page.selectedCard());

        page.handleKey(new KeyStroke(KeyType.ArrowLeft));
        page.handleKey(new KeyStroke(KeyType.ArrowUp));
        assertEquals(0, page.selectedCard());
    }

    @Test
    void enterOpensAndEscapeClosesCardDetails() {
        DashboardPage page = new DashboardPage(null, null, null, null, null);

        page.handleKey(new KeyStroke(KeyType.Enter));
        assertEquals(true, page.showingDetails());
        page.handleKey(new KeyStroke(KeyType.Escape));
        assertEquals(false, page.showingDetails());
    }

    @Test
    void detailKeysChangeRangeAndSelectorWithoutLeavingTheOverlay() {
        DashboardPage page = new DashboardPage(null, null, null, null, null);
        page.handleKey(new KeyStroke(KeyType.Enter));
        page.handleKey(new KeyStroke(']', false, false));
        assertEquals(com.ricky.thyme.feature.telemetry.TimeSeriesBuffer.Range.ONE_HOUR, page.detailRange());
        page.handleKey(new KeyStroke(KeyType.Tab));
        assertEquals(true, page.perCoreSelected());
        page.handleKey(new KeyStroke(KeyType.Escape));
        assertEquals(false, page.showingDetails());
    }

    @Test
    void cursorStaysHiddenAtNewestUntilMovingLeft() {
        DashboardPage page = new DashboardPage(null, null, null, null, null);
        page.handleKey(new KeyStroke(KeyType.Enter));
        page.handleKey(new KeyStroke(KeyType.ArrowRight));
        assertEquals(-1, page.detailCursor());
        page.handleKey(new KeyStroke(KeyType.ArrowLeft));
        assertEquals(-1, page.detailCursor());
    }

    @Test
    void detailCapturesTabBeforeRootPageNavigation() {
        DashboardPage page = new DashboardPage(null, null, null, null, null);
        assertEquals(false, page.capturesGlobalNavigation());
        page.handleKey(new KeyStroke(KeyType.Enter));
        assertEquals(true, page.capturesGlobalNavigation());
    }
}
