package com.easyclipboard.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.easyclipboard.app.data.ClipRepository;
import com.easyclipboard.app.model.Clip;
import com.easyclipboard.app.ui.ClipAdapter;

import java.util.List;

/**
 * Modern capture / paste entry point. Registered for ACTION_PROCESS_TEXT, so it
 * appears in the text-selection toolbar of any app. It saves the selected text
 * into history and shows the clip grid; picking a clip returns it as the
 * processed text (pasting it over the selection) unless the caller marked the
 * text read-only.
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

        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        recycler.setAdapter(new ClipAdapter(this, repo.getClips(), this));
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
    public boolean onItemLongClicked(int position) {
        return false;
    }
}
