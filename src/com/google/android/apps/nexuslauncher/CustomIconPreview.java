package com.google.android.apps.nexuslauncher;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.PreviewWorkspaceActivityBase;
import com.android.launcher3.R;
import com.android.launcher3.util.PackageManagerHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Anass on 26-2-2018.
 */

public class CustomIconPreview extends PreviewWorkspaceActivityBase {

    private RecyclerViewAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPause() {
        resetNumRows();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        resetNumRows();
        super.onDestroy();
    }

    @Override
    public void loadViews() {
        super.loadViews();
        loadRecyclerView();
    }

    private void loadRecyclerView() {
        View rootView = getRootView();

        // Should not happen
        if (rootView == null) {
            Log.w(TAG, "launcher view not loaded yet");
            return;
        }

        mAdapter = new RecyclerViewAdapter(this);

        RecyclerView view = rootView.findViewById(R.id.recycler_view);
        LinearLayoutManager manager = new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false);
        view.setLayoutManager(manager);
        view.setAdapter(mAdapter);
    }

    private void resetNumRows() {
        // Reset numRows to original value
        InvariantDeviceProfile iprofile = LauncherAppState.getInstance(this)
                .getInvariantDeviceProfile();
        iprofile.numRows = iprofile.numRowsOriginal;
    }

    private class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder> {
        private static final int NORMAL_VIEW_TYPE = 0;
        private static final int MARKET_DIVIDER_VIEW_TYPE = 1;
        private List<String> providers;
        private Context context;
        private PackageManager pm;

        RecyclerViewAdapter(Context context) {
            this.context = context;
            pm = context.getPackageManager();
            HashMap<String, CharSequence> packList = CustomIconUtils.getPackProviders(context);
            providers = new ArrayList<>(packList.keySet());
            Collections.sort(providers, new Comparator<String>() {
                @Override
                public int compare(String s, String t1) {
                    try {
                        ApplicationInfo info1 = pm.getApplicationInfo(s, 0);
                        String label1 = pm.getApplicationLabel(info1).toString();

                        ApplicationInfo info2 = pm.getApplicationInfo(t1, 0);
                        String label2 = pm.getApplicationLabel(info2).toString();

                        return label1.compareToIgnoreCase(label2);
                    } catch (PackageManager.NameNotFoundException e) {
                    }

                    return 0;
                }
            });

            // Insert default icon pack
            providers.add(0, null);

            // Insert market item & divider
            providers.add(null);
            providers.add(null);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == providers.size() - 2 && providers.get(position) == null){
                return MARKET_DIVIDER_VIEW_TYPE;
            }
            return NORMAL_VIEW_TYPE;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            int viewId = R.layout.icon_pack_item;
            if (viewType == MARKET_DIVIDER_VIEW_TYPE) {
                viewId = R.layout.market_divider;
            }
            View view = LayoutInflater.from(context).inflate(viewId, null);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            if (getItemViewType(position) == MARKET_DIVIDER_VIEW_TYPE) {
                return;
            }

            try {
                String name = providers.get(position);
                String currentPack = CustomIconUtils.getCurrentPack(context);

                // Default icon pack
                if (position == 0 && name == null) {
                    boolean selected = TextUtils.isEmpty(currentPack);
                    holder.icon.setImageResource(R.drawable.ic_framework);
                    holder.label.setText(R.string.icon_pack_default);
                    holder.label.setSelected(selected);
                    holder.selected.setVisibility(selected ? View.VISIBLE : View.GONE);
                    return;
                }

                // Market
                if (position == providers.size() - 1 && name == null) {
                    holder.icon.setImageResource(R.drawable.ic_market);
                    holder.label.setText(R.string.market);
                    holder.label.setSelected(false);
                    return;
                }

                boolean selected = name.equals(currentPack);
                Drawable icon = pm.getApplicationIcon(name);
                ApplicationInfo info = pm.getApplicationInfo(name, 0);

                holder.icon.setImageDrawable(icon);
                holder.label.setText(pm.getApplicationLabel(info));
                holder.label.setSelected(selected);
                holder.selected.setVisibility(selected ? View.VISIBLE : View.GONE);
            } catch (PackageManager.NameNotFoundException e) {
            }
        }

        @Override
        public int getItemCount() {
            return providers.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            ImageView icon;
            TextView label;
            View selected;

            ViewHolder(View view) {
                super(view);
                view.setOnClickListener(this);
                icon = view.findViewById(R.id.app_icon);
                label = view.findViewById(R.id.app_title);
                selected = view.findViewById(R.id.selected_indicator);
            }

            @Override
            public void onClick(final View v) {
                int position = getAdapterPosition();
                if (position == providers.size() - 1) {
                    Intent intent = PackageManagerHelper.getMarketSearchIntent(context,
                            "icon pack");
                    context.startActivity(intent);
                } else {
                    final String name = providers.get(position);
                    new ApplyIconPackTask(context, name).execute();
                }
            }
        }
    }

    private static class ApplyIconPackTask extends AsyncTask<Void, Void, Void> {
        private ProgressDialog dialog;
        private String iconPack;
        private WeakReference<Context> contextReference;

        ApplyIconPackTask(Context context, String name) {
            contextReference = new WeakReference<>(context);
            iconPack = name;
        }

        @Override
        public void onPreExecute() {
            Context context = contextReference.get();
            if (context != null) {
                dialog = new ProgressDialog(context);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setMessage(context.getString(R.string.state_loading));
                dialog.show();
            }
        }

        @Override
        public Void doInBackground(Void... params) {
            Context context = contextReference.get();
            if (context != null) {
                //if (!CustomIconUtils.getCurrentPack(context).equals(iconPack)) {
                    //CustomIconUtils.applyIconPack(context, iconPack);
                //}
            }
            return null;
        }

        @Override
        public void onPostExecute(Void param) {
            Context context = contextReference.get();
            if (dialog != null) {
                dialog.dismiss();
            }

            if (context != null) {
                if (context instanceof CustomIconPreview) {
                    ((CustomIconPreview) context).mAdapter.notifyDataSetChanged();
                    ((CustomIconPreview) context).setShouldReload(true);
                }
            }
        }
    }
}
