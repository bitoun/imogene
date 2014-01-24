package org.imogene.android.app;

import greendroid.app.GDPreferenceActivity;

import java.util.UUID;

import org.imogene.android.common.entity.SyncHistory;
import org.imogene.android.database.sqlite.ImogOpenHelper;
import org.imogene.android.preference.BaseDialogPreference;
import org.imogene.android.preference.BaseDialogPreference.OnDialogCloseListener;
import org.imogene.android.preference.Preferences;
import org.imogene.android.template.R;
import org.imogene.android.util.database.DatabaseUtils;
import org.imogene.android.util.ntp.NTPClock;
import org.imogene.android.util.os.BaseAsyncTask;

import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.text.TextUtils;

public class HiddenSettings extends GDPreferenceActivity implements OnDialogCloseListener, OnPreferenceClickListener,
		OnPreferenceChangeListener {

	private BaseDialogPreference mDeleteDatabase;
	private BaseDialogPreference mDeleteSyncHistory;
	private BaseDialogPreference mSyncTerminal;
	private EditTextPreference mNtpHost;
	private Preference mNtpOffset;

	private Preferences mPreferences;

	private SntpOffsetTask mSntpOffsetTask;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.hidden_settings);

		mPreferences = Preferences.getPreferences(this);

		mDeleteDatabase = (BaseDialogPreference) findPreference(Preferences.DELETE_DATABASE);
		mDeleteSyncHistory = (BaseDialogPreference) findPreference(Preferences.DELETE_HISTORY);
		mSyncTerminal = (BaseDialogPreference) findPreference(Preferences.SYNC_TERMINAL);
		mNtpHost = (EditTextPreference) findPreference(Preferences.NTP_HOST);
		mNtpOffset = findPreference(Preferences.NTP_OFFSET);

		mNtpOffset.setOnPreferenceClickListener(this);

		mDeleteDatabase.setOnDialogCloseListener(this);
		mDeleteSyncHistory.setOnDialogCloseListener(this);
		mSyncTerminal.setOnDialogCloseListener(this);

		mSntpOffsetTask = (SntpOffsetTask) getLastNonConfigurationInstance();
		if (mSntpOffsetTask != null) {
			mSntpOffsetTask.setCallback(this);
		}

		updateNtpOffsetVisibility();
		updateSyncHardwareSummary();
		updateNtpOffsetSummary();
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		mSntpOffsetTask.setCallback(null);
		return mSntpOffsetTask;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference == mNtpOffset) {
			executeNtpOffsetUpdate();
			return true;
		}
		return false;
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (preference == mNtpHost) {
			updateNtpOffsetVisibility();
			return true;
		}
		return false;
	}

	@Override
	public void onDialogClosed(Preference preference, boolean positiveResult) {
		if (preference == mDeleteDatabase) {
			if (positiveResult) {
				new Thread() {
					@Override
					public void run() {
						DatabaseUtils.deleteAll(HiddenSettings.this);
					}
				}.start();
			}
		} else if (preference == mDeleteSyncHistory) {
			if (positiveResult) {
				new Thread() {
					@Override
					public void run() {
						ImogOpenHelper.getHelper().delete(SyncHistory.Columns.TABLE_NAME, null, null);
					};
				}.start();
			}
		} else if (preference == mSyncTerminal) {
			if (positiveResult) {
				mPreferences.setSyncTerminal(UUID.randomUUID().toString());
				updateSyncHardwareSummary();
			}
		}
	}

	private void updateNtpOffsetVisibility() {
		mNtpOffset.setEnabled(!TextUtils.isEmpty(mNtpHost.getText()));
	}

	private void updateSyncHardwareSummary() {
		mSyncTerminal.setSummary(mPreferences.getSyncTerminal());
	}

	private void updateNtpOffsetSummary() {
		mNtpOffset.setSummary(String.valueOf(mPreferences.getNtpOffset()));
	}

	private void executeNtpOffsetUpdate() {
		if (mSntpOffsetTask == null || mSntpOffsetTask.isFinished()) {
			mSntpOffsetTask = new SntpOffsetTask(this);
			mSntpOffsetTask.setCallback(this);
			mSntpOffsetTask.execute(mNtpHost.getText());
		}
	}

	private static class SntpOffsetTask extends BaseAsyncTask<HiddenSettings, String, Void, Boolean> {

		private final Context context;

		public SntpOffsetTask(Context context) {
			this.context = context.getApplicationContext();
		}

		@Override
		protected void onPreExecute() {
			if (callback != null) {
				callback.mNtpOffset.setEnabled(false);
			}
		}

		@Override
		protected Boolean doInBackground(String... params) {
			return NTPClock.getInstance(context).updateOffsetSync(params[0]);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			if (callback != null) {
				callback.mNtpOffset.setEnabled(true);
				callback.updateNtpOffsetSummary();
			}
		}
	}

}