package com.example.taskmgr.ui.tab;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.model.ReferenceEntry;

import java.util.ArrayList;
import java.util.List;

public class ReferenceAdapter extends RecyclerView.Adapter<ReferenceAdapter.VH> {

    private final List<ReferenceEntry> items = new ArrayList<>();
    private final OnReferenceClick clickListener;
    public ReferenceAdapter(OnReferenceClick clickListener) {
        this.clickListener = clickListener;
    }

    public void setItems(List<ReferenceEntry> newItems) {
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
            .inflate(R.layout.item_reference, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ReferenceEntry r = items.get(position);
        h.title.setText(r.title);
        h.value.setText(r.value);

        if (r.description != null && !r.description.isEmpty()) {
            h.description.setText(r.description);
            h.description.setVisibility(View.VISIBLE);
        } else {
            h.description.setVisibility(View.GONE);
        }

        if (r.category != null && !r.category.isEmpty()) {
            h.category.setText(r.category);
            h.category.setVisibility(View.VISIBLE);
        } else {
            h.category.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(r);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface OnReferenceClick {
        void onClick(ReferenceEntry item);
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView title, value, category, description;

        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.ref_title);
            value = v.findViewById(R.id.ref_value);
            category = v.findViewById(R.id.ref_category);
            description = v.findViewById(R.id.ref_description);
        }
    }
}
