package com.android.launcher3;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.pageindicators.PageIndicator;
import com.google.android.apps.nexuslauncher.NexusLauncherActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Anass on 18-2-2018.
 */

public class PreviewWorkspaceActivityBase extends NexusLauncherActivity {
    // Keep track of current packages on homescreen
    private List<String> mPackages = new ArrayList<>();

    private Hotseat mHotseat;
    private View mRootView;
    private Workspace mWorkspace;

    private boolean mRecreating;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Should never hit this, but if we do we'll be good
        if (mRootView == null) {
            initLauncher();
        }

        setContentView(mRootView);
    }

    @Override
    public void recreate() {
        mRecreating = true;
        super.recreate();
    }

    @Override
    public void onPause() {
        super.onPause();
         if (!mRecreating) {
            finish();
        } else {
            mRecreating = false;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    public void overrideTheme(boolean isDark, boolean supportsDarkText) {
        if (isDark) {
            setTheme(R.style.PreviewWorkspaceThemeDark);
        } else if (supportsDarkText) {
            setTheme(R.style.PreviewWorkspaceThemeDarkText);
        }
    }

    @Override
    public int getCurrentWorkspaceScreen() {
        return 0;
    }

    @Override
    public void startBinding() {
        // This is called before onCreate because of super.onCreate
        if (mRootView == null) {
            initLauncher();
        }

        // Clear the workspace because it's going to be rebound
        mWorkspace.clearDropTargets();
        mWorkspace.removeAllWorkspaceScreens();

        if (mHotseat != null) {
            mHotseat.resetLayout();
        }
    }

    @Override
    public void bindItems(final List<ItemInfo> items, final boolean forceAnimateIcons) {
        if (!mPackages.isEmpty()) {
            mPackages.clear();
        }

        // Get the list of added items and intersect them with the set of items here
        int N = items.size();
        for (int i = 0; i < N; i++) {
            final ItemInfo item = items.get(i);

            // Short circuit if we are loading dock items for a configuration which has no dock
            if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                if (mHotseat == null) {
                    continue;
                }
            }

            final View view;
            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT: {
                    ShortcutInfo info = (ShortcutInfo) item;
                    String packageName = item.getTargetComponent().getPackageName();
                    if (!mPackages.contains(packageName)) {
                        mPackages.add(item.getTargetComponent().getPackageName());
                    }
                    view = createShortcut((ViewGroup) mWorkspace.getChildAt(
                            mWorkspace.getCurrentPage()), info);
                    break;
                }
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER: {
                    view = FolderIcon.fromXml(R.layout.folder_icon, this,
                            (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentPage()),
                            (FolderInfo) item, true);
                    break;
                }
                case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET: {
                    view = inflateAppWidget((LauncherAppWidgetInfo) item);
                    if (view == null) {
                        continue;
                    }
                    break;
                }
                default:
                    throw new RuntimeException("Invalid Item Type");
            }
            mWorkspace.addInScreenFromBind(view, item);
        }
        mWorkspace.requestLayout();
    }

    @Override
    public Hotseat getHotseat() {
        if (mRootView == null) {
            initLauncher();
        }
        return mHotseat;
    }

    @Override
    public Workspace getWorkspace() {
        if (mRootView == null) {
            initLauncher();
        }
        return mWorkspace;
    }

    public View getRootView() {
        return mRootView;
    }

    public List<String> getHomescreenPackages() {
        return mPackages;
    }

    private void initLauncher() {
        loadViews();
    }

    // Make sure to call super when overriding this method!
    public void loadViews() {
        int hotseatMargin = getResources().getDimensionPixelSize(
                R.dimen.qsb_hotseat_bottom_margin_hw);
        int hotseatSize = getResources().getDimensionPixelSize(
                R.dimen.dynamic_grid_hotseat_size);
        int hotseatSpringSize = getResources().getDimensionPixelSize(
                R.dimen.dynamic_grid_min_spring_loaded_space);
        int hotseatPadding = hotseatSpringSize / 4;

        mRootView = LayoutInflater.from(this).inflate(R.layout.workspace_preview, null);
        mHotseat = mRootView.findViewById(R.id.hotseat);

        mWorkspace = mRootView.findViewById(R.id.workspace);
        mWorkspace.bindAndInitFirstWorkspaceScreen(null);

        int marginBottom = ((hotseatMargin + hotseatSize) - hotseatSpringSize) + hotseatPadding;

        PageIndicator pageIndicator = mRootView.findViewById(R.id.page_indicator);
        pageIndicator.getCaretDrawable().setCaretProgress(-1f);
        FrameLayout.LayoutParams pageIndicatorParams = (FrameLayout.LayoutParams)
                pageIndicator.getLayoutParams();
        pageIndicatorParams.bottomMargin = marginBottom;

        ArrayList<Long> array = new ArrayList<>();
        array.add(0L);
        bindAddScreens(array);

        InvariantDeviceProfile iprofile = LauncherAppState.getInstance(this)
                .getInvariantDeviceProfile();

        CellLayout screen = mWorkspace.getScreenWithId(Workspace.FIRST_SCREEN_ID);
        screen.setGridSize(iprofile.numRows++, iprofile.numColumns);

        LauncherRootView launcherRoot = mRootView.findViewById(R.id.launcher);
        launcherRoot.setShouldConsumeTouches(true);
    }
}
