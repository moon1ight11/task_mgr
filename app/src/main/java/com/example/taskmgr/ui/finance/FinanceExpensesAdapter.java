package com.example.taskmgr.ui.finance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.model.FinanceExpense;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FinanceExpensesAdapter extends RecyclerView.Adapter<FinanceExpensesAdapter.VH> {

    private final List<FinanceExpense> items = new ArrayList<>();
    private final OnExpenseClick click;

    public FinanceExpensesAdapter(OnExpenseClick click) {
        this.click = click;
    }

    public void setItems(List<FinanceExpense> newItems) {
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
            .inflate(R.layout.item_finance_expense, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FinanceExpense e = items.get(position);
        h.category.setText(e.category);
        if (e.title != null && !e.title.isEmpty()) {
            h.title.setText(e.title);
            h.title.setVisibility(View.VISIBLE);
        } else h.title.setVisibility(View.GONE);
        h.amount.setText(String.format(Locale.getDefault(), "%.2f ₽", e.amount));
        h.itemView.setOnClickListener(v -> { if (click != null) click.onClick(e); });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface OnExpenseClick {
        void onClick(FinanceExpense e);
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView category, title, amount;
        VH(@NonNull View v) {
            super(v);
            category = v.findViewById(R.id.fin_exp_category);
            title = v.findViewById(R.id.fin_exp_title);
            amount = v.findViewById(R.id.fin_exp_amount);
        }
    }
}
