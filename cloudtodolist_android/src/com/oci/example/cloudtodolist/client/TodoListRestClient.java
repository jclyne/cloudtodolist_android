package com.oci.example.cloudtodolist.client;


import android.content.ContentValues;
import android.util.Log;
import org.apache.http.auth.AuthenticationException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;


/**
 * <p>Implements the service specific aspects of the TodoList REST API. The class
 * makes use of an HttpRestClient object to implement specifics of the api. All
 * defined responses are modeled in Response based object. </p>
 * <p/>
 * <p/>
 * <p>Protocol Definition:
 * <p/>
 * Data Format:
 * todolist_entry object
 * {
 * "id": entry ID,
 * "title": title of the Entry,
 * "notes": notes associated with the entry,
 * "complete": flag indicating whether the entry is complete
 * }
 * todolist_entry array
 * {
 * timestamp: timestamp to be used in a get with a modified time
 * entries:
 * [
 * {
 * "id": entry ID,
 * "title": title of the Entry,
 * "notes": notes associated with the entry,
 * "complete": flag indicating whether the entry is complete
 * },
 * {
 * "id": entry ID,
 * "title": title of the Entry,
 * "notes": notes associated with the entry,
 * "complete": flag indicating whether the entry is complete
 * },
 * ...
 * ]
 * }
 * <p/>
 * Status Codes:
 * 200(ok) - request was successful
 * 201(created) - new entry as been created
 * 400(bad request) - invalid query string
 * 410(gone) - entry does not exist
 * <p/>
 * <p/>
 * URIs:
 * cloudtodolist/entries - list of cloudtodolist entries
 * GET
 * Format - todolist_entry array
 * Query Parameters = id,modified (e.g. '?id=1;id=3;id=5' or "?modified=1317532850.83)
 * NOTE: Omitting the id parameter will retrieve all entries
 * NOTE: When a modified flag is used, deleted entries may be returned with a
 * deleted flag=true
 * Status Codes - 200,400
 * <p/>
 * POST
 * Format - todolist_entry
 * Query Parameters = title,notes,complete (e.g. '?title=ENTRY' or '?title=ENTRY;notes=NOTES')
 * Status Codes - 201,400
 * <p/>
 * DELETE
 * Format - empty
 * Query Parameters = id (e.g. '?id=1' or '?id=1+2+5')
 * Status Codes - 200
 * <p/>
 * cloudtodolist/entries/<id> - single cloudtodolist entry, referenced by entry id
 * GET
 * Format - todolist_entry
 * Status Codes - 200,410
 * <p/>
 * PUT
 * Format - todolist_entry
 * Query Parameters = title,notes,complete (e.g. '?title=ENTRY' or '?title=ENTRY;notes=NOTES')
 * Status Codes - 200,400,410
 * <p/>
 * DELETE
 * Format - empty
 * Status Codes - 200 </p>
 */
public final class TodoListRestClient {

    // Logging tag
    private static final String TAG = "TodoListRestClient";

    // URL of the cloudtodolist entries resource
    private static final String ENTRIES_PATH = "/todolist/entries";

    /**
     * Fields defined in the response message types
     */
    public static final String ENTRY_ID = "id";
    public static final String ENTRY_TITLE = "title";
    public static final String ENTRY_NOTES = "notes";
    public static final String ENTRY_COMPLETE = "complete";
    public static final String ENTRY_DELETED = "deleted";
    public static final String ENTRY_CREATED = "created";
    public static final String ENTRY_MODIFIED = "modified";


    // Instance of an HttpRestClient to make API requests
    private final HttpRestClient client;

    /**
     * Base class for a TodoList API response. The class
     * wraps an HttpRestClient response and defines the expected
     * response codes
     */
    public class Response {
        /**
         * Expected response status codes in a TodoList Response
         */
        public static final int SUCCESS_OK = 200;
        public static final int SUCCESS_ADDED = 201;
        public static final int FAILED_BAD_REQUEST = 400;
        public static final int FAILED_INVALID_RESOURCE = 410;

        // Instance of a HttpRestClient response
        private final HttpRestClient.Response response;

        /**
         * Constructor
         *
         * @param response HttpRestClient.response
         */
        public Response(HttpRestClient.Response response) {
            this.response = response;
        }

        /**
         * @return HttpRestClient for the TodoList api response
         */
        public HttpRestClient.Response getResponse() {
            return response;
        }
    }

    /**
     * Encapsulates an Entry Object response from a TodoList API  request.
     * Certain api requests return a single entry object. It encapsulates
     * a parsed, and immutable, JSONObject representing the entry
     */
    public class EntryObjectResponse extends Response {

