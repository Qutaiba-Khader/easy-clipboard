package com.easyclipboard.app.model;

import java.io.Serializable;
import java.util.List;

/**
 * A single clipboard entry. Ported from the original Native Clipboard
 * (com.dhm47.nativeclipboard.Clip) — logic preserved, package modernised.
 */
public class Clip implements Serializable {
    private static final long serialVersionUID = 595229838452849440L;

    boolean pinned;
    int position;
    String text;
    long time;
    String title;

    public Clip() {
    }

    public Clip(int position, long time, String text, String title, boolean pinned) {
        this.position = position;
        this.time = time;
        this.text = text;
        this.title = title;
        this.pinned = pinned;
    }

    public Clip(long time, String text, String title, boolean pinned) {
        this.time = time;
        this.text = text;
        this.title = title;
        this.pinned = pinned;
    }

    public int getPosition() {
        return this.position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public long getTime() {
        return this.time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isPinned() {
        return this.pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public static int contains(List<Clip> mClip, Clip clip) {
        int x = -1;
        for (Clip tClip : mClip) {
            x++;
            if (clip.getText().equals(tClip.getText())) {
                return x;
            }
        }
        return -1;
    }
}
