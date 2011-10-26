package com.oci.example.cloudtodolist.provider;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.RemoteException;
import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;


/**
 * Tests for TodoListProvider content provider
 */

public class TodoListProviderTest extends ProviderTestCase2<TodoListProvider> {

    private static class EntryData {
        final int id;
        final String title;
        final String notes;
        final int complete;
        private long createTime;
        private long modifyTime;


        public void setModifyTime(long modifyTime) {
            this.modifyTime = modifyTime;
        }

        public void setCreateTime(long createTime) {
            this.createTime = createTime;
        }

        public EntryData(int id, String title, String notes, boolean complete) {
            this.id=id;
            this.title = title;
            this.notes = notes;
            this.complete = complete ? 1 : 0;
        }

        public ContentValues toContentValues() {

            ContentValues values = new ContentValues();
            values.put(TodoListSchema.Entries.ID, id);
            values.put(TodoListSchema.Entries.TITLE, title);
            values.put(TodoListSchema.Entries.NOTES, notes);
            values.put(TodoListSchema.Entries.COMPLETE, complete);
            values.put(TodoListSchema.Entries.CREATED, createTime);
            values.put(TodoListSchema.Entries.MODIFIED, modifyTime);

            return values;
        }
    }

    private static final EntryData[] TEST_ENTRIES = new EntryData[10];

    static {
        for (int i = 0; i< TEST_ENTRIES.length; i++){
            TEST_ENTRIES[i] = new EntryData(i,"Entry"+i, "This is entry "+i, false);
        }
    }


    private MockContentResolver mockResolver;
    private SQLiteDatabase db;
    String tableName;

    // Create a TEST Start Date and some standard time definitions
    private static final long ONE_DAY_MILLIS = 1000 * 60 * 60 * 24;
    private static final long ONE_WEEK_MILLIS = ONE_DAY_MILLIS * 7;
    private static final GregorianCalendar TEST_CALENDAR
            = new GregorianCalendar(2010, Calendar.JANUARY, 1, 0, 0, 0);
    private final static long START_DATE = TEST_CALENDAR.getTimeInMillis();


