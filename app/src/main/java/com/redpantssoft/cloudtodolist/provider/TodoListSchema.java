package com.redpantssoft.cloudtodolist.provider;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Defines the schema for the TodoList content provider. A schema  defines the information
 * that a client needs to access the provider as one or more data tables. A schmea
 * is a public, non-extendable (final) class that contains constants defining column names and
 * URIs. The schema should define the structure and type of the data that is being made availabe
 * from this provider AUTHORITY. A well-written client depends only on the constants in the contract.
 */
@SuppressWarnings({"WeakerAccess"})
public final class TodoListSchema {
    // SCHEME component of the content URI
    public static final String SCHEME = "content://";
    // AUTHORITY component of the content URI
    public static final String AUTHORITY = "com.redpantssoft.cloudtodolist.provider";
    // Base path for the cloudtodolist data in the content URI
    public static final String PATH_TODOLIST = "cloudtodolist/";

    private TodoListSchema() {
    }

    /**
     * Defines the schema for the Entries table in the TodoList schema. This table
     * store information about entries in the cloudtodolist table
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public static final class Entries implements BaseColumns {

        // Underlying Database Table Name
        static final String TABLE_NAME = "entries";

        // This class cannot be instantiated
        private Entries() {
        }

        /**
         * Uri Path Definitions for this table.
         * These URI components are package local and should not be used by the
         * client to access the data. They can be used by the provider to build
         * a URI filter or to validate the URI in request handlers
         */
        static final String PATH_TODOLIST_ENTRIES = PATH_TODOLIST + "entries";
        static final String PATH_TODOLIST_ENTRY_ID = PATH_TODOLIST + "entries/";
        static final int TODOLIST_ENTRY_ID_PATH_POSITION = 2;

        /**
         * URI Definitions
         * The data should be reference by these URIs when using the content resolver. This
         * is the normal way that the provider data should be accessed.
         * <p/>
         * The CONTENT_URI references the complete table while the CONTENT_ID_URI references
         * individual rows. The CONTENT_ID_URI should be built for the specific row, referenced
         * by entry id with:
         * ContentUris.withAppendedId(TodoListSchema.Entries.CONTENT_ID_URI_BASE, entryId);
         */
        //
        public static final Uri CONTENT_URI
                = Uri.parse(SCHEME + AUTHORITY + "/" + PATH_TODOLIST_ENTRIES);

        public static final Uri CONTENT_ID_URI_BASE
                = Uri.parse(SCHEME + AUTHORITY + "/" + PATH_TODOLIST_ENTRY_ID);

        /**
         * MIME type definitions
         * These types are used to dynamically query the provider for supported types
         * when a mime type is included in an intent filter
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.oci.cloudtodolist.entries";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.oci.cloudtodolist.entry";

        /**
         * Default Sort Order for requests
         * This used whenever the sort order is omitted
         */

        public static final String DEFAULT_SORT_ORDER = "created ASC";

        /**
         * Data Field Definitions
         * These defines are used to identify fields in a projection map (what clause), as
         * well as to find column indexes in a result cursor
         */
        public static final String ID = "ID";
        public static final String TITLE = "title";
        public static final String NOTES = "notes";
        public static final String COMPLETE = "complete";
        public static final String CREATED = "created";
        public static final String MODIFIED = "modified";
        public static final String PENDING_UPDATE = "pending_update";
        public static final String PENDING_DELETE = "pending_delete";
        public static final String PENDING_TX = "pending_tx";
    }
}