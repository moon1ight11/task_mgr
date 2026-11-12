package com.example.taskmgr.ui.notes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.model.QuickNote;
import com.example.taskmgr.ui.main.TaskAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.VH> {

    private final List<QuickNote> items = new ArrayList<>();
    private final OnNoteClick click;
    private final OnNoteLongClick longClick;

    public NotesAdapter(OnNoteClick click, OnNoteLongClick longClick) {
        this.click = click;
        this.longClick = longClick;
    }

    public void setItems(List<QuickNote> newItems) {
        int oldSize = items.size();
        items.clear();
        if (newItems != null) items.addAll(newItems);
        int newSize = items.size();
        if (oldSize == newSize) {
            if (newSize > 0) notifyItemRangeChanged(0, newSize);
        } else {
            if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
            if (newSize > 0) notifyItemRangeInserted(0, newSize);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_note, parent, false);
        VH vh = new VH(v);
        TaskAdapter.InstallRoundedClip(vh.body, parent);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        QuickNote n = items.get(position);
        h.body.setText(n.body);
        h.date.setText(fmt(n.createdAt));

        h.itemView.setOnClickListener(v -> {
            if (click != null) click.onClick(n);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (longClick != null) longClick.onLongClick(n);
            return true;
        });
    }

    private String fmt(long millis) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(new Date(millis));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface OnNoteClick {
        void onClick(QuickNote note);
    }

    public interface OnNoteLongClick {
        void onLongClick(QuickNote note);
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView body, date;

        VH(@NonNull View v) {
            super(v);
            body = v.findViewById(R.id.note_body);
            date = v.findViewById(R.id.note_date);
        }
    }
}
