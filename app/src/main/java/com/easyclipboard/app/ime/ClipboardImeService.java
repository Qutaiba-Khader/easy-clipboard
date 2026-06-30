package com.easyclipboard.app.ime;

import android.content.ClipboardManager;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputConnection;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.easyclipboard.app.R;
import com.easyclipboard.app.data.ClipRepository;
import com.easyclipboard.app.model.Clip;
import com.easyclipboard.app.ui.ClipAdapter;

import java.util.List;

/**
 * Minimal clip-tray keyboard: a horizontal RecyclerView of recent clips.
 * Tapping a clip commits its text into the focused field. While shown it pulls
 * the current system clipboard into history (an IME is allowed to read it).
 */
public class ClipboardImeService extends InputMethodService implements ClipAdapter.OnItemListener {

    private ClipRepository repo;
    private ClipAdapter adapter;

    @Override
    public View onCreateInputView() {
        repo = ClipRepository.get(this);
        View root = LayoutInflater.from(this).inflate(R.layout.ime_clip_tray, null);
        RecyclerView recycler = root.findViewById(R.id.ime_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        adapter = new ClipAdapter(this, repo.getClips(), this, true);
        recycler.setAdapter(adapter);
        return root;
    }

    @Override
    public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        captureSystemClipboard();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void captureSystemClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null
                || cm.getPrimaryClip().getItemCount() == 0) {
            return;
        }
        CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (text != null && text.length() > 0) {
            repo.addText(text.toString(), this);
        }
    }

    @Override
    public void onItemClicked(int position) {
        List<Clip> clips = repo.getClips();
        if (position < 0 || position >= clips.size()) {
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(clips.get(position).getText(), 1);
        }
    }

    @Override
    public boolean onItemLongClicked(int position) {
        return false;
    }
}