        // JSON parsed entry object
        private final JSONObject entryObject;

        /**
         * Constructor
         *
         * @param response    HttpRestClient.response
         * @param entryObject entry object as a JSONObject
         */
        public EntryObjectResponse(HttpRestClient.Response response, JSONObject entryObject) {
            super(response);
            this.entryObject = entryObject;
        }

        /**
         * @return entry object as a JSONObject
         */
        public JSONObject getEntryObject() {
            return entryObject;
        }
    }

    /**
     * Encapsulates a list of Entry Objects from a TodoList API request.
     * Certain api requests return a list of cloudtodolist entries as well as a
     * timestamp for incremental updates. It encapsulates a list of parsed,
     * and immutable, JSONObjects representing the entries in the list
     */
    public class EntryListResponse extends Response {

        // Expected response values
        private static final String ENTRY_LIST_TIMESTAMP = "timestamp";
        private static final String ENTRY_LIST_ENTRIES = "entries";

        // Timestamp from the response
        private final double timestamp;


        // List of cloudtodolist entries in the response
        private final List<JSONObject> entryList = new ArrayList<JSONObject>();

        /**
         * Constructor  - parses the entry list object
         *
         * @param response  HttpRestClient.response
         * @param entryList list of entry objects as a JSONObject
         * @throws JSONException indicates that the response is invalid or
         *                       the schema was unexpected
         */
        public EntryListResponse(HttpRestClient.Response response, JSONObject entryList)
                throws JSONException {
            super(response);
            if (entryList != null) {
                this.timestamp = entryList.getDouble(ENTRY_LIST_TIMESTAMP);
                JSONArray entryArray = entryList.getJSONArray(ENTRY_LIST_ENTRIES);
                for (int idx = 0; idx < entryArray.length(); idx++)
                    this.entryList.add(entryArray.getJSONObject(idx));
            } else {
                this.timestamp = 0;
            }
        }

        /**
         * @return timestamp from the response
         */
        public double getTimestamp() {
            return timestamp;
        }

        /**
         * @return list of cloudtodolist entries in the response
         */
        public List<JSONObject> getEntryList() {
            return entryList;
        }
    }

    /**
     * Constructor
     *
     * @param client HttpRestClient to use for API requests
     */
    public TodoListRestClient(HttpRestClient client) {
        this.client = client;
    }

    /**
     * Builds a URI query string from an array of keys correlating to keys in
     * a ContentValues object
     *
     * @param keys   keys from the ContentValues object to include in the query string
     * @param values ContentValues object containing query string values
     * @return constructed query string
     */
    private static String buildQueryString(String[] keys, ContentValues values) {
        String queryString = "";
        if (values != null) {
            for (String key : keys) {
                if (values.containsKey(key)) {
                    if (!queryString.isEmpty()) queryString += ";";
                    queryString += key + "=" + values.getAsString(key);
                }
            }
        }
        return (queryString.isEmpty() ? null : queryString);
    }

    /**
     * Creates a new cloudtodolist entry via HTTP post request
     *
     * @param values ContantValues containing fields for the http post
     *               query string to create a new entry
     * @return EntryObjectReponse encapsulating the newly created entry
     * @throws URISyntaxException      indicates invalid syntax in the request's resulting URI
     * @throws IOException             indicates error in underlying network state or operation
     * @throws JSONException           indicates an error in the JSON response from the request, either
     *                                 the JSON is invalid or the schema was not expected
     * @throws AuthenticationException indicates an error with the Authentication process
     */
    public EntryObjectResponse postEntry(ContentValues values)
            throws IOException, URISyntaxException, JSONException, AuthenticationException {

        String uri = ENTRIES_PATH;
        String[] validParams = {ENTRY_TITLE, ENTRY_NOTES, ENTRY_COMPLETE};

        HttpRestClient.Response response = client.Post(uri,
                buildQueryString(validParams, values),
                HttpRestClient.ContentType.JSON);
        JSONObject resonseObject = null;
        if (response.succeeded()) {
            resonseObject = new JSONObject(response.getContent());

            Log.i(TAG, "post entry: " + resonseObject.getInt(ENTRY_ID));
        } else {
            Log.e(TAG, "post failed: " + response.getStatusCode() + "- " + response.getContent());
        }

        return new EntryObjectResponse(response, resonseObject);
    }

