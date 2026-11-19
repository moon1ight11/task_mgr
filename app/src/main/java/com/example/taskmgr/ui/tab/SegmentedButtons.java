package com.example.taskmgr.ui.tab;

import android.content.Context;
import android.content.res.ColorStateList;

import androidx.core.content.ContextCompat;

import com.example.taskmgr.R;
import com.google.android.material.button.MaterialButton;

import java.util.List;

final class SegmentedButtons {

    private SegmentedButtons() {
    }

    static void apply(Context context, MaterialButton button, boolean checked) {
        int accent = ContextCompat.getColor(context, R.color.neon_green);
        int accentDim = ContextCompat.getColor(context, R.color.neon_green_dim);
        int inactiveStroke = ContextCompat.getColor(context, R.color.divider_strong);
        int inactiveText = ContextCompat.getColor(context, R.color.text_secondary);

        button.setChecked(checked);
        if (checked) {
            button.setStrokeColor(ColorStateList.valueOf(accent));
            button.setTextColor(accent);
            button.setBackgroundTintList(ColorStateList.valueOf(accentDim & 0x33FFFFFF));
        } else {
            button.setStrokeColor(ColorStateList.valueOf(inactiveStroke));
            button.setTextColor(inactiveText);
            button.setBackgroundTintList(ColorStateList.valueOf(0x00000000));
        }
    }

    static void applyGroup(Context context, List<MaterialButton> buttons, int selectedIndex) {
        for (int i = 0; i < buttons.size(); i++) {
            apply(context, buttons.get(i), i == selectedIndex);
        }
    }

    static void setSortIcon(MaterialButton button, boolean ascending) {
        int iconRes = ascending ? R.drawable.ic_sort_asc : R.drawable.ic_sort_desc;
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
        button.setCompoundDrawablePadding(2);
    }
}
