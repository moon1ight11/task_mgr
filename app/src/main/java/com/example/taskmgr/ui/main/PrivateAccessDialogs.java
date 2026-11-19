package com.example.taskmgr.ui.main;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.AppSettingsDao;
import com.example.taskmgr.util.DbExecutor;
import com.example.taskmgr.util.PasswordHasher;
import com.example.taskmgr.util.PinInputHelper;

final class PrivateAccessDialogs {

    interface OnSuccess {
        void run();
    }

    private PrivateAccessDialogs() {
    }

    static void showCreate(Activity activity, AppSettingsDao settingsDao, OnSuccess onSuccess) {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_set_password, null);

        EditText c1 = view.findViewById(R.id.pin_create_1);
        EditText c2 = view.findViewById(R.id.pin_create_2);
        EditText c3 = view.findViewById(R.id.pin_create_3);
        EditText c4 = view.findViewById(R.id.pin_create_4);
        EditText f1 = view.findViewById(R.id.pin_confirm_1);
        EditText f2 = view.findViewById(R.id.pin_confirm_2);
        EditText f3 = view.findViewById(R.id.pin_confirm_3);
        EditText f4 = view.findViewById(R.id.pin_confirm_4);

        PinInputHelper createHelper = new PinInputHelper(c1, c2, c3, c4);
        PinInputHelper confirmHelper = new PinInputHelper(f1, f2, f3, f4);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("PIN для приватного раздела")
            .setMessage("Придумайте PIN из 4 цифр. Восстановить его будет нельзя.")
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("OK", null)
            .create();

        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!createHelper.isComplete() || !confirmHelper.isComplete()) {
                    Toast.makeText(activity, "Введите все 4 цифры", Toast.LENGTH_SHORT).show();
                    return;
                }
                String pwd = createHelper.getValue();
                String confirm = confirmHelper.getValue();
                if (!pwd.matches("\\d{4}")) {
                    Toast.makeText(activity, "Только 4 цифры", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!pwd.equals(confirm)) {
                    Toast.makeText(activity, "PIN не совпадает", Toast.LENGTH_SHORT).show();
                    confirmHelper.clear();
                    return;
                }
                final String pwdFinal = pwd;
                DbExecutor.runVoid(() -> {
                    String salt = PasswordHasher.newSaltBase64();
                    int iter = PasswordHasher.DEFAULT_ITERATIONS;
                    String hash = PasswordHasher.hashBase64(pwdFinal, salt, iter);
                    settingsDao.put(AppSettingsDao.KEY_PRIVATE_PWD_SALT, salt);
                    settingsDao.put(AppSettingsDao.KEY_PRIVATE_PWD_ITER, String.valueOf(iter));
                    settingsDao.put(AppSettingsDao.KEY_PRIVATE_PWD_HASH, hash);
                }, () -> {
                    dialog.dismiss();
                    onSuccess.run();
                });
            })
        );

        dialog.show();
        createHelper.focusFirst();
    }

    static void showEnter(Activity activity, AppSettingsDao settingsDao, OnSuccess onSuccess) {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_enter_password, null);

        EditText p1 = view.findViewById(R.id.pin_1);
        EditText p2 = view.findViewById(R.id.pin_2);
        EditText p3 = view.findViewById(R.id.pin_3);
        EditText p4 = view.findViewById(R.id.pin_4);

        PinInputHelper helper = new PinInputHelper(p1, p2, p3, p4);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("Введите PIN")
            .setView(view)
            .setPositiveButton("OK", null)
            .setNegativeButton("Отмена", null)
            .create();

        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!helper.isComplete()) {
                    Toast.makeText(activity, "Введите все 4 цифры", Toast.LENGTH_SHORT).show();
                    return;
                }
                final String pwd = helper.getValue();
                DbExecutor.run(() -> {
                    String salt = settingsDao.get(AppSettingsDao.KEY_PRIVATE_PWD_SALT);
                    String hash = settingsDao.get(AppSettingsDao.KEY_PRIVATE_PWD_HASH);
                    String iterStr = settingsDao.get(AppSettingsDao.KEY_PRIVATE_PWD_ITER);
                    int iter = PasswordHasher.DEFAULT_ITERATIONS;
                    try {
                        if (iterStr != null) iter = Integer.parseInt(iterStr);
                    } catch (Exception ignored) {
                    }
                    return PasswordHasher.verify(pwd, salt, iter, hash);
                }, ok -> {
                    if (ok != null && ok) {
                        dialog.dismiss();
                        onSuccess.run();
                    } else {
                        Toast.makeText(activity, "Неверный PIN", Toast.LENGTH_SHORT).show();
                        helper.clear();
                    }
                });
            })
        );

        dialog.show();
        helper.focusFirst();
    }
}
