package com.oci.example.cloudtodolist;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;

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
}
