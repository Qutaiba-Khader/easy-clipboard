package com.easyclipboard.app.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

import com.easyclipboard.app.R;

/**
 * Settings page. Ports the real preference keys from the original
 * pref_main / pref_advanced / pref_sizes (history, sort, blacklist, singlepaste,
 * cbbutton, ...). Keys match what {@link com.easyclipboard.app.data.ClipRepository}
 * and {@link ClipAdapter} read.
 */
public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }
}
