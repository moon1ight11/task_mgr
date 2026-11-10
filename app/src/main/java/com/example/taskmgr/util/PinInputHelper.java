package com.example.taskmgr.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

public final class PinInputHelper {

    private final EditText[] boxes;

    public PinInputHelper(EditText... boxes) {
        this.boxes = boxes;
        for (int i = 0; i < boxes.length; i++) {
            final int index = i;
            boxes[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() == 1 && index < boxes.length - 1) {
                        boxes[index + 1].requestFocus();
                    } else if (s.length() == 0 && index > 0) {
                        boxes[index - 1].requestFocus();
                    }
                }
            });
        }
    }

    public String getValue() {
        StringBuilder sb = new StringBuilder(boxes.length);
        for (EditText b : boxes) {
            sb.append(b.getText().toString());
        }
        return sb.toString();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isComplete() {
        for (EditText b : boxes) {
            if (b.getText().length() != 1) return false;
        }
        return true;
    }

    public void clear() {
        for (EditText b : boxes) {
            b.setText("");
        }
        boxes[0].requestFocus();
    }

    public void focusFirst() {
        boxes[0].requestFocus();
    }
}
