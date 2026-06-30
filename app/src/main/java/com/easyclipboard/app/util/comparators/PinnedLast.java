package com.easyclipboard.app.util.comparators;

import com.easyclipboard.app.model.Clip;

import java.util.Comparator;

/** Pinned clips last. Ported from com.dhm47.nativeclipboard.comparators.PinnedLast. */
public class PinnedLast implements Comparator<Clip> {
    @Override
    public int compare(Clip lhs, Clip rhs) {
        if (lhs.isPinned() && rhs.isPinned()) {
            if (lhs.getTime() > rhs.getTime()) {
                return -1;
            }
            return lhs.getTime() == rhs.getTime() ? 0 : 1;
        }
        if (rhs.isPinned()) {
            return -1;
        }
        if (lhs.isPinned()) {
            return 1;
        }
        if (lhs.getTime() <= rhs.getTime()) {
            return lhs.getTime() == rhs.getTime() ? 0 : 1;
        }
        return -1;
    }
}
