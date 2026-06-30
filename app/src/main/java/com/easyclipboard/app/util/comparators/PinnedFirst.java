package com.easyclipboard.app.util.comparators;

import com.easyclipboard.app.model.Clip;

import java.util.Comparator;

/** Pinned clips first, then newest. Ported from com.dhm47.nativeclipboard.comparators.PinnedFirst. */
public class PinnedFirst implements Comparator<Clip> {
    @Override
    public int compare(Clip lhs, Clip rhs) {
        if (lhs.isPinned() && rhs.isPinned()) {
            if (lhs.getTime() > rhs.getTime()) {
                return -1;
            }
            return lhs.getTime() == rhs.getTime() ? 0 : 1;
        }
        if (rhs.isPinned()) {
            return 1;
        }
        if (lhs.isPinned() || lhs.getTime() > rhs.getTime()) {
            return -1;
        }
        return lhs.getTime() == rhs.getTime() ? 0 : 1;
    }
}
