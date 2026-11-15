package com.ricky.thyme.ui;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Owns the ordered set of root pages and keyboard navigation between them. */
public final class PageRegistry implements AutoCloseable {
    private final List<Entry> entries;
    private int activeIndex;

    public PageRegistry(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("the page registry requires at least one page");
        }
        Set<String> ids = new HashSet<>();
        for (Entry entry : entries) {
            if (entry == null || entry.id().isBlank() || entry.page() == null || !ids.add(entry.id())) {
                throw new IllegalArgumentException("page entries must have unique ids and non-null pages");
            }
        }
        this.entries = List.copyOf(entries);
    }

    public static PageRegistry of(Page... pages) {
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < pages.length; index++) {
            entries.add(new Entry("page-" + (index + 1), pages[index]));
        }
        return new PageRegistry(entries);
    }

    public Page active() {
        return entries.get(activeIndex).page();
    }

    public String activeId() {
        return entries.get(activeIndex).id();
    }

    public int activeIndex() {
        return activeIndex;
    }

    public int size() {
        return entries.size();
    }

    public boolean isRoot() {
        return activeIndex == 0;
    }

    public boolean select(int index) {
        if (index < 0 || index >= entries.size() || index == activeIndex) {
            return false;
        }
        activeIndex = index;
        return true;
    }

    public boolean handleNavigation(KeyStroke key) {
        if (key == null) {
            return false;
        }
        if (key.getKeyType() == KeyType.Tab || key.getKeyType() == KeyType.ReverseTab) {
            return move(key.getKeyType() == KeyType.ReverseTab ? -1 : 1);
        }
        if (key.getKeyType() == KeyType.ArrowLeft) {
            return move(-1);
        }
        if (key.getKeyType() == KeyType.ArrowRight) {
            return move(1);
        }
        if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
            char character = key.getCharacter();
            if (character >= '1' && character <= '9') {
                return select(character - '1');
            }
            if (character == 'h' || character == 'H') {
                return move(-1);
            }
            if (character == 'l' || character == 'L') {
                return move(1);
            }
        }
        return false;
    }

    public boolean returnToRoot() {
        return select(0);
    }

    @Override
    public void close() {
        Set<Page> closed = new HashSet<>();
        for (Entry entry : entries) {
            if (closed.add(entry.page())) {
                entry.page().close();
            }
        }
    }

    public record Entry(String id, Page page) {
    }

    private boolean move(int delta) {
        int next = (activeIndex + delta + entries.size()) % entries.size();
        return select(next);
    }
}
