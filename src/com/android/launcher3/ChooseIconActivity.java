/*
 * Copyright (C) 2017 Paranoid Android
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

package com.android.launcher3;

import android.app.ActionBar;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.GridLayoutManager.SpanSizeLookup;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class ChooseIconActivity extends Activity {

    private String mCurrentPackageLabel;
    private String mCurrentPackageName;
    private String mIconPackPackageName;

    private GridLayoutManager mGridLayout;
    private ProgressBar mProgressBar;
    private RecyclerView mIconsGrid;
    private IconCache mIconCache;
    private IconsHandler mIconsHandler;

    private int mIconSize;

    private List<String> mAllIcons;
    private List<String> mMatchingIcons;
    private List<String> mCurrentInstalledApps = new ArrayList<>();
    private GridAdapter mGridAdapter;
    private ActionBar mActionBar;

    private boolean mDefaultIconPack;

    private static WeakReference<ItemInfo> sItemInfoReference;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.search_menu, menu);

        MenuItem search = menu.findItem(R.id.search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(search);
        initQueryTextListener(searchView);

        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.all_icons_view);

        mActionBar = getActionBar();

        mCurrentPackageName = getIntent().getStringExtra("app_package");
        mCurrentPackageLabel = getIntent().getStringExtra("app_label");
        mIconPackPackageName = getIntent().getStringExtra("icon_pack_package");


        // Default icon pack
        if (mIconPackPackageName == null) {
            mIconPackPackageName = getString(R.string.default_iconpack);

            mCurrentInstalledApps.add(0, null);
            mCurrentInstalledApps.addAll(getIntent().getStringArrayListExtra("default_apps_infos"));
            mDefaultIconPack = true;
        }

        PackageManager packageManager = getPackageManager();
        ApplicationInfo iconPackInfo;
        try {
            if (mDefaultIconPack) {
                mActionBar.setSubtitle(mIconPackPackageName);
            } else {
                iconPackInfo = packageManager.getApplicationInfo(mIconPackPackageName, 0);
                mActionBar.setSubtitle(packageManager.getApplicationLabel(iconPackInfo));
            }
        } catch (final PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        mIconCache = LauncherAppState.getInstance(getApplicationContext()).getIconCache();
        mIconsHandler = LauncherAppState.getInstance(getApplicationContext()).getIconsHandler();

        int itemSpacing = getResources().getDimensionPixelSize(R.dimen.grid_item_spacing);
        mIconsGrid = findViewById(R.id.icons_grid);
        mIconsGrid.setHasFixedSize(true);
        mIconsGrid.addItemDecoration(new GridItemSpacer(itemSpacing));
        mGridLayout = new GridLayoutManager(this, 4);
        mIconsGrid.setLayoutManager(mGridLayout);
        mIconsGrid.setAlpha(0.0f);
        mActionBar.hide();

        mProgressBar = findViewById(R.id.icons_grid_progress);

        mIconSize = getResources().getDimensionPixelSize(R.dimen.icon_pack_icon_size);

        if (mDefaultIconPack) {
            setListLoaded();
        } else {
            new LoadIconListTask(this).execute();
        }
    }

    public void setIcons(List<String> allIcons, List<String> matchingIcons) {
        mAllIcons = allIcons;
        mMatchingIcons = matchingIcons;
    }

    public void setListLoaded() {
        mGridAdapter = new GridAdapter(mAllIcons, mMatchingIcons, mCurrentInstalledApps);
        mIconsGrid.setAdapter(mGridAdapter);
        mProgressBar.setVisibility(View.GONE);
        mIconsGrid.animate().alpha(1.0f);
        mActionBar.show();
    }

    public static void setItemInfo(ItemInfo info) {
        sItemInfoReference = new WeakReference<ItemInfo>(info);
    }

    public class GridAdapter extends RecyclerView.Adapter<GridAdapter.ViewHolder>
            implements Filterable {

        private static final int TYPE_MATCHING_HEADER = 0;
        private static final int TYPE_MATCHING_ICONS = 1;
        private static final int TYPE_ALL_HEADER = 2;
        private static final int TYPE_ALL_ICONS = 3;

        private boolean mNoMatchingDrawables;

        private List<String> mAllDrawables = new ArrayList<>();
        private List<String> mDefaultAllDrawables = new ArrayList<>();
        private List<String> mMatchingDrawables = new ArrayList<>();

        void filterList(List<String> filteredAllDrawables, List<String> filteredMatchingDrawables,
                        boolean defaultIconPack) {
            if (defaultIconPack) {
                mDefaultAllDrawables = filteredAllDrawables;
            } else {
                mAllDrawables = filteredAllDrawables;
                mMatchingDrawables = filteredMatchingDrawables;
            }
            notifyDataSetChanged();
        }

        private GridAdapter(List<String> allDrawables, List<String> matchingDrawables,
                            List<String> defaultAllDrawables) {
            if (!mDefaultIconPack) {
                mAllDrawables.add(null);
                mAllDrawables.addAll(allDrawables);
                mMatchingDrawables.add(null);
                mMatchingDrawables.addAll(matchingDrawables);

                mNoMatchingDrawables = matchingDrawables.isEmpty();
                if (mNoMatchingDrawables) {
                    mMatchingDrawables.clear();
                }
            } else {
                mDefaultAllDrawables.addAll(defaultAllDrawables);
            }
            mGridLayout.setSpanSizeLookup(mSpanSizeLookup);
        }

        @Override
        public Filter getFilter() {

            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence charSequence) {

                    return new FilterResults();
                }

                @Override
                protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                    //do nothing
                }
            };
        }

        @Override
        public int getItemViewType(int position) {
            if (mDefaultIconPack) {
                if (position == 0 && mCurrentInstalledApps.get(0) == null) {
                    return TYPE_ALL_HEADER;
                }
                return TYPE_ALL_ICONS;
            }

            if (!mNoMatchingDrawables && position < mMatchingDrawables.size() &&
                    mMatchingDrawables.get(position) == null) {
                return TYPE_MATCHING_HEADER;
            }

            if (!mNoMatchingDrawables && position > TYPE_MATCHING_HEADER &&
                    position < mMatchingDrawables.size()) {
                return TYPE_MATCHING_ICONS;
            }

            if (position == mMatchingDrawables.size()) {
                return TYPE_ALL_HEADER;
            }
            return TYPE_ALL_ICONS;
        }

        @Override
        public int getItemCount() {
            return mDefaultIconPack ? mDefaultAllDrawables.size() : (mAllDrawables.size() + 1);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

            if (viewType == TYPE_MATCHING_HEADER) {
                TextView text = (TextView) getLayoutInflater().inflate(
                        R.layout.all_icons_view_header, null);
                text.setText(R.string.similar_icons);
                return new ViewHolder(text);
            }
            if (viewType == TYPE_ALL_HEADER) {
                TextView text = (TextView) getLayoutInflater().inflate(
                        R.layout.all_icons_view_header, null);
                text.setText(R.string.all_icons);
                return new ViewHolder(text);
            }
            ImageView view = new ImageView(ChooseIconActivity.this);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, mIconSize);
            view.setLayoutParams(params);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, final int position) {
            if (holder.getItemViewType() != TYPE_MATCHING_HEADER
                    && holder.getItemViewType() != TYPE_ALL_HEADER) {
                boolean drawablesMatching = holder.getItemViewType() == TYPE_MATCHING_ICONS;
                final List<String> drawables = drawablesMatching ?
                        mMatchingDrawables : mAllDrawables;
                if (!mDefaultIconPack && position >= drawables.size()) {
                    return;
                }
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Drawable icon = null;
                        if (mDefaultIconPack) {
                            String pkgName  = mDefaultAllDrawables.get(holder.getAdapterPosition());
                            PackageManager pm = getPackageManager();
                            try {
                                icon = pm.getApplicationIcon(pkgName);
                            } catch (PackageManager.NameNotFoundException e) {
                            }
                        } else {
                            icon = mIconsHandler.loadDrawable(
                                    mIconPackPackageName, drawables.get(
                                            holder.getAdapterPosition()), true);
                        }
                        if (icon != null) {
                            ItemInfo info = sItemInfoReference.get();
                            if (info != null) {
                                mIconCache.addCustomInfoToDataBase(1, icon, info,
                                        mCurrentPackageLabel);
                            }
                        }
                        ChooseIconActivity.this.finish();
                    }
                });
                Drawable icon = null;
                if (mDefaultIconPack) {
                    String pkgName = mDefaultAllDrawables.get(position);
                    PackageManager pm = getPackageManager();
                    try {
                        icon = pm.getApplicationIcon(pkgName);
                    } catch (PackageManager.NameNotFoundException e) {
                    }
                } else {
                    String drawable = drawables.get(position);
                    try {
                        icon = mIconsHandler.loadDrawable(mIconPackPackageName, drawable, true);
                    } catch (OutOfMemoryError e) {
                        // time for a new device?
                        e.printStackTrace();
                    }
                }
                ((ImageView) holder.itemView).setImageDrawable(icon);
            }
        }

        private final SpanSizeLookup mSpanSizeLookup = new SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return getItemViewType(position) == TYPE_MATCHING_HEADER
                        || getItemViewType(position) == TYPE_ALL_HEADER ?
                        4 : 1;
            }
        };

        class ViewHolder extends RecyclerView.ViewHolder {
            private ViewHolder(View v) {
                super(v);
            }
        }
    }

    private class GridItemSpacer extends RecyclerView.ItemDecoration {
        private int spacing;

        private GridItemSpacer(int spacing) {
            this.spacing = spacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                 RecyclerView.State state) {
            outRect.top = spacing;
        }
    }

    private void initQueryTextListener(SearchView searchView) {

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (mDefaultIconPack) {
                    IconsSearchUtils.filter(newText, null, mCurrentInstalledApps, mGridAdapter);
                } else {
                    IconsSearchUtils.filter(newText, mMatchingIcons, mAllIcons, mGridAdapter);
                }
                return true;
            }
        });
    }

    private static class LoadIconListTask extends AsyncTask<Void, Void, Void> {
        private IconsHandler iconsHandler;
        private List<String> allIcons;
        private List<String> matchingIcons;
        private String iconPackPackageName;
        private String currentPackageName;
        private WeakReference<ChooseIconActivity> activityReference;

        LoadIconListTask(ChooseIconActivity activity) {
            activityReference = new WeakReference<ChooseIconActivity>(activity);
            iconsHandler = LauncherAppState.getInstance(activity).getIconsHandler();
            iconPackPackageName = activity.mIconPackPackageName;
            currentPackageName = activity.mCurrentPackageName;
        }

        @Override
        public Void doInBackground(Void... params) {
            allIcons = iconsHandler.getAllDrawables(iconPackPackageName);
            matchingIcons = iconsHandler.getMatchingDrawables(currentPackageName);
            return null;
        }

        @Override
        public void onPostExecute(Void aVoid) {
            ChooseIconActivity activity = activityReference.get();
            if (activity != null) {
                activity.setIcons(allIcons, matchingIcons);
                activity.setListLoaded();
            }
        }
    }
}
