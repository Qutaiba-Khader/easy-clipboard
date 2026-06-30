package com.easyclipboard.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.easyclipboard.app.data.ClipRepository;
import com.easyclipboard.app.model.Clip;
import com.easyclipboard.app.ui.ClipAdapter;
import com.easyclipboard.app.ui.ShowClipDialog;

import java.util.List;

/**
 * Modern capture / paste entry point. Registered for ACTION_PROCESS_TEXT, so it
 * appears in the text-selection toolbar of any app. It saves the selected text
 * into history and shows the clip GRID; single-tap a clip to return it as the
 * processed text (paste over the selection) unless read-only; double-tap to view
 * the full text; the close (X) button dismisses without pasting.
 */
public class ProcessTextActivity extends AppCompatActivity implements ClipAdapter.OnItemListener {

    private ClipRepository repo;
    private boolean readOnly;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_process_text);
        setTitle(R.string.app_name);

        repo = ClipRepository.get(this);

        Intent intent = getIntent();
        CharSequence selected = intent != null
                ? intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT) : null;
        readOnly = intent != null
                && intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false);

        if (selected != null && selected.length() > 0) {
            repo.addText(selected.toString(), this);
        }

        ImageButton close = findViewById(R.id.popup_close);
        close.setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new GridLayoutManager(this, spanCount()));
        recycler.setAdapter(new ClipAdapter(this, repo.getClips(), this));
    }

    private int spanCount() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int dpWidth = (int) (dm.widthPixels / dm.density);
        return Math.max(2, dpWidth / 160);
    }

    @Override
    public void onItemClicked(int position) {
        List<Clip> clips = repo.getClips();
        if (position < 0 || position >= clips.size()) {
            return;
        }
        String chosen = clips.get(position).getText();
        if (!readOnly) {
            Intent result = new Intent();
            result.putExtra(Intent.EXTRA_PROCESS_TEXT, chosen);
            setResult(RESULT_OK, result);
        }
        finish();
    }

    @Override
    public void onItemDoubleTap(int position) {
        List<Clip> clips = repo.getClips();
        if (position < 0 || position >= clips.size()) {
            return;
        }
        ShowClipDialog.show(this, clips.get(position));
    }

    @Override
    public boolean onItemLongClicked(int position) {
        return false;
    }
}
