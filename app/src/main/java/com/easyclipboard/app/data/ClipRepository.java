package com.easyclipboard.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single in-memory source of truth for clip history.
 *
 * PERFORMANCE: reads are served from an in-memory list — NOTHING on the hot
 * "show the panel" path touches disk. The list is loaded from {@code clips.dat}
 * exactly once, on a background single-thread executor (kicked off at process
 * start by EasyClipApp). All writes serialize a snapshot to disk on that same
 * background executor, so mutations never block the UI / accessibility main
 * thread. Observers are notified on the main thread so a visible grid/panel
 * refreshes when a clip is captured in the background.
 *
 * THREAD-SAFETY: the in-memory list is mutated only under {@code synchronized(this)}
 * (all public mutators are synchronized, and the background loader uses the same
 * monitor). All UI mutations happen on the main thread; background captures post
 * to the main thread before mutating, so iteration on the main thread is safe.
 * The disk executor only touches a defensive copy (snapshot) — never the live list.
 *
 * The {@link #addClip} logic is ported faithfully from the original
 * Native Clipboard {@code Util.addClip}.
 */
public class ClipRepository {

    private static final String FILE_NAME = "clips.dat";
    public static final String SENTINEL = "//EASYCLIPBOARDCLOSE//";
    private static final int HISTORY_DEFAULT = 25;
    private static final int HISTORY_UNLIMITED = 999999;

    private static volatile ClipRepository INSTANCE;

    private final List<Clip> clips = new ArrayList<>();
    private volatile boolean loaded = false;
    private boolean loadStarted = false;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Runnable> observers = new CopyOnWriteArrayList<>();

    private ClipRepository() {
    }

    public static ClipRepository get(Context ctx) {
        ClipRepository r = INSTANCE;
        if (r == null) {
            synchronized (ClipRepository.class) {
                r = INSTANCE;
                if (r == null) {
                    r = new ClipRepository();
                    INSTANCE = r;
                }
            }
        }
        r.ensureLoaded(ctx.getApplicationContext());
        return r;
    }

    public List<Clip> getClips() {
        return clips;
    }

    public boolean isLoaded() {
        return loaded;
    }

    // ---- observers (main-thread refresh callbacks) -----------------------

    public void addObserver(Runnable r) {
        if (r != null) {
            observers.addIfAbsent(r);
        }
    }

    public void removeObserver(Runnable r) {
        observers.remove(r);
    }

    private void notifyObservers() {
        for (Runnable r : observers) {
            main.post(r);
        }
    }

    // ---- prefs -----------------------------------------------------------

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

    // ---- mutations (in-memory; disk write is async) ----------------------

    public void addText(String text, Context ctx) {
        addText(text, "", ctx);
    }

    public void addText(String text, String title, Context ctx) {
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
        notifyObservers();
        return clips;
    }

    public synchronized void setPinned(int index, boolean pinned, Context ctx) {
        if (index < 0 || index >= clips.size()) {
            return;
        }
        clips.get(index).setPinned(pinned);
        sort(ctx);
        save(ctx);
        notifyObservers();
    }

    public synchronized void updateText(int index, String text, String title, Context ctx) {
        if (index < 0 || index >= clips.size()) {
            return;
        }
        clips.get(index).setText(text);
        clips.get(index).setTitle(title == null ? "" : title);
        save(ctx);
        notifyObservers();
    }

    public synchronized void remove(int index, Context ctx) {
        if (index < 0 || index >= clips.size()) {
            return;
        }
        clips.remove(index);
        save(ctx);
        notifyObservers();
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

    // ---- disk I/O (background only) --------------------------------------

    /** Kick off the one-time background load. Safe to call from any thread. */
    public void ensureLoaded(final Context ctx) {
        synchronized (this) {
            if (loadStarted) {
                return;
            }
            loadStarted = true;
        }
        final Context app = ctx.getApplicationContext();
        io.execute(() -> {
            List<Clip> read = readFromDisk(app);
            synchronized (ClipRepository.this) {
                // Only apply the on-disk history if nothing was added in the
                // (rare) window before the async load finished — never clobber a
                // clip the user already captured at startup.
                if (read != null && clips.isEmpty()) {
                    clips.addAll(read);
                }
                loaded = true;
            }
            notifyObservers();
        });
    }

    /** Schedule an async snapshot write; never blocks the calling thread. */
    public void save(Context ctx) {
        final ArrayList<Clip> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(clips);
        }
        final Context app = ctx.getApplicationContext();
        io.execute(() -> writeToDisk(app, snapshot));
    }

    @SuppressWarnings("unchecked")
    private List<Clip> readFromDisk(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        if (!f.exists()) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            Object obj = in.readObject();
            if (obj instanceof List) {
                return (List<Clip>) obj;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void writeToDisk(Context ctx, List<Clip> snapshot) {
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f))) {
            out.writeObject(snapshot);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
