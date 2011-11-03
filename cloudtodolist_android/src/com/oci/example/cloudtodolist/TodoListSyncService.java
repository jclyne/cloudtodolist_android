package com.oci.example.cloudtodolist;

import android.accounts.Account;
import android.app.IntentService;
import android.content.ContentProviderClient;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.oci.example.cloudtodolist.client.HttpRestClient;
import com.oci.example.cloudtodolist.provider.RestDataProvider;
import com.oci.example.cloudtodolist.provider.TodoListProvider;
import com.oci.example.cloudtodolist.provider.TodoListSchema;

/**
 * Service that handles sync and refresh request intents and handles
 * requesting the sync from the TodoList provider on a background
 * thread.
 * This will handle backing off requests when there are network errors
 * as well honoring the offline mode preference and system wide
 * background data setting
 */
public class TodoListSyncService extends IntentService {

    // Log Tag
    private static final String TAG = "TodoListSyncService";

    // Definitions of the supported intents
    private static final String INTENT_BASE = "com.oci.example.cloudtodolist.";
    public static final String ACTION_TODOLIST_SYNC = INTENT_BASE + "SYNC";
    public static final String ACTION_TODOLIST_FULL_SYNC = INTENT_BASE + "FULL_SYNC";
    
    // These are notification intents, an activity can register for a broadcast to get updates
    public static final String STATUS_TODOLIST_SYNC_STARTED = INTENT_BASE + "SYNC_STARTED";
    public static final String STATUS_TODOLIST_SYNC_COMPLETE = INTENT_BASE + "SYNC_COMPLETE";

    // Reference to the content provider to sync
    private ContentProviderClient  todoListProviderClient;

    // HttpRest client to provide to the provider for sync
    private HttpRestClient client;


    @SuppressWarnings({"FieldCanBeLocal"})
    private final int NETWORK_ERROR_RETRY = 30000;

    /**
     * Default constructor
     */
    public TodoListSyncService() {
        super("TodoListSyncService");
    }

    /**
     * Called by the system when the service is first created..
     */
    @Override
    public void onCreate() {
        super.onCreate();


        // Initialize the PreferenceManager reference
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        // Build a new rest client with the http client
        String serverAddr = prefs.getString(getString(R.string.setting_server_address),
                getString(R.string.app_host_name));

        boolean useHttps = prefs.getBoolean(getString(R.string.setting_https), true);

        client = new HttpRestClient(serverAddr, useHttps, getBaseContext());

        // Initialize the TodoListProvider reference
        todoListProviderClient = getContentResolver().acquireContentProviderClient(TodoListSchema.AUTHORITY);

        Log.d(TAG, "Service Created" + " (" + Thread.currentThread().getName() + ")");
        sendBroadcast(new Intent(STATUS_TODOLIST_SYNC_STARTED));
    }

    /**
     * Called by the system every time a client explicitly starts the service
     *
     * @param intent  intent supplied to startService
     * @param flags   additional data about this start request.
     * @param startId unique integer representing this specific request to start.
     * @return indicates what semantics the system should use for the service's
     *         current started state. should be values from START_CONTINUATION_MASK
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Started, startId:" + startId + " (" + Thread.currentThread().getName() + ")");
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Called by the system to notify a Service that it is no longer used and is being removed.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        todoListProviderClient.release();
        client.close();
        
        Log.d(TAG, "Service Destroyed" + " (" + Thread.currentThread().getName() + ")");
        sendBroadcast(new Intent(STATUS_TODOLIST_SYNC_COMPLETE));
    }


    /**
     * This method is invoked on the worker thread with a request to process. Only one
     * Intent is processed at a time, but the processing happens on a worker thread
     * that runs independently from other application logic. So, if this code takes a
     * long time, it will hold up other requests to the same IntentService, but it will
     * not hold up anything else. When all requests have been handled,
     * the IntentService stops itself
     * <p/>
     * This implementation will call onPerformSync on the TodoListProvider if we are
     * currently online. This handles an ACTION_TODOLIST_SYNC and ACTION_TODOLIST_REFRESH
     * intents. It will also schedule another periodic sync regardless of the whether
     * onPerformSync was called or succeeded/failed.
     *
     * @param intent intent supplied to startService
     */
    @Override
    protected void onHandleIntent(Intent intent) {

        String action = intent.getAction();
        Log.d(TAG, "onHandleIntent: Action = " + action + " (" + Thread.currentThread().getName() + ")");

        if (TodoListSyncHelper.isOnline(getBaseContext())
        		&& TodoListSyncHelper.isSyncEnabled(getBaseContext())) {
            RestDataProvider.SyncResult res;
            boolean fullSync = action.equals(ACTION_TODOLIST_FULL_SYNC);

            // Get the currently configured preferred account to use to sync against
            Account account = TodoListSyncHelper.getPreferredAccount(getBaseContext());
            if (account == null) {
                TodoListSyncHelper.scheduleSync(getBaseContext());
                return;
            }

            TodoListProvider provider = (TodoListProvider)todoListProviderClient.getLocalContentProvider();
            if (provider != null){
	            res = provider.onPerformSync(client, account, fullSync);
	            if (res.fullSyncRequested) {
	                res = provider.onPerformSync(client, account, fullSync);
	            }
	
	            TodoListSyncHelper.showSyncResultNotification(getBaseContext(),res);
	            
	
	            if (res.networkError())
	            {
	                TodoListSyncHelper.scheduleSync(getBaseContext(), NETWORK_ERROR_RETRY);
	            } else if (!res.serverError()){
		            /**
		             * On a server error, don't schedule another sync. This is not likely to go away
		             * so just wait until an explicit refresh is requested
		             */
	                TodoListSyncHelper.scheduleSync(getBaseContext());
	            }
            }
        }
    }
}
