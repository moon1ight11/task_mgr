// com/example/taskmgr/ui/utilities/UtilitiesReadingAdapter.java
package com.example.taskmgr.ui.utilities;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.model.UtilityMeter;
import com.example.taskmgr.model.UtilityReading;
import com.example.taskmgr.ui.main.TaskAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UtilitiesReadingAdapter extends RecyclerView.Adapter<UtilitiesReadingAdapter.VH> {

    private final List<UtilityMeter> meters = new ArrayList<>();
    private Map<Long, UtilityReading> readings;
    private final OnMeterClick click;

    public UtilitiesReadingAdapter(Context ignored, OnMeterClick click) {
        this.click = click;
    }

    public void setItems(List<UtilityMeter> newMeters, Map<Long, UtilityReading> newReadings) {
        int oldSize = meters.size();
        meters.clear();
        if (newMeters != null) meters.addAll(newMeters);
        readings = newReadings;
        int newSize = meters.size();
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
            .inflate(R.layout.item_utility_reading, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Context ctx = h.itemView.getContext();
        UtilityMeter m = meters.get(position);
        UtilityReading r = readings != null ? readings.get(m.id) : null;

        h.name.setText(m.name);

        if (r != null) {
            h.amount.setText(String.format(Locale.getDefault(), "%.2f ₽", r.amount));
            h.formula.setText(String.format(Locale.getDefault(),
                "%s → %s %s × %.2f = %.2f ₽",
                formatNum(r.previousValue), formatNum(r.currentValue), m.unit,
                r.rateApplied, r.amount));
            h.stripe.setBackgroundColor(ContextCompat.getColor(ctx, R.color.neon_green));
        } else {
            h.amount.setText("—");
            h.formula.setText("не заполнено · тариф " +
                String.format(Locale.getDefault(), "%.2f ₽/%s", m.rate, m.unit));
            h.stripe.setBackgroundColor(ContextCompat.getColor(ctx, R.color.divider_strong));
        }

        h.itemView.setOnClickListener(v -> {
            if (click != null) click.onClick(m);
        });
    }

    @Override
    public int getItemCount() {
        return meters.size();
    }

    private static String formatNum(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.format(Locale.getDefault(), "%.2f", v);
    }

    public interface OnMeterClick {
        void onClick(UtilityMeter meter);
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView name, amount, formula;
        View stripe;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.reading_name);
            amount = v.findViewById(R.id.reading_amount);
            formula = v.findViewById(R.id.reading_formula);
            stripe = v.findViewById(R.id.reading_stripe);
        }
    }
}
