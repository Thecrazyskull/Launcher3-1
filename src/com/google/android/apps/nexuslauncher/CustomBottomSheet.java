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
import android.content.pm.LauncherActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.AppInfo;
import com.android.launcher3.IconCache;
import com.android.launcher3.IconsHandler;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.widget.WidgetsBottomSheet;

import java.util.List;

public class CustomBottomSheet extends WidgetsBottomSheet {
    private FragmentManager mFragmentManager;
    private Launcher mLauncher;

    private ItemInfo mCurrentItem;

    private boolean mLabelChanged;

    public CustomBottomSheet(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomBottomSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mFragmentManager = Launcher.getLauncher(context).getFragmentManager();
        mLauncher = Launcher.getLauncher(context);
    }

    @Override
    protected void handleClose(boolean animate) {
        super.handleClose(animate);

        if (mLabelChanged) {
            IconsHandler.updatePackage(getContext(), mCurrentItem);

            mCurrentItem = null;
            mLabelChanged = false;
        }
    }

    @Override
    public void populateAndShow(final ItemInfo itemInfo) {
        super.populateAndShow(itemInfo);

        final LauncherActivityInfo app = LauncherAppsCompat.getInstance(getContext())
                .resolveActivity(itemInfo.getIntent(), itemInfo.user);
        final IconsHandler handler = LauncherAppState.getInstance(getContext()).getIconsHandler();
        final IconCache cache = LauncherAppState.getInstance(getContext()).getIconCache();
        final Bitmap appliedIcon = handler.getAppliedIconBitmap(cache, app, itemInfo);

        final ImageView appIcon = findViewById(R.id.app_icon);
        final EditText title = findViewById(R.id.title);
        final TextView reset = findViewById(R.id.reset);

        mCurrentItem = itemInfo;

        appIcon.setImageBitmap(appliedIcon);
        appIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ComponentName componentName = null;
                if (itemInfo instanceof AppInfo) {
                    componentName = ((AppInfo) itemInfo).componentName;
                } else if (itemInfo instanceof ShortcutInfo) {
                    componentName = ((ShortcutInfo) itemInfo).intent.getComponent();
                }

                if (componentName != null) {
                    mLauncher.startEdit((ImageView) v, itemInfo, componentName);
                }
            }
        });

        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Drawable resetDrawable = handler.getResetIconDrawable(itemInfo);

                title.setText(app.getLabel());
                appIcon.setImageDrawable(resetDrawable);

                cache.addCustomInfoToDataBase(3, resetDrawable, itemInfo, null);
                setLabelChanged();
            }
        });

        title.setText(itemInfo.title);
        title.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // unused
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // unused
            }

            @Override
            public void afterTextChanged(Editable editable) {
                cache.addCustomInfoToDataBase(2,
                        new BitmapDrawable(getContext().getResources(), appliedIcon),
                        itemInfo, title.getText());
                setLabelChanged();
            }
        });
        ((PrefsFragment) mFragmentManager.findFragmentById(R.id.sheet_prefs))
                .loadForApp(itemInfo);
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

    private void setLabelChanged() {
        mLabelChanged = true;
    }

    public static class PrefsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
        private final static String PREF_HIDE = "pref_app_hide";
        private SwitchPreference mPrefHide;

        private ComponentKey mKey;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.app_edit_prefs);
        }

        public void loadForApp(final ItemInfo itemInfo) {
            Context context = getActivity();
            mKey = new ComponentKey(itemInfo.getTargetComponent(), itemInfo.user);

            mPrefHide = (SwitchPreference) findPreference(PREF_HIDE);

            mPrefHide.setChecked(CustomAppFilter.isHiddenApp(context, mKey));
            mPrefHide.setOnPreferenceChangeListener(this);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            boolean enabled = (boolean) newValue;
            Launcher launcher = Launcher.getLauncher(getActivity());
            switch (preference.getKey()) {
                case PREF_HIDE:
                    CustomAppFilter.setComponentNameState(launcher, mKey, enabled);
                    break;
            }
            return true;
        }
    }
}
