package com.easyclipboard.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.easyclipboard.app.R;
import com.easyclipboard.app.model.Clip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Expanded "show" view (the original big-view): displays the full clip text in a
 * scrollable dialog with a close (X) button that returns to the grid.
 */
public final class ShowClipDialog {

    private ShowClipDialog() {
    }

    public static void show(Context ctx, Clip clip) {
        if (clip == null) {
            return;
        }
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_show, null);
        TextView title = view.findViewById(R.id.show_title);
        TextView text = view.findViewById(R.id.show_text);
        ImageButton close = view.findViewById(R.id.show_close);

        if (clip.getTitle() != null && !clip.getTitle().isEmpty()) {
            title.setText(clip.getTitle());
        } else {
            title.setText(R.string.show_clip);
        }
        text.setText(clip.getText());

        final AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setView(view)
                .create();
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }
}
