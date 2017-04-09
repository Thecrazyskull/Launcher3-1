/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.nexuslauncher;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.widget.WidgetsBottomSheet;

public class CustomBottomSheet extends WidgetsBottomSheet {
    private FragmentManager mFragmentManager;
    private Launcher mLauncher;

    public CustomBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mFragmentManager = Launcher.getLauncher(context).getFragmentManager();
        mLauncher = Launcher.getLauncher(context);
    }

    @Override
    public void populateAndShow(ItemInfo itemInfo) {
        super.populateAndShow(itemInfo);
        ((TextView) findViewById(R.id.title)).setText(itemInfo.title);
        ((PrefsFragment) mFragmentManager.findFragmentById(R.id.sheet_prefs))
                .loadForApp(this, mLauncher, itemInfo);
    }

    @Override
    public void onDetachedFromWindow() {
        Fragment pf = mFragmentManager.findFragmentById(R.id.sheet_prefs);
        if (pf != null) {
            mFragmentManager.beginTransaction().remove(pf).commitAllowingStateLoss();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWidgetsBound() {
    }

    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    public static class PrefsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
        private final static String PREF_PACK = "pref_app_icon_pack";
        private final static String PREF_HIDE = "pref_app_hide";
        private Preference mPrefPack;
        private SwitchPreference mPrefHide;

        private ComponentKey mKey;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.app_edit_prefs);
        }

        public void loadForApp(final CustomBottomSheet sheet, final Launcher launcher,
                               final ItemInfo itemInfo) {
            Context context = getActivity();
            mKey = new ComponentKey(itemInfo.getTargetComponent(), itemInfo.user);

            mPrefPack = findPreference(PREF_PACK);
            mPrefHide = (SwitchPreference) findPreference(PREF_HIDE);

            PackageManager pm = context.getPackageManager();

            String defaultPack = context.getString(R.string.default_iconpack);
            String iconPack = PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getString(Utilities.KEY_ICON_PACK, defaultPack);

            try {
                ApplicationInfo info = pm.getApplicationInfo(iconPack, 0);
                mPrefPack.setSummary(pm.getApplicationLabel(info));
            } catch (PackageManager.NameNotFoundException e) {
                mPrefPack.setSummary(defaultPack);
            }

            mPrefPack.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    sheet.close(true);
                    ComponentName componentName = null;
                    if (itemInfo instanceof com.android.launcher3.AppInfo) {
                        componentName = ((com.android.launcher3.AppInfo) itemInfo).componentName;
                    } else if (itemInfo instanceof ShortcutInfo) {
                        componentName = ((ShortcutInfo) itemInfo).intent.getComponent();
                    }

                    if (componentName != null) {
                        launcher.startEdit(itemInfo, componentName);
                    }
                    return false;
                }
            });

            mPrefHide.setChecked(CustomAppFilter.isHiddenApp(context, mKey));
            mPrefHide.setOnPreferenceChangeListener(this);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            boolean enabled = (boolean) newValue;
            Launcher launcher = Launcher.getLauncher(getActivity());
            switch (preference.getKey()) {
                case PREF_PACK:
                    CustomIconProvider.setAppState(launcher, mKey, enabled);
                    CustomIconUtils.reloadIconByKey(launcher, mKey);
                    break;
                case PREF_HIDE:
                    CustomAppFilter.setComponentNameState(launcher, mKey, enabled);
                    break;
            }
            return true;
        }
    }
}
