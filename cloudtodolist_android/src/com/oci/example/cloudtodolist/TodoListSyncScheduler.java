package com.oci.example.cloudtodolist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.util.Log;

/**
 * Broadcast receiver that handles scheduling sync requests on resumption of
 * network access as well as scheduling periodic syncs at boot time
 */
public class TodoListSyncScheduler extends BroadcastReceiver {

    // Log Tag
    private static final String TAG = "TodoListSyncScheduler";

    /**
     * Called when the BroadcastReceiver is receiving an Intent broadcast.
     *
     * @param ctxt   current application context
     * @param intent intent being received
     */
    @Override
    public void onReceive(Context ctxt, Intent intent) {
        Log.d(TAG, intent.toString());

        String action = intent.getAction();

        if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
                Log.w(TAG, "Loss of Network connectivity detected, disabling sync");
            } else {
                Log.i(TAG, "Network connectivity detected, enabling sync");
                TodoListSyncHelper.requestSync(ctxt);
            }

        } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.i(TAG, "Enabling boot time background sync");
            TodoListSyncHelper.requestSync(ctxt);
        }

    }
}
