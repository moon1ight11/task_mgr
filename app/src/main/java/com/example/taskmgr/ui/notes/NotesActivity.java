package com.example.taskmgr.ui.notes;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskmgr.R;
import com.example.taskmgr.data.dao.QuickNoteDao;
import com.example.taskmgr.model.QuickNote;
import com.example.taskmgr.util.DbExecutor;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Collections;
import java.util.List;

public class NotesActivity extends AppCompatActivity {

    private QuickNoteDao dao;
    private NotesAdapter adapter;
    private RecyclerView recycler;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        dao = new QuickNoteDao(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Быстрые заметки");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recycler = findViewById(R.id.notes_recycler);
        emptyView = findViewById(R.id.notes_empty);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotesAdapter(this::openEdit, this::confirmDelete);
        recycler.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab_add_note);
        fab.setOnClickListener(v -> openEdit(null));

        loadNotes();
    }

    private void openEdit(QuickNote note) {
        Intent i = new Intent(this, NoteEditActivity.class);
        if (note != null) i.putExtra(NoteEditActivity.EXTRA_NOTE_ID, note.id);
        startActivity(i);
    }

    private void confirmDelete(QuickNote note) {
        new AlertDialog.Builder(this)
            .setTitle("Удалить заметку?")
            .setPositiveButton("Удалить", (d, w) ->
                DbExecutor.runVoid(() -> dao.delete(note.id), this::loadNotes))
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void loadNotes() {
        DbExecutor.run(dao::getAll, notes -> {
            if (notes == null) notes = Collections.emptyList();
            adapter.setItems(notes);
            boolean empty = notes.isEmpty();
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotes();
    }
}