    public TodoListProviderTest() {
        super(TodoListProvider.class, TodoListSchema.AUTHORITY);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mockResolver = getMockContentResolver();
        db = getProvider().getWritableDatabase();
        tableName = TodoListSchema.Entries.TABLE_NAME;

    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private void insertData() {
        for (int index = 0; index < TEST_ENTRIES.length; index++) {
            TEST_ENTRIES[index].setCreateTime(START_DATE + (index * ONE_DAY_MILLIS));
            TEST_ENTRIES[index].setModifyTime(START_DATE + (index * ONE_WEEK_MILLIS));
            db.insertOrThrow(tableName, null, TEST_ENTRIES[index].toContentValues());
        }
    }


    public void testGetType() {
        // Tests the MIME type for the notes table URI.
        String mimeType = mockResolver.getType(TodoListSchema.Entries.CONTENT_URI);
        assertEquals(TodoListSchema.Entries.CONTENT_TYPE, mimeType);

        // Gets the note ID URI MIME type.
        Uri entryIdUri = ContentUris.withAppendedId(TodoListSchema.Entries.CONTENT_ID_URI_BASE, 1);
        mimeType = mockResolver.getType(entryIdUri);
        assertEquals(TodoListSchema.Entries.CONTENT_ITEM_TYPE, mimeType);

        // Tests an invalid URI. This should throw an IllegalArgumentException.
        Uri invalidUri = Uri.withAppendedPath(TodoListSchema.Entries.CONTENT_URI, "dummy");
        mockResolver.getType(invalidUri);
    }


    public void testQueryEntriesEmpty() {

        Cursor cursor = mockResolver.query(
                TodoListSchema.Entries.CONTENT_URI,  // the URI for the main data table
                null,                       // no projection, get all columns
                null,                       // no selection criteria, get all records
                null,                       // no selection arguments
                null                        // use default sort order
        );

        assertEquals(0, cursor.getCount());
    }

    public void testQueryEntries() {

        insertData();

        Cursor cursor = mockResolver.query(
                TodoListSchema.Entries.CONTENT_URI,  // the URI for the main data table
                null,                       // no projection, get all columns
                null,                       // no selection criteria, get all records
                null,                       // no selection arguments
                null                        // use default sort order
        );

        assertEquals(TEST_ENTRIES.length, cursor.getCount());

    }

    public void testQueryEntriesWithProjection() {

        final String[] what = {
                TodoListSchema.Entries.TITLE,
                TodoListSchema.Entries.NOTES,
                TodoListSchema.Entries.COMPLETE
        };

        insertData();

        Cursor cursor = mockResolver.query(
                TodoListSchema.Entries.CONTENT_URI,  // the URI for the main data table
                what,            // get the title, note, and mod date columns
                null,                       // no selection columns, get all the records
                null,                       // no selection criteria
                null                        // use default the sort order
        );

        assertEquals(what.length, cursor.getColumnCount());
        assertEquals(what[0], cursor.getColumnName(0));
        assertEquals(what[1], cursor.getColumnName(1));
        assertEquals(what[2], cursor.getColumnName(2));
    }

    public void testQueryEntriesWithProjectionAndSelectionColumns() {

        final String[] what = {
                TodoListSchema.Entries.TITLE,
                TodoListSchema.Entries.NOTES,
                TodoListSchema.Entries.COMPLETE
        };

        final String TITLE_SELECTION = TodoListSchema.Entries.TITLE + " = " + "?";

        String where = TITLE_SELECTION;
        final String[] whereArgs = {"Entry0", "Entry1", "Entry5"};
        for (int i = 1; i < whereArgs.length; i++)
            where += " OR " + TITLE_SELECTION;

        final String sort = TodoListSchema.Entries.TITLE + " ASC";

        insertData();

        Cursor cursor = mockResolver.query(
                TodoListSchema.Entries.CONTENT_URI, // the URI for the main data table
                what,           // get the title, note, and mod date columns
                where,         // select on the title column
                whereArgs,            // select titles "Entry0", "Entry1", or "Entry5"
                sort                 // sort ascending on the title column
        );

        assertEquals(whereArgs.length, cursor.getCount());

        int index = 0;

        while (cursor.moveToNext())
            assertEquals(whereArgs[index++], cursor.getString(0));


        assertEquals(whereArgs.length, index);
    }

    public void testQueryEntryEmpty() {
        Uri noteIdUri = ContentUris.withAppendedId(TodoListSchema.Entries.CONTENT_ID_URI_BASE, 1);

        Cursor cursor = mockResolver.query(
                noteIdUri, // URI pointing to a single record
                null,      // no projection, get all the columns for each record
                null,      // no selection criteria, get all the records in the table
                null,      // no need for selection arguments
                null       // default sort, by ascending title
        );

        // Asserts that the cursor is null.
        assertEquals(0, cursor.getCount());
    }

    public void testQueryEntry() {
        final String where = TodoListSchema.Entries.TITLE + " = " + "?";
        final String[] whereArgs = {"Entry1"};
        final String sort = TodoListSchema.Entries.TITLE + " ASC";
        final String[] what = {TodoListSchema.Entries._ID};

        insertData();

        // Queries the table using the URI for the full table.
        Cursor cursor = mockResolver.query(
                TodoListSchema.Entries.CONTENT_URI, // the base URI for the table
                what,        // returns the ID and title columns of rows
                where,         // select based on the title column
                whereArgs,            // select title of "Entry1"
                sort                 // sort order returned is by title, ascending
        );

        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToFirst());

        int entryId = cursor.getInt(0);
        Uri entryIdUri = ContentUris.withAppendedId(TodoListSchema.Entries.CONTENT_ID_URI_BASE, entryId);


        cursor = mockResolver.query(entryIdUri, // the URI for a single note
                what,                 // same projection, get ID and title columns
                where,                  // same selection, based on title column
                whereArgs,                     // same selection arguments, title = "Entry1"
                sort                          // same sort order returned, by title, ascending
        );

        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToFirst());
        assertEquals(entryId, cursor.getInt(0));
    }

    public void testInsert() {
        EntryData entry = new EntryData(30,"Entry30", "This is Entry30", false);
        entry.setCreateTime(START_DATE + (10 * ONE_DAY_MILLIS));
        entry.setModifyTime(START_DATE + (2 * ONE_WEEK_MILLIS));

        ContentValues values = entry.toContentValues();

        mockResolver.insert(TodoListSchema.Entries.CONTENT_URI, values);

        Cursor cursor = mockResolver.query(
                TodoListSchema.Entries.CONTENT_URI, // the main table URI
                null,                      // no projection, return all the columns
                null,                      // no selection criteria, return all the rows in the model
                null,                      // no selection arguments
                null                       // default sort order
        );

        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToFirst());

        int titleIndex = cursor.getColumnIndex(TodoListSchema.Entries.TITLE);
        int notesIndex = cursor.getColumnIndex(TodoListSchema.Entries.NOTES);
        int completeIndex = cursor.getColumnIndex(TodoListSchema.Entries.COMPLETE);
        int createTimeIndex = cursor.getColumnIndex(TodoListSchema.Entries.CREATED);
        int modifyTimeIndex = cursor.getColumnIndex(TodoListSchema.Entries.MODIFIED);

        assertEquals(entry.title, cursor.getString(titleIndex));
        assertEquals(entry.notes, cursor.getString(notesIndex));
        assertEquals(entry.complete, cursor.getInt(completeIndex) );
        assertEquals(entry.createTime, cursor.getLong(createTimeIndex));
        assertEquals(entry.modifyTime, cursor.getLong(modifyTimeIndex));
    }

    public void testInsertExisting() {
        EntryData entry = new EntryData(30,"Entry30", "This is Entry30", false);
        entry.setCreateTime(START_DATE + (10 * ONE_DAY_MILLIS));
        entry.setModifyTime(START_DATE + (2 * ONE_WEEK_MILLIS));

        ContentValues values = entry.toContentValues();

        Uri entryUri = mockResolver.insert(TodoListSchema.Entries.CONTENT_URI, values);
        values.put(TodoListSchema.Entries._ID, (int) ContentUris.parseId(entryUri));

        // Tries to insert this record into the table. This should fail and drop into the
        // catch block. If it succeeds, issue a failure message.
        try {
            mockResolver.insert(TodoListSchema.Entries.CONTENT_URI, values);
            fail("Expected insert failure for existing record but insert succeeded.");
        } catch (Exception e) {
            // succeeded, so do nothing.
        }
    }

    public void testDeleteEmpty(){

        int rowsDeleted = mockResolver.delete(
            TodoListSchema.Entries.CONTENT_URI, // the base URI of the table
            null,                        // all columns
            null                        //
        );

        assertEquals(0, rowsDeleted);
    }

    public void testDelete(){

        final String where = TodoListSchema.Entries.TITLE + " = " + "?";
        final String[] whereArgs = { "Entry0" };

        insertData();

        Cursor cursor = mockResolver.query(
            TodoListSchema.Entries.CONTENT_URI, // the base URI of the table
            null,                      // no projection, return all columns
            where,         // select based on the title column
            whereArgs,            // select title = "Entry0"
            null                       // use the default sort order
        );

        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToFirst());

        int rowsDeleted = mockResolver.delete(
            TodoListSchema.Entries.CONTENT_URI, // the base URI of the table
            where,         // same selection column, "title"
            whereArgs             // same selection arguments, title = "Entry0"
        );

        assertEquals(1, rowsDeleted);

        cursor = mockResolver.query(
            TodoListSchema.Entries.CONTENT_URI, // the base URI of the table
            null,                      // no projection, return all columns
            where,         // select based on the title column
            whereArgs,            // select title = "Entry0"
            null                       // use the default sort order
        );

        assertEquals(0, cursor.getCount());

        rowsDeleted = mockResolver.delete(
            TodoListSchema.Entries.CONTENT_URI, // the base URI of the table
            where,         // same selection column, "title"
            whereArgs             // same selection arguments, title = "Entry0"
        );

        assertEquals(0, rowsDeleted);

    }

    public void testUpdatesEmpty() {

        ContentValues values = new ContentValues();
        String newNote = "Testing an update with this string";
        values.put(TodoListSchema.Entries.NOTES, newNote);

        int rowsUpdated = mockResolver.update(
            TodoListSchema.Entries.CONTENT_URI,  // the URI of the data table
            values,                     // a map of the updates to do (column title and value)
            null,                       // select based on the title column
            null                        // select "title = Entry1"
        );

        assertEquals(0, rowsUpdated);
    }
    public void testUpdates() {

        final String[] what = { TodoListSchema.Entries.NOTES};
        final String  where = TodoListSchema.Entries.TITLE + " = " + "?";
        final String[] whereArgs = { "Entry1" };

        ContentValues values = new ContentValues();
        String newNote = "Testing an update with this string";
        values.put(TodoListSchema.Entries.NOTES, newNote);

        insertData();

        int rowsUpdated = mockResolver.update(
            TodoListSchema.Entries.CONTENT_URI,   // The URI of the data table
            values,                      // the same map of updates
            where,            // same selection, based on the title column
            whereArgs                // same selection argument, to select "title = Entry1"
        );

        assertEquals(1, rowsUpdated);

        Cursor cursor = mockResolver.query(
                TodoListSchema.Entries.CONTENT_URI, // the base URI for the table
                what,        // returns the entry notes
                where,         // select based on the title column
                whereArgs,            // select title of "Entry1"
                null
        );

        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToFirst());
        int notesIndex = cursor.getColumnIndex(TodoListSchema.Entries.NOTES);
        assertEquals(newNote, cursor.getString(notesIndex));

    }

    public void testBatchDeleteAndInsert() {

        // Delete Entry0
        final String where = TodoListSchema.Entries.TITLE + " = " + "?";
        final String[] whereArgs = { "Entry0" };
        final String[] what = {TodoListSchema.Entries._ID};

        // Insert Entry30
        EntryData newEntry = new EntryData(30,"Entry30", "This is Entry30", false);
        newEntry.setCreateTime(START_DATE + (10 * ONE_DAY_MILLIS));
        newEntry.setModifyTime(START_DATE + (2 * ONE_WEEK_MILLIS));


        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();

        operations.add(
                ContentProviderOperation.newDelete(
                        TodoListSchema.Entries.CONTENT_URI)
                        .withSelection(where, whereArgs)
                        .build()
        );

        operations.add(
                ContentProviderOperation.newInsert(
                        TodoListSchema.Entries.CONTENT_URI)
                        .withValues(newEntry.toContentValues())
                        .build()
        );

        insertData();

        try {
            ContentProviderResult[] results =  mockResolver.applyBatch(TodoListSchema.AUTHORITY, operations);
            assertEquals(1, (int)results[0].count);
            assertEquals(1, mockResolver.query(results[1].uri,what, null,null,null).getCount());

        } catch (RemoteException e) {
            fail(e.toString());
        } catch (OperationApplicationException e) {
            fail(e.toString());
        }
    }

    public void testBatchDeleteAndInsertInvalidUri() {

        // Delete Entry0
        final String where = TodoListSchema.Entries.TITLE + " = " + "?";
        final String[] whereArgs = { "Entry0" };

         // Insert Entry30
        EntryData newEntry = new EntryData(30,"Entry30", "This is Entry30", false);
        newEntry.setCreateTime(START_DATE + (10 * ONE_DAY_MILLIS));
        newEntry.setModifyTime(START_DATE + (2 * ONE_WEEK_MILLIS));


        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();

        operations.add(
                ContentProviderOperation.newDelete(TodoListSchema.Entries.CONTENT_URI)
                        .withSelection(where, whereArgs)
                        .build()
        );

        operations.add(
                ContentProviderOperation.newInsert(Uri.withAppendedPath(TodoListSchema.Entries.CONTENT_URI, "dummy"))
                        .withValues(newEntry.toContentValues())
                        .build()
        );

        insertData();

        try {
            ContentProviderResult[] results =  mockResolver.applyBatch(TodoListSchema.AUTHORITY, operations);
            assertEquals(0, results.length);
            assertEquals(1, mockResolver.query(TodoListSchema.Entries.CONTENT_URI,
                                null,where,whereArgs,null).getCount());

        } catch (RemoteException e) {
            fail(e.toString());
        } catch (OperationApplicationException e) {
            fail(e.toString());
        }
    }

}
