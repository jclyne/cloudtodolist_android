package com.redpantssoft.cloudtodolist;


import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;

import com.redpantssoft.cloudtodolist.provider.RestDataProvider;

/**
 * Provides helper methods for scheduling syncs via a completely static helper class.
 * The helper allows for easy request for syncs, lazy syncs, full refresh or schedule
 * a periodic sync.
 * <p/>
 * A lazy sync will schedule a sync to occur in the future, after the LAZY_INTERVAL, and
 * will occur after no lazySyncs have been requested in the LAZY_INTERVAL.
 */
@SuppressWarnings({"UnusedDeclaration"})
public class TodoListSyncHelper {

    // Intent to request a sync from the sync service
    private static Intent syncIntent = new Intent(TodoListSyncService.ACTION_TODOLIST_SYNC);

    // Intent to request a refresh from the sync service
    private static Intent fullSyncIntent = new Intent(TodoListSyncService.ACTION_TODOLIST_FULL_SYNC);

    // Lazy interval for lazy sync requests
    private static final int LAZY_INTERVAL = 5000; // hardcoded to 5 seconds
    
    // ID of the sync result notification message
    private static final int SYNC_RESULT_NOTIFICATION_ID = 1;

    /**
     * Requests that sync be performed immediately
     *
     * @param ctxt current application context
     */
    public static void requestSync(Context ctxt) {
        ctxt.startService(syncIntent);
    }

    /**
     * Requests that a sync be performed lazily
     *
     * @param ctxt current application context
     */
    public static void requestLazySync(Context ctxt) {
        scheduleSyncAlarm(ctxt, syncIntent, LAZY_INTERVAL);
    }

    /**
     * Requests that a refresh be performed immediately
     *
     * @param ctxt current application context
     */
    public static void requestFullSync(Context ctxt) {
        ctxt.startService(fullSyncIntent);
    }

    /**
     * Requests that a sync be scheduled for the interval specified
     * in the sync_interval shared preference.
     *
     * @param ctxt current application context
     */
    public static void scheduleSync(Context ctxt) {
        Resources res = ctxt.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
        int syncInterval = Integer.parseInt(
                prefs.getString(res.getString(R.string.setting_sync_interval),
                        res.getString(R.string.setting_sync_interval_default_value))
        );

        scheduleSyncAlarm(ctxt, syncIntent, syncInterval);
    }

    /**
     * Requests that a sync be scheduled for the interval specified
     * by the syncInterval parameter
     *
     * @param ctxt         current application context
     * @param syncInterval interval to delay scheduling the sync
     */
    public static void scheduleSync(Context ctxt, int syncInterval) {
        scheduleSyncAlarm(ctxt, syncIntent, syncInterval);
    }

    /**
     * Cancels any pending sync or refresh requests. A periodic sync needs to
     * be rescheduled after cancelling
     *
     * @param ctxt current application context
     */
    public static void cancelPendingSync(Context ctxt) {
        AlarmManager alarmManager = (AlarmManager) ctxt.getSystemService(Context.ALARM_SERVICE);
        PendingIntent syncOperation = PendingIntent.getService(ctxt, 0, syncIntent, 0);

        alarmManager.cancel(syncOperation);
    }
    
