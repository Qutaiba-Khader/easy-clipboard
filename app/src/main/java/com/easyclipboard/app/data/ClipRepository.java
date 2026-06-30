package com.easyclipboard.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.easyclipboard.app.model.Clip;
import com.easyclipboard.app.util.comparators.NewFirst;
import com.easyclipboard.app.util.comparators.PinnedFirst;
import com.easyclipboard.app.util.comparators.PinnedLast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single source of truth for clip history. Holds the in-memory {@link Clip}
 * list and persists it to an internal file ({@code clips.dat}) with
 * {@link ObjectOutputStream}. The {@link #addClip} logic is ported faithfully
 * from the original Native Clipboard {@code Util.addClip}.
 */
public class ClipRepository {

    private static final String FILE_NAME = "clips.dat";
    /** Sentinel text that must never be stored (original used //NATIVECLIPBOARDCLOSE//). */
    public static final String SENTINEL = "//EASYCLIPBOARDCLOSE//";
    private static final int HISTORY_DEFAULT = 25;
    private static final int HISTORY_UNLIMITED = 999999;

    private static ClipRepository INSTANCE;

    private final List<Clip> clips = new ArrayList<>();
    private boolean loaded = false;

    private ClipRepository() {
    }

    public static synchronized ClipRepository get(Context ctx) {
        if (INSTANCE == null) {
            INSTANCE = new ClipRepository();
            INSTANCE.load(ctx.getApplicationContext());
        }
        return INSTANCE;
    }

    public List<Clip> getClips() {
        return clips;
    }

    private static SharedPreferences prefs(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx.getApplicationContext());
    }

    private int getHistory(Context ctx) {
        SharedPreferences p = prefs(ctx);
        try {
            return p.getInt("history", HISTORY_DEFAULT);
        } catch (Exception e) {
            try {
                return Integer.parseInt(p.getString("history", String.valueOf(HISTORY_DEFAULT)));
            } catch (Exception ignored) {
                return HISTORY_DEFAULT;
            }
        }
    }

    /** Convenience: add raw text (no title) captured from the system clipboard. */
    public synchronized void addText(String text, Context ctx) {
        addText(text, "", ctx);
    }

    public synchronized void addText(String text, String title, Context ctx) {
        if (text == null) {
            return;
        }
        addClip(new Clip(System.currentTimeMillis(), text, title == null ? "" : title, false), ctx);
    }

    /**
     * Ported from the original {@code Util.addClip}: dedupe by text (bump time),
     * else insert at index 0, enforce the history-size pref (999999 = unlimited,
     * pinned entries are never trimmed), then sort by the user's sort pref.
     */
    public synchronized List<Clip> addClip(Clip nClip, Context ctx) {
        int contains = Clip.contains(clips, nClip);
        if (contains >= 0) {
            clips.get(contains).setTime(nClip.getTime());
        } else if (contains == -1
                && !nClip.getText().equals(SENTINEL)
                && !nClip.getText().equals("")) {
            clips.add(0, nClip);
            int history = getHistory(ctx);
            if (history != HISTORY_UNLIMITED) {
                int x = clips.size();
                while (clips.size() > history) {
                    if (clips.get(x - 1).isPinned()) {
                        history++;
                    } else {
                        clips.remove(x - 1);
                    }
                    x--;
                }
            }
        }
        sort(ctx);
        save(ctx);
        return clips;
    }

    public synchronized void setPinned(int index, boolean pinned, Context ctx) {
        if (index < 0 || index >= clips.size()) {
            return;
        }
        clips.get(index).setPinned(pinned);
        sort(ctx);
        save(ctx);
    }

    public synchronized void updateText(int index, String text, String title, Context ctx) {
        if (index < 0 || index >= clips.size()) {
            return;
        }
        clips.get(index).setText(text);
        clips.get(index).setTitle(title == null ? "" : title);
        save(ctx);
    }

    public synchronized void remove(int index, Context ctx) {
        if (index < 0 || index >= clips.size()) {
            return;
        }
        clips.remove(index);
        save(ctx);
    }

    public synchronized void sort(Context ctx) {
        String sort = prefs(ctx).getString("sort", "newfirst");
        if ("newfirst".equals(sort)) {
            Collections.sort(clips, new NewFirst());
        } else if ("pinnedfirst".equals(sort)) {
            Collections.sort(clips, new PinnedFirst());
        } else if ("pinnedlast".equals(sort)) {
            Collections.sort(clips, new PinnedLast());
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized void load(Context ctx) {
        if (loaded) {
            return;
        }
        loaded = true;
        File f = new File(ctx.getApplicationContext().getFilesDir(), FILE_NAME);
        if (!f.exists()) {
            return;
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            Object obj = in.readObject();
            if (obj instanceof List) {
                clips.clear();
                clips.addAll((List<Clip>) obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void save(Context ctx) {
        File f = new File(ctx.getApplicationContext().getFilesDir(), FILE_NAME);
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f))) {
            out.writeObject(new ArrayList<>(clips));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
