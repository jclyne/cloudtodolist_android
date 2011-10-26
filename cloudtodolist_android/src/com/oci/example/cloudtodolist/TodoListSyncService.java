package com.oci.example.cloudtodolist;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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

    // ID of the sync result notification message
    private static final int SYNC_RESULT_NOTIFICATION_ID = 1;

    // Reference to the system wide ConnectivityManager
    private ConnectivityManager connManager;

    // Reference to the system wide Notification Manager to notify the user of Sync results
    private NotificationManager notificationManager;

    // Reference to the application SharedPreferences
    private SharedPreferences prefs;

    // Reference to the content provider to sync
    private TodoListProvider provider;

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

        // Initialize the ConnectivityManager reference
        connManager = (ConnectivityManager) getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        // Get a reference to the notification manager
        notificationManager = (NotificationManager) getBaseContext().getSystemService(Context.NOTIFICATION_SERVICE);

        // Initialize the PreferenceManager reference
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        // Build a new rest client with the http client
        String serverAddr = prefs.getString(getString(R.string.setting_server_address),
                getString(R.string.app_host_name));

        boolean useHttps = prefs.getBoolean(getString(R.string.setting_https), true);

        client = new HttpRestClient(serverAddr, useHttps, getBaseContext());

        // Initialize the TodoListProvider reference
        provider = (TodoListProvider) getContentResolver()
                .acquireContentProviderClient(TodoListSchema.AUTHORITY)
                .getLocalContentProvider();

        Log.d(TAG, "Service Created" + " (" + Thread.currentThread().getName() + ")");
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
        Log.d(TAG, "Service Destroyed" + " (" + Thread.currentThread().getName() + ")");
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

        if (isOnline() && isSyncEnabled()) {
            RestDataProvider.SyncResult res;
            boolean fullSync = action.equals(ACTION_TODOLIST_FULL_SYNC);

            // Get the currently configured preferred account to use to sync against
            Account account = getPreferredAccount();
            if (account == null) {
                TodoListSyncHelper.scheduleSync(getBaseContext());
                return;
            }

            res = provider.onPerformSync(client, account, fullSync);
            if (res.fullSyncRequested) {
                res = provider.onPerformSync(client, account, fullSync);
            }

            if (res.updated()) {
                showSyncResultNotification(res);
            }

            if (res.invalidCredentials) {
                // Show invalid credentials notification
            }

            if (res.networkError())
                TodoListSyncHelper.scheduleSync(getBaseContext(), NETWORK_ERROR_RETRY);
            else if (!res.serverError())
            /**
             * On a server error, don't schedule another sync. This is not likely to go away
             * so just wait until an explicit refresh is requested
             */
                TodoListSyncHelper.scheduleSync(getBaseContext());
        }
    }

    /**
     * Retrieves the currently configured preferred account for syncing.
     * There is a shared preference that indicates the  preferred account. This will validate
     * that the preferred account setting still refers to a valid account. If there is no
     * preferred account set, it will return the first account of the "com.google"  type
     *
     * @return Account object for the preferred account, or null if no accounts exists
     */
    private Account getPreferredAccount() {
        AccountManager accountManager = AccountManager.get(getApplicationContext());
        Account account = null;
        Account[] accounts = accountManager.getAccountsByType(getString(R.string.setting_account_type));

        if (accounts.length == 0) {
            // no accounts error
            return null;
        }

        String accountName = prefs.getString(getString(R.string.setting_google_account), null);
        if (accountName != null) {
            for (Account acc : accounts) {
                if (acc.name.equals(accountName)) {
                    account = acc;
                    break;
                }
            }
        }
        // The preferred account is not set or it  no longer exists, select the first one
        // of the specified type
        if (account == null)
            account = accounts[0];

        return account;
    }

    /**
     * Determines whether or not we are currently online and should call the
     * provider's onPerformSync. This will look at the state of the active network.
     *
     * @return flag indicating whether to do an onPerformSync
     */
    private boolean isOnline() {
        final NetworkInfo netInfo = connManager.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }


    /**
     * Determines whether or not sync is enabled  and should call the
     * provider's onPerformSync.  This is determined by looking
     * at the application offline mode setting and also at the connectivity manager
     * background data setting.
     *
     * @return flag indicating whether sync is enabled
     */
    private boolean isSyncEnabled() {
        return !prefs.getBoolean(getString(R.string.setting_offline_mode), false)
                && connManager.getBackgroundDataSetting();
    }

    /**
     * Show a system notification to indicate to the user that the TodoList was updated
     *
     * @param syncResult result of the previously successful sync operation
     */
    private void showSyncResultNotification(RestDataProvider.SyncResult syncResult) {

        // Create a new notification, using system defaults
        Notification notification = new Notification(
                R.drawable.icon,
                getString(R.string.sync_update_ticker),
                System.currentTimeMillis());

        notification.defaults |= Notification.DEFAULT_ALL;

        // Build a pendingIntent that displays the cloudtodolist activity
        PendingIntent todoListActivityIntent =
                PendingIntent.getActivity(
                        getBaseContext(), 0, new Intent(getBaseContext(), TodoListActivity.class), 0);

        // Set the latest event info, this display info regarding the very latest event being notified on
        notification.setLatestEventInfo(
                getBaseContext(),
                getResources().getQuantityString(R.plurals.sync_update_title,
                        (int) syncResult.numEntries,
                        (int) syncResult.numEntries),
                getString(R.string.sync_update_text),
                todoListActivityIntent);

        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        // issue the notification
        notificationManager.notify(SYNC_RESULT_NOTIFICATION_ID, notification);
    }
}
