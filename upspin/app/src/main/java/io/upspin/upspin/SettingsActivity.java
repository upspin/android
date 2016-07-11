package io.upspin.upspin;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

/**
 * A {@link PreferenceActivity} that presents a set of application settings.
 */
public class SettingsActivity extends AppCompatPreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new PreferenceFrag()).commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();  //return to caller
            return true;
        }
        return false;
    }

    public static class PreferenceFrag extends PreferenceFragment {
        private SharedPreferences mPrefs;
        private SharedPreferences.OnSharedPreferenceChangeListener mListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
                PreferenceFrag.this.refresh();
            }
        };

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            mPrefs = getPreferenceScreen().getSharedPreferences();
            mPrefs.registerOnSharedPreferenceChangeListener(mListener);

            ActionBar b = ((AppCompatPreferenceActivity) getActivity()).getSupportActionBar();
            b.setHomeButtonEnabled(true);
            b.setDisplayHomeAsUpEnabled(true);
            b.setDisplayShowHomeEnabled(true);
            b.setTitle(getString(R.string.title_activity_settings));
        }

        public void onDestroy() {
            super.onDestroy();
            mPrefs.unregisterOnSharedPreferenceChangeListener(mListener);
        }

        public void refresh() {
            setSummaryToValue(getString(R.string.username_key));
            setSummaryToValue(getString(R.string.pubkey_key));
            setSummaryToValue(getString(R.string.userserver_key));
            setSummaryToValue(getString(R.string.storeserver_key));
            setSummaryToValue(getString(R.string.dirserver_key));
            String strPrivKeyKey = getString(R.string.privkey_key);
            String privKey = mPrefs.getString(strPrivKeyKey, "");
            if (!privKey.equals("")) {
                EditTextPreference pref = (EditTextPreference) findPreference(strPrivKeyKey);
                pref.setSummary(getString(R.string.privkey_set));
            }
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            refresh();
        }

        private void setSummaryToValue(String prefKey) {
            String prefValue = mPrefs.getString(prefKey, "");
            if (prefValue != null && !prefValue.equals("")) {
                EditTextPreference pref = (EditTextPreference) findPreference(prefKey);
                pref.getEditText().setEllipsize(TextUtils.TruncateAt.MIDDLE);
                pref.setSummary(prefValue);
            }
        }
    }
}

