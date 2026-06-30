package com.easyclipboard.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.easyclipboard.app.R;
import com.easyclipboard.app.model.Clip;

import java.util.ArrayList;
import java.util.List;

/**
 * Clip-card grid adapter. Ported from the original
 * com.dhm47.nativeclipboard.ClipAdapter: same item layout (R.layout.textview),
 * same card colouring rules and text binding, migrated android.support.v7 ->
 * androidx. The only behavioural change is that click callbacks go through an
 * explicit {@link OnItemListener} instead of casting the Context, so the adapter
 * works from Fragments, the IME and the PROCESS_TEXT activity.
 */
public class ClipAdapter extends RecyclerView.Adapter<ClipAdapter.ClipViewHolder> {

    public interface OnItemListener {
        void onItemClicked(int position);

        boolean onItemLongClicked(int position);
    }

    private static SharedPreferences setting;
    private final boolean keyboard;
    public List<Clip> mClips;
    private final Context mContext;
    private final OnItemListener listener;

    public ClipAdapter(Context ctx, List<Clip> mClips, OnItemListener listener) {
        this(ctx, mClips, listener, false);
    }

    public ClipAdapter(Context ctx, List<Clip> mClips, OnItemListener listener, boolean keyboard) {
        this.mContext = ctx;
        this.mClips = mClips != null ? mClips : new ArrayList<Clip>();
        this.listener = listener;
        this.keyboard = keyboard;
        setting = PreferenceManager.getDefaultSharedPreferences(ctx.getApplicationContext());
    }

    @Override
    public int getItemCount() {
        return this.mClips.size();
    }

    @NonNull
    @Override
    public ClipViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.textview, viewGroup, false);
        return new ClipViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final ClipViewHolder holder, int in) {
        int i = holder.getAdapterPosition();
        if (i < 0 || i >= mClips.size()) {
            return;
        }
        if (this.mClips.get(i).getTitle().equals("")) {
            holder.clipText.setText(this.mClips.get(i).getText());
            holder.clipText.setLines(3);
            holder.clipTitleText.setVisibility(View.GONE);
        } else {
            holder.clipText.setText(this.mClips.get(i).getText());
            holder.clipText.setLines(2);
            holder.clipTitleText.setText(this.mClips.get(i).getTitle());
            holder.clipTitleText.setVisibility(View.VISIBLE);
        }

        if (this.keyboard) {
            switch (setting.getString("keyboard_theme", "same")) {
                case "dark":
                    if (this.mClips.get(i).isPinned()) {
                        holder.cv.setCardBackgroundColor(-13550526);
                    } else {
                        holder.cv.setCardBackgroundColor(-12563632);
                    }
                    holder.clipTitleText.setTextColor(-1);
                    holder.clipText.setTextColor(-1);
                    break;
                case "light":
                    break;
                case "same":
                default:
                    if (this.mClips.get(i).isPinned()) {
                        holder.cv.setCardBackgroundColor(setting.getInt("pincolor", -3190016));
                    } else {
                        holder.cv.setCardBackgroundColor(setting.getInt("clpcolor", -17630));
                    }
                    holder.clipTitleText.setTextColor(-1);
                    holder.clipText.setTextColor(-1);
                    break;
            }
        } else {
            if (this.mClips.get(i).isPinned()) {
                holder.cv.setCardBackgroundColor(setting.getInt("pincolor", -3190016));
            } else {
                holder.cv.setCardBackgroundColor(setting.getInt("clpcolor", -17630));
            }
            holder.clipTitleText.setTextColor(setting.getInt("txtcolor", -10073330));
            holder.clipText.setTextColor(setting.getInt("txtcolor", -10073330));
        }

        holder.clipText.setTextSize(setting.getInt("txtsize", 20));
        holder.clipTitleText.setTextSize(1.2f * setting.getInt("txtsize", 20));

        holder.cv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onItemClicked(holder.getAdapterPosition());
                }
            }
        });
        holder.cv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return listener != null && listener.onItemLongClicked(holder.getAdapterPosition());
            }
        });
    }

    public void add(int position, Clip clip) {
        this.mClips.add(position, clip);
        notifyItemInserted(position);
    }

    public void remove(int position) {
        this.mClips.remove(position);
        notifyItemRemoved(position);
    }

    public static class ClipViewHolder extends RecyclerView.ViewHolder {
        TextView clipText;
        TextView clipTitleText;
        CardView cv;

        ClipViewHolder(View itemView) {
            super(itemView);
            this.cv = (CardView) itemView;
            this.clipText = this.cv.findViewById(R.id.textViewC);
            this.clipTitleText = this.cv.findViewById(R.id.textViewTitle);
        }
    }
}
