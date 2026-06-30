package com.easyclipboard.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.easyclipboard.app.R;
import com.easyclipboard.app.data.ClipRepository;
import com.easyclipboard.app.model.Clip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

/**
 * The headline page: a RecyclerView grid of clip cards using the ported
 * {@link ClipAdapter}, the original clip-card item layout, drawables and
 * colours. Tap = copy, long-press = pin/edit/delete, swipe = delete, FAB = add.
 */
public class ClipboardFragment extends Fragment implements ClipAdapter.OnItemListener {

    private ClipRepository repo;
    private ClipAdapter adapter;
    private RecyclerView recycler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_clipboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        repo = ClipRepository.get(ctx);

        recycler = view.findViewById(R.id.recycler);
        int columns = spanCount(ctx);
        recycler.setLayoutManager(new GridLayoutManager(ctx, columns));
        adapter = new ClipAdapter(ctx, repo.getClips(), this);
        recycler.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                repo.remove(pos, requireContext());
                adapter.notifyItemRemoved(pos);
            }
        }).attachToRecyclerView(recycler);

        FloatingActionButton fab = view.findViewById(R.id.fab_add);
        fab.setOnClickListener(v -> showAddDialog());
    }

    private int spanCount(Context ctx) {
        int columns = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getInt("columncount", 0);
        return columns <= 0 ? 2 : columns;
    }

    private void refresh() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onItemClicked(int position) {
        List<Clip> clips = repo.getClips();
        if (position < 0 || position >= clips.size()) {
            return;
        }
        copyToSystem(clips.get(position).getText());
        Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onItemLongClicked(int position) {
        List<Clip> clips = repo.getClips();
        if (position < 0 || position >= clips.size()) {
            return false;
        }
        final Clip clip = clips.get(position);
        CharSequence[] options = {
                getString(clip.isPinned() ? R.string.unpin : R.string.pin),
                getString(R.string.edit),
                getString(R.string.delete)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clip_actions)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            repo.setPinned(position, !clip.isPinned(), requireContext());
                            refresh();
                            break;
                        case 1:
                            showEditDialog(position, clip);
                            break;
                        case 2:
                            repo.remove(position, requireContext());
                            refresh();
                            break;
                        default:
                            break;
                    }
                })
                .show();
        return true;
    }

    private void showAddDialog() {
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.add_clip)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String text = input.getText().toString();
                    if (!text.isEmpty()) {
                        repo.addText(text, requireContext());
                        refresh();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEditDialog(final int position, final Clip clip) {
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(clip.getText());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.edit)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    repo.updateText(position, input.getText().toString(), clip.getTitle(),
                            requireContext());
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void copyToSystem(String text) {
        ClipboardManager cm = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Easy Clipboard", text));
        }
    }
}
