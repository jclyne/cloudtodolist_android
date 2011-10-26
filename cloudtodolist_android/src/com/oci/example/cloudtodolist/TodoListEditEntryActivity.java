package com.oci.example.cloudtodolist;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import com.oci.example.cloudtodolist.provider.TodoListSchema;

/**
 * Activity that provides an interface to edit a TodoList entry
 */
public class TodoListEditEntryActivity extends Activity {

    // Reference to the EditText view for the entry title
    private EditText titleEditText;
    // Reference to the multi-line EditText view for the entry notes
    private EditText notesEditText;

    // Current value of the entry title
    private String currentTitle;
    // Current value of the entry notes
    private String currentNotes;

    // Uri, in the content provider, of the entry being edited
    private Uri entryUri;

    /**
     * Called when the activity is starting. Inflates the activiy UI
     * from the edit_entry_layout xml, binds the view with the data
     * specified in the intent.
     *
     * @param savedInstanceState saved instance state
     */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_entry_layout);

        titleEditText = (EditText) findViewById(R.id.edit_title);
        notesEditText = (EditText) findViewById(R.id.edit_notes);

        // Get the entry uri from the intent
        entryUri = getIntent().getData();

        // Get the current title and notes strings to pre-populate
        final String[] what = {TodoListSchema.Entries.TITLE,
                TodoListSchema.Entries.NOTES};

        Cursor cursor = getContentResolver().query(entryUri, what, null, null, null);
        final int titleIndex = cursor.getColumnIndex(TodoListSchema.Entries.TITLE);
        final int notesIndex = cursor.getColumnIndex(TodoListSchema.Entries.NOTES);
        cursor.moveToFirst();

        currentTitle = cursor.getString(titleIndex);
        currentNotes = cursor.getString(notesIndex);

        // Restore any saved state
        if (savedInstanceState != null) {
            titleEditText.setText(savedInstanceState.getString("title"));
            notesEditText.setText(savedInstanceState.getString("notes"));
        } else {
            titleEditText.setText(currentTitle);
            notesEditText.setText(currentNotes);
        }
    }

    /**
     * Called to retrieve per-instance state from an activity before being killed so
     * that the state can be restored in onCreate
     *
     * @param outState bundle in which to place saved state.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("title", titleEditText.getText().toString());
        outState.putString("notes", notesEditText.getText().toString());
    }

    /**
     * Handler for the edit_entry_apply button.
     * This will update the entry with the values that have changed
     * in the activity
     *
     * @param view button view that was clicked
     */
    @SuppressWarnings({"UnusedDeclaration", "UnusedParameters"})
    public void apply(View view) {
        ContentValues values = new ContentValues();

        String newTitle = titleEditText.getText().toString();
        if (!newTitle.equals(currentTitle))
            values.put(TodoListSchema.Entries.TITLE, newTitle);

        String newNotes = notesEditText.getText().toString();
        if (!newNotes.equals(currentNotes))
            values.put(TodoListSchema.Entries.NOTES, newNotes);

        if (values.size() > 0) {
            getContentResolver().update(entryUri, values, null, null);
            Toast.makeText(this, getString(R.string.entry_updated), Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    /**
     * Handler for the edit_entry_cancel button.
     * This just ends the activity and leaves the entry alone.
     *
     * @param view button view that was clicked
     */
    @SuppressWarnings({"UnusedDeclaration", "UnusedParameters"})
    public void cancel(View view) {
        finish();
    }
}