    /**
     * Retrieves the currently configured preferred account for syncing.
     * There is a shared preference that indicates the  preferred account. This will validate
     * that the preferred account setting still refers to a valid account. If there is no
     * preferred account set, it will return the first account of the "com.google"  type
     *
     * @return Account object for the preferred account, or null if no accounts exists
     */
    public static Account getPreferredAccount(Context ctxt) {
        AccountManager accountManager = AccountManager.get(ctxt);
        Account account = null;
        Account[] accounts = accountManager.getAccountsByType(ctxt.getString(R.string.setting_account_type));

        if (accounts.length == 0) {
            // no accounts error
            return null;
        }

        String accountName = getSharedPreferences(ctxt).getString(ctxt.getString(R.string.setting_google_account), null);
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
    public static boolean isOnline(Context ctxt) {
        final NetworkInfo netInfo = getConnectivityManager(ctxt).getActiveNetworkInfo();
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
    public static boolean isSyncEnabled(Context ctxt) {
        return !getSharedPreferences(ctxt).getBoolean(ctxt.getString(R.string.setting_offline_mode), false)
                && getConnectivityManager(ctxt).getBackgroundDataSetting();
    }

    /**
     * Show a system notification to indicate to the user that the TodoList was updated
     *
     * @param syncResult result of the previously successful sync operation
     */
    public static void showSyncResultNotification(Context ctxt, RestDataProvider.SyncResult syncResult) {

    	if (!syncResult.needsNotification())
    		return;
    	
    	Notification notification = null;
    	
    	// Build a pendingIntent that displays the cloudtodolist activity
        PendingIntent todoListActivityIntent =
                PendingIntent.getActivity(
                		ctxt, 0, new Intent(ctxt, TodoListActivity.class), 0);


        // Set the latest event info, this display info regarding the very latest event being notified on
        if (syncResult.updated()){
        	notification = new Notification(
                    R.drawable.icon,
                    ctxt.getString(R.string.sync_update_ticker),
                    System.currentTimeMillis());

	        notification.setLatestEventInfo(
	        		ctxt,
	        		ctxt.getResources().getQuantityString(R.plurals.sync_update_title,
	                        (int) syncResult.numEntries,
	                        (int) syncResult.numEntries),
	                ctxt.getString(R.string.sync_update_text),
	                todoListActivityIntent);
        } else if (syncResult.authenticationError()) {
        	notification = new Notification(
                    R.drawable.icon,
                    ctxt.getString(R.string.sync_invalid_credentials_ticker),
                    System.currentTimeMillis());

        	notification.setLatestEventInfo(
	        		ctxt,
	        		ctxt.getString(R.string.sync_invalid_credentials_title),
	                ctxt.getString(R.string.sync_invalid_credentials_text),
	                todoListActivityIntent);
        } 
        
        if (notification != null){
	        notification.defaults |= Notification.DEFAULT_ALL;
	        notification.flags |= Notification.FLAG_AUTO_CANCEL;
	
	        // issue the notification
	        getNotificationManager(ctxt).notify(SYNC_RESULT_NOTIFICATION_ID, notification);
        }
    }

    /**
     * Sets an alarm to handle a scheduled sync. This will cancel any pending sync alarms,
     * and schedule a new one to expire in when milliseconds
     *
     * @param ctxt   current application context
     * @param action intent to deliver to the sync service
     * @param when   time the alarm should go off, in milliseconds
     */
    private static void scheduleSyncAlarm(Context ctxt, Intent action, int when) {
        AlarmManager alarmManager = (AlarmManager) ctxt.getSystemService(Context.ALARM_SERVICE);
        PendingIntent syncOperation = PendingIntent.getService(ctxt, 0, action, 0);

        alarmManager.cancel(syncOperation);
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + when, syncOperation);
    }
    
    /**
     * Returns a reference to the system wide ConnectivityManager
     * 
     * @param ctxt current application context
     * @return reference to the system wide ConnectivityManager
     */
    private static ConnectivityManager getConnectivityManager(Context ctxt){
    	return (ConnectivityManager) ctxt.getSystemService(Context.CONNECTIVITY_SERVICE);
    }
    
    /**
     * Returns a reference to the system wide NotificationManager
     * 
     * @param ctxt current application context
     * @return reference to the system wide NotificationManager
     */
    private static NotificationManager getNotificationManager(Context ctxt){
    	return (NotificationManager) ctxt.getSystemService(Context.NOTIFICATION_SERVICE);
    }
    
    /**
     * Returns a reference to the applications SharedPreferences
     * 
     * @param ctxt current application context
     * @return reference to the applications SharedPreferences
     */
    private static SharedPreferences getSharedPreferences(Context ctxt){
    	return PreferenceManager.getDefaultSharedPreferences(ctxt);
    }
}
