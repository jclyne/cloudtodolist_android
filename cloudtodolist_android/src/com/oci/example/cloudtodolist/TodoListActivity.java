package com.oci.example.cloudtodolist;

import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.*;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.*;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import com.oci.example.cloudtodolist.provider.TodoListSchema;

/**
 * Main activity for the TodoList application.
 * It provides a ListView to display the cloudtodolist items and handles context
 * menus on the items
 */
public class TodoListActivity extends FragmentActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    // Log Tag
    private static final String TAG = "TodoListActivity";

    // Reference to the system wide ConnectivityManager
    private static ConnectivityManager connManager;
    // Reference to the system wide SharedPreferences
    private static SharedPreferences prefs;

    // Loader ID of the Loader for the TodoList Cursor
    private static final int TODOLIST_CURSOR_LOADER = 1;

    // Dialog ID for the Notes Dialog
    private static final int NOTES_DIALOG = 1;

    // Reference to the new entry EditText view
    private EditText newEntryBox;
    // Reference to TodoListCursorAdapter
    private TodoListCursorAdapter todoListAdapter;
    // Reference to an intenal broadcast receiver to handle connectivity events
    private BroadcastReceiver broadcastReceiver;

    /**
     * Called when the activity is starting
     *
     * @param savedInstanceState bundle of saved instance state data
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Created" + " (" + Thread.currentThread().getName() + ")");

        verifyValidAccountType();

        // Initialize the ConnectivityManager reference
        connManager = (ConnectivityManager) getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        // Initialize the PreferenceManager reference
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        // Inflate the layout xml
        setContentView(R.layout.todolist_layout);

        // Set the window title
        setWindowTitle();

        // Hide the soft input keyboard initially
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        /**
         * This adapter is initially created with a null cursor. The  TODOLIST_CURSOR_LOADER we defined
         * will take care of swapping in loaded cursors in the LoaderCallbacks. This will take care of
         * making sure that the "requery" is not on the UI thread, and that it will not requery when the
         * the activity is paused.
         */
        todoListAdapter = new TodoListCursorAdapter(this, null);

        // Now associate the adapter with the list view
        final ListView todoListView = (ListView) findViewById(R.id.todo_list);
        todoListView.setAdapter(todoListAdapter);

        /**
         * Register the listview as having a context menu. This sets the long-clickable attribute
         * and will call this activity's onCreateContextMenu
         */
        registerForContextMenu(todoListView);

        /**
         * Register the TODOLIST_CURSOR_LOADER. The LoaderCallbacks will handle creation of
         * the CursorLoader as well as setting the proper cursor in the todoListAdapter,
         * based on the activity and data state.
         */
        getSupportLoaderManager().initLoader(TODOLIST_CURSOR_LOADER, null,
                new LoaderManager.LoaderCallbacks<Cursor>() {

                    @Override
                    public CursorLoader onCreateLoader(int id, Bundle bundle) {
                        // Create and return a CursorLoader that will take care of
                        // creating a Cursor for the data being displayed.
                        Log.d(TAG, "TodoList Cursor Loader Initialized");
                        return new CursorLoader(getBaseContext(),
                                TodoListSchema.Entries.CONTENT_URI,
                                null, null, null, null);
                    }

                    @Override
                    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
                        /**
                         * Swap the new cursor in.  (The framework will take care of closing the
                         * old cursor once we return.)
                         */
                        Log.d(TAG, "TodoList Cursor Loader Finished" + " (" + cursor.getCount() + ")");
                        todoListAdapter.swapCursor(cursor);
                    }

                    @Override
                    public void onLoaderReset(Loader<Cursor> cursorLoader) {
                        /**
                         * This is called when the last Cursor provided to onLoadFinished()
                         * above is about to be closed.  We need to make sure we are no
                         * longer using it.
                         */
                        Log.d(TAG, "TodoList Cursor Loader Reset");
                        todoListAdapter.swapCursor(null);
                    }
                });

        /**
         * Add a listener to the new entry to handle an enter key. This
         * will call the onNewEntryHandler and clear the textbox
         */
        newEntryBox = (EditText) findViewById(R.id.new_entry);
        newEntryBox.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    onNewEntry(newEntryBox.getText().toString());
                    newEntryBox.setText("");
                    return true;
                }
                return false;
            }
        });

        /**
         * Add a FocusChange listener that will simply clear the edit text box
         * when it loses focus
         */
        newEntryBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    newEntryBox.setText("");
                }
            }
        });

        // Register this class as an SharedPreferencesChange listener
        prefs.registerOnSharedPreferenceChangeListener(this);

        // Build a BroadcastReceiver that will update the window title based on connectivity events
        IntentFilter broadcastFilter = new IntentFilter();
        broadcastFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        broadcastFilter.addAction(ConnectivityManager.ACTION_BACKGROUND_DATA_SETTING_CHANGED);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setWindowTitle();
            }
        };
        registerReceiver(broadcastReceiver, broadcastFilter);

        // Finally, request an intial sync
        TodoListSyncHelper.requestSync(this);
    }

    /**
     * Called when the activity is resumed
     */
    @Override
    protected void onResume() {
        super.onResume();
    }

    /**
     * checks if a valid account of the required type "com.google" exists. If it doesn't, the user
     * is prompted to create a new account or log in to an existing account.
     */
    private void verifyValidAccountType() {
        final AccountManager accountManager = AccountManager.get(getApplicationContext());
        final String accountType = getString(R.string.setting_account_type);
        if (accountManager.getAccountsByType(accountType).length == 0) {
            accountManager.addAccount(getString(R.string.setting_account_type),
                    null, null, null, this, new AccountManagerCallback<Bundle>() {
                        @Override
                        public void run(AccountManagerFuture result) {
                            if (accountManager.getAccountsByType(accountType).length > 0) {
                                startActivity(new Intent(getBaseContext(), this.getClass()));
                            }
                        }
                    }, null);
            finish();
        }
    }

    /**
     * Called when the activity is being destroyed
     */
    @Override
    protected void onDestroy() {
        // Make sure to unregister the broadcast receiver
        unregisterReceiver(broadcastReceiver);
        super.onDestroy();
    }

    /**
     * Called when a shared preference is changed, added, or removed.
     *
     * @param prefs SharedPreferences that received the change.
     * @param key   key of the preference that was changed, added, or removed.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        // Update the window title if the offline_mode setting is changed
        if (key.equals(getString(R.string.setting_offline_mode))) {
            setWindowTitle();
        }
    }

    /**
     * Called to initialize the contents of the activity's standard options menu
     *
     * @param menu options menu to update
     * @return true for the menu to be displayed, false otherwise
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // Inflate the menu defined in the xml
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.todolist_menu, menu);
        return true;
    }

    /**
     * Called right before the menu is shown
     *
     * @param menu menu as last shown or first initialized by onCreateOptionsMenu().
     * @return true for the menu to be displayed, false otherwise
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // Disable the refresh menu option if in offline mode
        menu.getItem(0).setEnabled(!prefs.getBoolean(getString(R.string.setting_offline_mode), false));

        return true;
    }

    /**
     * Called when a context menu for the view is about to be shown
     *
     * @param menu     context menu that is being built
     * @param view     view for which the context menu is being built
     * @param menuInfo Extra information about the item for which the context menu should be shown.
     *                 This should be an AdapterView.AdapterContextMenuInfo because we registered context menus with the
     *                 TodoList ListView
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);

        // Inflate the menu from the xml
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.entry_menu, menu);

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        /**
         * Disable the NOTES menu option if there are not notes to display, so do a lookup
         * and check
         */
        Uri entryUri = ContentUris.withAppendedId(TodoListSchema.Entries.CONTENT_ID_URI_BASE, info.id);
        final String[] what = {TodoListSchema.Entries.NOTES};
        Cursor cursor = getContentResolver().query(entryUri, what, null, null, null);
        cursor.moveToFirst();
        String notes = cursor.getString(cursor.getColumnIndex(TodoListSchema.Entries.NOTES));

        menu.getItem(0).setEnabled((notes != null && !notes.isEmpty()));
    }

    /**
     * Called whenever an item in the options menu is selected
     *
     * @param item item selected
     * @return false to allow normal menu processing to proceed, true if it was handled
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                // request a sync and how a quitck toast indicating the refresh started
                Toast.makeText(this, R.string.sync_initiated, Toast.LENGTH_SHORT).show();
                TodoListSyncHelper.requestSync(this);
                return true;

            case R.id.menu_clear_selected:
                // call the onClearCompleted handler to delete completed entries
                onClearCompleted();
                return true;

            case R.id.menu_preferences:
                // issue an intent for the preferences activity
                Intent intent = new Intent();
                intent.setClass(this, TodoListPreferencesActivity.class);
                startActivity(intent);
                return true;
        }

        return false;
    }

    /**
     * Called whenever an item in the context menu is selected
     *
     * @param item item selected
     * @return false to allow normal menu processing to proceed, true if it was handled
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.menu_notes_item:
                // call the onShowNotes handler to display the item's notes
                onShowNotes(info.id);
                return true;

            case R.id.menu_edit_item:
                // call the onEditEntry handler to edit the specified entry
                onEditEntry(info.id);
                return true;

            case R.id.menu_delete_item:
                // call the onEditEntry handler to edit the specified entry
                onDeleteEntry(info.id);
                return true;
        }

        return false;
    }

    /**
     * Called as a result to showDialog
     *
     * @param id   id of the dialog to display
     * @param args extra args
     * @return the newly created dialog
     *         TODO: This is depracted, implement with DialogFragment
     */
    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
            case NOTES_DIALOG:
                return buildNotesDialog(args.getLong("entryId"));

            default:
                return super.onCreateDialog(id);
        }
    }

    /**
     * Handles a request to shows the Notes dialog
     *
     * @param entryId id of the entry to display
     */
    void onShowNotes(long entryId) {
        Bundle args = new Bundle();
        args.putLong("entryId", entryId);
        showDialog(NOTES_DIALOG, args);
    }

    /**
     * Handles a new entry request from the newEntry EditTextBox
     *
     * @param title title of the newly created entry
     */
    void onNewEntry(String title) {

        // Make sure the title isn't empty
        if (title.length() == 0)
            return;

        ContentValues values = new ContentValues();
        values.put(TodoListSchema.Entries.TITLE, title);
        try {
            // Add the entry and show a Toast indicating it succeeded
            getContentResolver().insert(TodoListSchema.Entries.CONTENT_URI, values);
            Toast.makeText(this, getString(R.string.entry_added), Toast.LENGTH_SHORT).show();

        } catch (IllegalArgumentException e) {
            Toast.makeText(this, getString(R.string.entry_invalid), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to add new entry: " + e.toString());
        }

    }

    /**
     * Handles a request to edit an entry
     *
     * @param entryId id of the entry to edit
     */
    void onEditEntry(long entryId) {

        // Build and issue an ACTION_EDIT intent for the URI of the entry
        startActivity(new Intent(
                Intent.ACTION_EDIT,
                ContentUris.withAppendedId(TodoListSchema.Entries.CONTENT_ID_URI_BASE, entryId)));
    }

    /**
     * Handles a request to delete an entry
     *
     * @param entryId id of the entry to delete
     */
    void onDeleteEntry(long entryId) {
        // Build the entry URI and delete it
        final Uri entryUri =
                ContentUris.withAppendedId(
                        TodoListSchema.Entries.CONTENT_ID_URI_BASE, entryId);
        int rowsDeleted = getContentResolver().delete(entryUri, null, null);

        // Show a toast if an entry was deleted
        if (rowsDeleted > 0)
            Toast.makeText(this, getString(R.string.entry_deleted), Toast.LENGTH_SHORT).show();
    }

    /**
     * Handles a clear complete reaquest
     */
    void onClearCompleted() {
        // Build a where clause to filter for completed entries
        final String where = TodoListSchema.Entries.COMPLETE + " = ?";
        final String[] whereArgs = {Integer.toString(1)};
        // Issue the delete
        int rowsDeleted = getContentResolver().delete(
                TodoListSchema.Entries.CONTENT_URI, where, whereArgs);

        // Show a toast with the number of entires deleted
        String msg = getResources().getQuantityString(R.plurals.clearedEntriesDeleted, rowsDeleted, rowsDeleted);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * Sets the window title based on the state of the background data connection
     * or manual offline_mode
     */
    private void setWindowTitle() {

        String title = getString(R.string.app_name);
        NetworkInfo netInfo = connManager.getActiveNetworkInfo();
        if (prefs.getBoolean(getString(R.string.setting_offline_mode), false)) {
            title += " - Offline Mode";
        } else if (!connManager.getBackgroundDataSetting()) {
            title += " - Background data disabled";
        } else if (netInfo == null || !netInfo.isConnected()) {
            title += " - Network Unavailable";
        } else {
            title += " - Online";
        }

        setTitle(title);
    }

    /**
     * Builds a notes dialog
     *
     * @param entryId id of the entry to display
     * @return the newly create dialog
     */
    private Dialog buildNotesDialog(long entryId) {
        // Build the URI for the specified entry
        Uri entryUri = ContentUris.withAppendedId(TodoListSchema.Entries.CONTENT_ID_URI_BASE, entryId);

        // Build a what clause to get the entiry's title and notes
        final String[] what = {TodoListSchema.Entries.NOTES, TodoListSchema.Entries.TITLE};

        // Query for the values
        Cursor cursor = getContentResolver().query(entryUri, what, null, null, null);
        cursor.moveToFirst();

        // Get the values from the cursor
        String title = cursor.getString(cursor.getColumnIndex(TodoListSchema.Entries.TITLE));
        String notes = cursor.getString(cursor.getColumnIndex(TodoListSchema.Entries.NOTES));
        if (notes == null || notes.isEmpty()) {
            notes = getString(R.string.empty_notes);
        }

        // Bind the values with the alert dialog with a "Done" button
        AlertDialog notesDialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(notes)
                .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                })
                .create();

        // Create a handler for the "Done" button
        notesDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface dialogInterface) {
                removeDialog(NOTES_DIALOG);
            }
        });

        return notesDialog;
    }
}
