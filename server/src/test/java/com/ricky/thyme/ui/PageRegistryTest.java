package com.ricky.thyme.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRegistryTest {
    @Test
    void selectsPagesByNumberAndCyclesWithTab() {
        PageRegistry registry = registry(3);

        assertTrue(registry.handleNavigation(new KeyStroke('3', false, false)));
        assertEquals(2, registry.activeIndex());
        assertTrue(registry.handleNavigation(new KeyStroke(KeyType.Tab)));
        assertEquals(0, registry.activeIndex());
        assertTrue(registry.handleNavigation(new KeyStroke(KeyType.ReverseTab)));
        assertEquals(2, registry.activeIndex());
    }

    @Test
    void arrowsAndVimKeysMoveBetweenPages() {
        PageRegistry registry = registry(3);

        assertTrue(registry.handleNavigation(new KeyStroke('l', false, false)));
        assertEquals(1, registry.activeIndex());
        assertTrue(registry.handleNavigation(new KeyStroke(KeyType.ArrowLeft)));
        assertEquals(0, registry.activeIndex());
        assertTrue(registry.handleNavigation(new KeyStroke('h', false, false)));
        assertEquals(2, registry.activeIndex());
    }

    @Test
    void rejectsDuplicateIdsAndReturnsToRoot() {
        Page first = new TestPage();
        Page second = new TestPage();
        assertThrows(IllegalArgumentException.class,
                () -> new PageRegistry(List.of(
                        new PageRegistry.Entry("same", first),
                        new PageRegistry.Entry("same", second))));

        PageRegistry registry = new PageRegistry(List.of(
                new PageRegistry.Entry("one", first),
                new PageRegistry.Entry("two", second)));
        registry.handleNavigation(new KeyStroke('2', false, false));
        assertTrue(registry.returnToRoot());
        assertEquals(0, registry.activeIndex());
        assertFalse(registry.returnToRoot());
    }

    private static PageRegistry registry(int count) {
        List<PageRegistry.Entry> entries = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new PageRegistry.Entry("page-" + index, new TestPage()))
                .toList();
        return new PageRegistry(entries);
    }

    private static final class TestPage extends Page {
        @Override
        public String title() {
            return "test";
        }

        @Override
        public void handleKey(KeyStroke key) {
            // Test page has no domain input.
        }

        @Override
        public void refresh() {
            // Test page has no background work.
        }

        @Override
        public void render(TextGraphics graphics, TerminalSize size) {
            // Test page has no visual output.
        }
    }
}
