package com.example.taskmgr.ui.subs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.model.Subscription;
import com.example.taskmgr.ui.main.TaskAdapter;
import com.example.taskmgr.util.DateUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SubscriptionsAdapter extends RecyclerView.Adapter<SubscriptionsAdapter.VH> {

    private final List<Subscription> items = new ArrayList<>();
    private final OnSubClick listener;

    public SubscriptionsAdapter(OnSubClick listener) {
        this.listener = listener;
    }

    public void setItems(List<Subscription> newItems) {
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
            .inflate(R.layout.item_subscription, parent, false);
        VH vh = new VH(v);
        TaskAdapter.InstallRoundedClip(vh.itemView.findViewById(R.id.subs_sticker), parent);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Context ctx = h.itemView.getContext();
        Subscription s = items.get(position);

        h.name.setText(s.name);
        h.amount.setText(String.format(Locale.getDefault(), "%.2f ₽", s.amount));

        if (s.description != null && !s.description.isEmpty()) {
            h.desc.setText(s.description);
            h.desc.setVisibility(View.VISIBLE);
        } else {
            h.desc.setVisibility(View.GONE);
        }

        String date = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            .format(new Date(s.nextChargeAt));
        long today = DateUtils.startOfDay(System.currentTimeMillis());
        boolean overdue = s.isActive && s.nextChargeAt < today;

        // стикер: число + месяц
        SimpleDateFormat dayFmt = new SimpleDateFormat("d", Locale.getDefault());
        SimpleDateFormat monthFmt = new SimpleDateFormat("MMM", new Locale("ru"));
        h.day.setText(dayFmt.format(new Date(s.nextChargeAt)));
        h.month.setText(monthFmt.format(new Date(s.nextChargeAt)).replace(".", ""));

        if (!s.isActive) {
            h.sticker.setBackgroundResource(R.drawable.bg_subs_sticker);
            h.day.setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary));
            h.month.setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary));
            h.status.setText("Неактивна");
            h.status.setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary));
        } else if (overdue) {
            h.sticker.setBackgroundResource(R.drawable.bg_subs_sticker_overdue);
            h.day.setTextColor(ContextCompat.getColor(ctx, R.color.semantic_danger));
            h.month.setTextColor(ContextCompat.getColor(ctx, R.color.semantic_danger));
            h.status.setText("Просрочено · " + date);
            h.status.setTextColor(ContextCompat.getColor(ctx, R.color.semantic_danger));
        } else {
            long daysLeft = (s.nextChargeAt - today) / (24L * 60 * 60 * 1000);
            int bg = daysLeft <= 3 ? R.drawable.bg_subs_sticker_soon : R.drawable.bg_subs_sticker;
            h.sticker.setBackgroundResource(bg);
            int color = daysLeft <= 3
                ? ContextCompat.getColor(ctx, R.color.semantic_warning)
                : ContextCompat.getColor(ctx, R.color.neon_green);
            h.day.setTextColor(color);
            h.month.setTextColor(color);
            String tail = daysLeft == 0 ? "сегодня"
                : daysLeft == 1 ? "завтра"
                : "через " + daysLeft + " дн.";
            h.status.setText(date + " · " + tail);
            h.status.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(s);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface OnSubClick {
        void onClick(Subscription s);
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView name, amount, desc, status, day, month;
        LinearLayout sticker;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.subs_name);
            amount = v.findViewById(R.id.subs_amount);
            desc = v.findViewById(R.id.subs_desc);
            status = v.findViewById(R.id.subs_status);
            day = v.findViewById(R.id.subs_day);
            month = v.findViewById(R.id.subs_month);
            sticker = v.findViewById(R.id.subs_sticker);
        }
    }
}