    /**
     * Updates a  cloudtodolist entry via HTTP put request
     *
     * @param id     id of the cloudtodolist entry to update
     * @param values ContantValues containing fields for the http put
     *               query string to update the entry
     * @return EntryObjectReponse encapsulating the newly updated entry
     * @throws URISyntaxException      indicates invalid syntax in the request's resulting URI
     * @throws IOException             indicates error in underlying network state or operation
     * @throws JSONException           indicates an error in the JSON response from the request, either
     *                                 the JSON is invalid or the schema was not expected
     * @throws AuthenticationException indicates an error with the Authentication process
     */
    public EntryObjectResponse putEntry(int id, ContentValues values)
            throws IOException, URISyntaxException, JSONException, AuthenticationException {

        String uri = ENTRIES_PATH + "/" + id;
        String[] validParams = {ENTRY_TITLE, ENTRY_NOTES, ENTRY_COMPLETE};

        HttpRestClient.Response response = client.Put(uri,
                buildQueryString(validParams, values),
                HttpRestClient.ContentType.JSON);
        JSONObject responseObject = null;
        if (response.succeeded()) {
            responseObject = new JSONObject(response.getContent());

            Log.i(TAG, "post entry: " + responseObject.getInt(ENTRY_ID));
        } else {
            Log.e(TAG, "post failed: " + response.getStatusCode() + "- " + response.getContent());
        }

        return new EntryObjectResponse(response, responseObject);
    }

    /**
     * Deletes a cloudtodolist entry via HTTP delete request
     *
     * @param id id of the cloudtodolist entry to delete
     * @return EntryObjectReponse encapsulating the newly updated entry
     * @throws URISyntaxException      indicates invalid syntax in the request's resulting URI
     * @throws IOException             indicates error in underlying network state or operation
     * @throws AuthenticationException indicates an error with the Authentication process
     */
    public Response deleteEntry(int id) throws IOException, URISyntaxException, AuthenticationException {
        String uri = ENTRIES_PATH + "/" + id;

        HttpRestClient.Response response = client.Delete(uri, null, HttpRestClient.ContentType.JSON);
        if (response.succeeded()) {
            Log.i(TAG, "deleted entry: " + id);
        } else {
            Log.e(TAG, "delete failed: " + response.getStatusCode() + "- " + response.getContent());
        }


        return new Response(response);
    }

    /**
     * Gets the current list of cloudtodolist entries via get request
     *
     * @return EntryListResponse representing the response of the get request
     * @throws URISyntaxException      indicates invalid syntax in the request's resulting URI
     * @throws IOException             indicates error in underlying network state or operation
     * @throws JSONException           indicates an error in the JSON response from the request, either
     *                                 the JSON is invalid or the schema was not expected
     * @throws AuthenticationException indicates an error with the Authentication process
     */
    public EntryListResponse getEntries()
            throws IOException, URISyntaxException, JSONException, AuthenticationException {
        return getEntries(null);

    }

    /**
     * Gets the current list of cloudtodolist entries via get request. Allows for a modified
     * qualifier that will return entries modified after the specified time stamp.
     * <p/>
     * <p>Normally, the modified value will be a timestamp value returned from a
     * previous getEntries request. This allows an initial getEntries() followed by
     * getEntries(modified) to get updates since last request</p>
     * <p/>
     * <p>This form of the get request may return deleted entries. The API defines an
     * archive time of 24hours for deleted entries. This means that a request with a
     * modified time that is more than 24 hours in the past, will fail with a
     * FAILED_BAD_REQUEST(401)</p>
     *
     * @param modified timestamp to filter responses having a modified time greater than
     *                 the specified value
     * @return EntryListResponse representing the response of the get request
     * @throws URISyntaxException      indicates invalid syntax in the request's resulting URI
     * @throws IOException             indicates error in underlying network state or operation
     * @throws JSONException           indicates an error in the JSON response from the request, either
     *                                 the JSON is invalid or the schema was not expected
     * @throws AuthenticationException indicates an error with the Authentication process
     */
    public EntryListResponse getEntries(Double modified)
            throws IOException, URISyntaxException, JSONException, AuthenticationException {

        String queryString = null;
        if (modified != null)
            queryString = String.format("%s=%f", ENTRY_MODIFIED, modified);

        HttpRestClient.Response response = client.Get(ENTRIES_PATH, queryString, HttpRestClient.ContentType.JSON);
        if (response.succeeded()) {
            EntryListResponse resp = new EntryListResponse(response, new JSONObject(response.getContent()));
            Log.i(TAG, "getEntries retrieved " + resp.getEntryList().size() + " entries");
            return resp;
        } else {
            Log.e(TAG, "getEntries failed: " + response.getStatusCode() + "- " + response.getContent());
        }
        return new EntryListResponse(response, null);
    }
}
