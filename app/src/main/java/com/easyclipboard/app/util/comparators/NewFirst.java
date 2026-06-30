package com.easyclipboard.app.util.comparators;

import com.easyclipboard.app.model.Clip;

import java.util.Comparator;

/** Newest clips first. Ported from com.dhm47.nativeclipboard.comparators.NewFirst. */
public class NewFirst implements Comparator<Clip> {
    @Override
    public int compare(Clip lhs, Clip rhs) {
        if (lhs.getTime() > rhs.getTime()) {
            return -1;
        }
        return lhs.getTime() == rhs.getTime() ? 0 : 1;
    }
}
