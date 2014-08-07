package com.redpantssoft.cloudtodolist;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;


/**
 * Activity to handle editing shared preferences for the cloudtodolist
 * application
 */
public class TodoListPreferencesActivity extends PreferenceActivity implements
		SharedPreferences.OnSharedPreferenceChangeListener {

	// Log tag
	private static final String TAG = "TodoListSettings";

	/**
	 * Called when the activity is starting. Inflates the preferences UI from
	 * the preferences xml and binds the view with the shared preferences data.
	 * Also registers a onSharedPreferenceChanged listener that handles required
	 * system updates on changes
	 * 
	 * @param savedInstanceState
	 *            saved instance state
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		// Fill the list of accounts from the account manager
		final ListPreference accountPref = (ListPreference) findPreference(getString(R.string.setting_google_account));
		final AccountManager accountManager = AccountManager
				.get(getBaseContext());
		final Account[] accounts = accountManager
				.getAccountsByType(getString(R.string.setting_account_type));

		if (accounts.length > 0) {
			final CharSequence[] entries = new CharSequence[accounts.length];
			int idx = 0;
			for (final Account account : accounts)
				entries[idx++] = account.name;

			accountPref.setEntries(entries);
			accountPref.setEntryValues(entries);
		} else {
			accountPref.setEnabled(false);
		}

		// Register the change listener
		PreferenceManager.getDefaultSharedPreferences(this)
				.registerOnSharedPreferenceChangeListener(this);
	}

	/**
	 * Called when a shared preference is changed, added, or removed.
	 * 
	 * @param prefs
	 *            SharedPreferences that received the change.
	 * @param key
	 *            key of the preference that was changed, added, or removed.
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

		if (key.equals(getString(R.string.setting_google_account))
				|| key.equals(getString(R.string.setting_server_address))) {
			// If the server address changes, request a full refresh
			TodoListSyncHelper.requestFullSync(this);
		} else if (key.equals(getString(R.string.setting_sync_interval))) {
			// On sync_interval change, reschedule a periodic sync
			TodoListSyncHelper.scheduleSync(this);

		} else if (key.equals(getString(R.string.setting_offline_mode))) {
			/**
			 * If offline mode is turned off, schedule an immediate sync. This
			 * setting does not affect scheduled syncs, the sync service will
			 * determine whether to request a sync based on the value of
			 * preference and other factors.
			 */
			final boolean enabled = prefs.getBoolean(key, false);
			Log.i(TAG, "Offline mode " + (enabled ? "enabled" : "disabled"));
			if (!enabled)
				TodoListSyncHelper.requestSync(getBaseContext());
		}
	}
}