// com/example/taskmgr/ui/notes/NoteEditActivity.java
package com.example.taskmgr.ui.notes;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.QuickNoteDao;
import com.example.taskmgr.model.QuickNote;
import com.example.taskmgr.util.DbExecutor;

public class NoteEditActivity extends AppCompatActivity {

    public static final String EXTRA_NOTE_ID = "note_id";

    private QuickNoteDao dao;
    private EditText edit;
    private long noteId = -1;
    private long createdAt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_edit);

        dao = new QuickNoteDao(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Заметка");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> saveAndFinish());

        edit = findViewById(R.id.note_edit);
        noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, -1);

        if (noteId > 0) {
            DbExecutor.run(() -> dao.getById(noteId), note -> {
                if (note == null) return;
                createdAt = note.createdAt;
                edit.setText(note.body);
                edit.setSelection(edit.getText().length());
            });
        }

        edit.requestFocus();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (noteId > 0) {
            menu.add(0, 1, 0, "Удалить")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            confirmDelete();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDelete() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Удалить заметку?")
            .setPositiveButton("Удалить", (d, w) -> {
                DbExecutor.runVoid(() -> dao.delete(noteId), this::finish);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void saveAndFinish() {
        String text = edit.getText().toString().trim();
        if (noteId > 0) {
            if (text.isEmpty()) {
                // пустая — удаляем
                DbExecutor.runVoid(() -> dao.delete(noteId), this::finish);
            } else {
                DbExecutor.runVoid(() -> dao.update(noteId, text), this::finish);
            }
        } else {
            if (!text.isEmpty()) {
                DbExecutor.runVoid(() -> dao.insert(text), this::finish);
            } else {
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        saveAndFinish();
    }
}
