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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Process;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.graphics.FixedScaleDrawable;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.graphics.IconNormalizer;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class IconsHandler {

    private static final String TAG = "IconsHandler";
    private static final String FRONT = "_front";
    private static final String BACK = "_back";

    private static String[] LAUNCHER_INTENTS = new String[] {
        "com.fede.launcher.THEME_ICONPACK",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "org.adw.launcher.THEMES",
        "org.adw.launcher.icons.ACTION_PICK_ICON"
    };

    private Map<String, IconPackInfo> mIconPacks = new HashMap<>();
    private Map<String, String> mAppFilterDrawables = new HashMap<>();
    private List<Bitmap> mBackImages = new ArrayList<>();
    private List<String> mDrawables = new ArrayList<>();

    private Bitmap mFrontImage;
    private Bitmap mMaskImage;

    private Resources mCurrentIconPackRes;
    private Resources mOriginalIconPackRes;
    private String mIconPackPackageName;

    private Context mContext;
    private PackageManager mPackageManager;
    private String mDefaultIconPack;

    private float mFactor = 1.0f;

    public IconsHandler(Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();

        mDefaultIconPack = context.getString(R.string.default_iconpack);

        String iconPack = PreferenceManager.getDefaultSharedPreferences(mContext)
                    .getString(Utilities.KEY_ICON_PACK, mDefaultIconPack);
        loadAvailableIconPacks();
        loadIconPack(iconPack, false);
    }

    private void loadIconPack(String packageName, boolean fallback) {
        mIconPackPackageName = packageName;
        if (!fallback) {
            mAppFilterDrawables.clear();
            mBackImages.clear();
            clearCache();
        } else {
            mDrawables.clear();
        }
        mFactor = 1.0f;

        if (isDefaultIconPack()) {
            return;
        }

        XmlPullParser xpp = null;

        try {
            mOriginalIconPackRes = mPackageManager.getResourcesForApplication(mIconPackPackageName);
            mCurrentIconPackRes = mOriginalIconPackRes;
            int appfilterid = mOriginalIconPackRes.getIdentifier("appfilter", "xml", mIconPackPackageName);
            if (appfilterid > 0) {
                xpp = mOriginalIconPackRes.getXml(appfilterid);
            }

            if (xpp != null) {
                int eventType = xpp.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        if (!fallback & xpp.getName().equals("iconback")) {
                            for (int i = 0; i < xpp.getAttributeCount(); i++) {
                                if (xpp.getAttributeName(i).startsWith("img")) {
                                    String drawableName = xpp.getAttributeValue(i);
                                    Bitmap iconback = loadBitmap(drawableName);
                                    if (iconback != null) {
                                        mBackImages.add(iconback);
                                    }
                                }
                            }
                        } else if (!fallback && xpp.getName().equals("iconmask")) {
                            if (xpp.getAttributeCount() > 0 && xpp.getAttributeName(0).equals("img1")) {
                                String drawableName = xpp.getAttributeValue(0);
                                mMaskImage = loadBitmap(drawableName);
                            }
                        } else if (!fallback && xpp.getName().equals("iconupon")) {
                            if (xpp.getAttributeCount() > 0 && xpp.getAttributeName(0).equals("img1")) {
                                String drawableName = xpp.getAttributeValue(0);
                                mFrontImage = loadBitmap(drawableName);
                            }
                        } else if (!fallback && xpp.getName().equals("scale")) {
                            if (xpp.getAttributeCount() > 0 && xpp.getAttributeName(0).equals("factor")) {
                                mFactor = Float.valueOf(xpp.getAttributeValue(0));
                            }
                        }
                        if (xpp.getName().equals("item")) {
                            String componentName = null;
                            String drawableName = null;

                            for (int i = 0; i < xpp.getAttributeCount(); i++) {
                                if (xpp.getAttributeName(i).equals("component")) {
                                    componentName = xpp.getAttributeValue(i);
                                } else if (xpp.getAttributeName(i).equals("drawable")) {
                                    drawableName = xpp.getAttributeValue(i);
                                }
                            }
                            if (fallback && getIdentifier(packageName, drawableName, true) > 0
                                    && !mDrawables.contains(drawableName)) {
                                mDrawables.add(drawableName);
                            }
                            if (!fallback && componentName != null && drawableName != null &&
                                    !mAppFilterDrawables.containsKey(componentName)) {
                                mAppFilterDrawables.put(componentName, drawableName);
                            }
                        }
                    }
                    eventType = xpp.next();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing appfilter.xml " + e);
        }
    }

    public List<String> getAllDrawables(final String packageName) {
        loadAllDrawables(packageName);
        Collections.sort(mDrawables, new Comparator<String>() {
            @Override
            public int compare(String drawable, String drawable2) {
                return drawable.compareToIgnoreCase(drawable2);
            }
        });

        return mDrawables;
    }

    Drawable getIconFromHandler(LauncherActivityInfo info) {
        return getDrawableIconForPackage(info.getComponentName());
    }

    private void loadAllDrawables(String packageName) {
        mDrawables.clear();
        XmlPullParser xpp;
        try {
            Resources res = mPackageManager.getResourcesForApplication(packageName);
            mCurrentIconPackRes = res;
            int resource = res.getIdentifier("drawable", "xml", packageName);
            if (resource < 0) {
                return;
            }
            xpp = res.getXml(resource);
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if (xpp.getName().equals("item")) {
                        String drawableName = xpp.getAttributeValue(null, "drawable");
                        if (!mDrawables.contains(drawableName) &&
                                getIdentifier(packageName, drawableName, true) > 0) {
                            mDrawables.add(drawableName);
                        }
                    }
                }
                eventType = xpp.next();
            }
        } catch (Exception e) {
            Log.i(TAG, "Error parsing drawable.xml for package " + packageName + " trying appfilter now");
            // fallback onto appfilter if drawable xml fails
            loadIconPack(packageName, true);
        }
    }

    public String getCurrentIconPackPackageName() {
        return mIconPackPackageName;
    }

    public boolean isDefaultIconPack() {
        return mIconPackPackageName.equalsIgnoreCase(mDefaultIconPack);
    }

    public List<String> getMatchingDrawables(String packageName) {
        List<String> matchingDrawables = new ArrayList<>();
        ApplicationInfo info = null;
        try {
            info = mPackageManager.getApplicationInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        String packageLabel = (info != null ? mPackageManager.getApplicationLabel(info).toString()
                : packageName).replaceAll("[^a-zA-Z]", "").toLowerCase().trim();
        for (String drawable : mDrawables) {
            if (drawable == null) continue;
            String filteredDrawable = drawable.replaceAll("[^a-zA-Z]", "").toLowerCase().trim();
            if (filteredDrawable.length() > 2 && (packageLabel.contains(filteredDrawable)
                    || filteredDrawable.contains(packageLabel))) {
                matchingDrawables.add(drawable);
            }
        }
        return matchingDrawables;
    }

    private int getIdentifier(String packageName, String drawableName, boolean currentIconPack) {
        if (drawableName == null) {
            return 0;
        }
        if (packageName == null) {
            packageName = mIconPackPackageName;
        }
        return (!currentIconPack ? mOriginalIconPackRes : mCurrentIconPackRes).getIdentifier(
                drawableName, "drawable", packageName);
    }

    public Drawable loadDrawable(String packageName, String drawableName, boolean currentIconPack) {
        if (packageName == null) {
            packageName = mIconPackPackageName;
        }
        int id = getIdentifier(packageName, drawableName, currentIconPack);
        if (id > 0) {
            return (!currentIconPack ? mOriginalIconPackRes : mCurrentIconPackRes)
                    .getDrawable(id, mContext.getTheme());
        }
        return null;
    }

    private Bitmap loadBitmap(String drawableName) {
        Drawable bitmap = loadDrawable(null, drawableName, true);
        if (bitmap != null && bitmap instanceof BitmapDrawable) {
            return ((BitmapDrawable) bitmap).getBitmap();
        }
        return null;
    }

    private Drawable getDefaultAppDrawable(ComponentName componentName) {
        Drawable drawable = null;
        try {
            drawable = mPackageManager.getApplicationIcon(mPackageManager.getApplicationInfo(
                    componentName.getPackageName(), 0));
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Unable to find component " + componentName.toString() + e);
        }
        if (drawable == null) {
            return null;
        }

        return generateAdaptiveIcon(componentName, drawable);
    }

    public void switchIconPacks(String packageName, boolean update) {
        if (packageName.equals(mIconPackPackageName) && !update) {
            packageName = mDefaultIconPack;
        }
        if (packageName.equals(mDefaultIconPack) || mIconPacks.containsKey(packageName)) {
            new IconPackLoader(this, packageName).execute();
        }
    }

    private Drawable getDrawableIconForPackage(ComponentName componentName) {
        Drawable cachedIcon;

        if (!mBackImages.isEmpty()) {
            Drawable cachedIconFront = cacheGetDrawable(componentName.toString(), FRONT);
            Drawable cachedIconBack = cacheGetDrawable(componentName.toString(), BACK);
            if (cachedIconFront == null || cachedIconBack == null) {
                cachedIcon = null;
            } else {
                cachedIcon = new AdaptiveIconDrawable(cachedIconBack, cachedIconFront);
            }
        } else {
            cachedIcon = cacheGetDrawable(componentName.toString(), null);
        }
        if (cachedIcon != null) {
            return cachedIcon;
        }

        String drawableName = mAppFilterDrawables.get(componentName.toString());
        Drawable drawable = loadDrawable(null, drawableName, false);
        if (drawable != null) {
            cacheStoreDrawable(componentName.toString(), null, drawable);
            return drawable;
        }

        return getDefaultAppDrawable(componentName);
    }

    // Get the first icon pack parsed icon for reset purposes
    Drawable getResetIconDrawable(ItemInfo info) {
        return getDrawableIconForPackage(info.getTargetComponent());
    }

    // Get the applied icon
    Bitmap getAppliedIconBitmap(IconCache iconCache, LauncherActivityInfo app,
                                ItemInfo info) {
        final Drawable defaultIcon = new BitmapDrawable(mContext.getResources(),
                iconCache.getNonNullIcon(iconCache.getCacheEntry(app), info.user));
        return LauncherIcons.createBadgedIconBitmap(defaultIcon, info.user, mContext,
                Build.VERSION.SDK_INT);
    }

    private Drawable createAdaptiveIcon(Drawable drawable) {
        IconNormalizer normalizer = IconNormalizer.getInstance(mContext);

        boolean[] outShape = new boolean[1];
        AdaptiveIconDrawable dr = (AdaptiveIconDrawable)
                mContext.getDrawable(R.drawable.adaptive_icon_drawable_wrapper).mutate();
        dr.setBounds(0, 0, 1, 1);
        float scale = normalizer.getScale(drawable, null, dr.getIconMask(), outShape);
        if (!outShape[0]) {
            return LauncherIcons.wrapToAdaptiveIconDrawable(mContext, drawable, scale);
        }
        return null;
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private Drawable generateAdaptiveIcon(ComponentName cn, Drawable drawable) {
        if (mBackImages.isEmpty()) {
            // Make sure all icons are adaptive if no back is provided
            if (!(drawable instanceof AdaptiveIconDrawable)) {
                Drawable d = createAdaptiveIcon(drawable);
                if (d != null) {
                    return d;
                }
            }
            return drawable;
        }

        IconNormalizer normalizer = IconNormalizer.getInstance(mContext);

        // Convert passed icon to bitmap
        Bitmap defaultBitmap = drawableToBitmap(drawable);

        // Load back image
        Random random = new Random();
        int id = random.nextInt(mBackImages.size());
        Bitmap backImage = mBackImages.get(id);
        int w = backImage.getWidth();
        int h = backImage.getHeight();

        // Create final bitmap
        Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(backImage, 0, 0, null);

        // Check if we need a custom factor for transparent back images
        try {
            if (!normalizer.isTransparentBitmap(backImage)) {
                mFactor = 0.7f;
            }
        } catch (Exception e) {
            Log.w(TAG, "failed to check if bitmap is transparent", e);
        }

        // Scale the passed icon
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(defaultBitmap,
                (int) (w * mFactor), (int) (h * mFactor), false);
        Drawable scaledDrawable = new BitmapDrawable(mContext.getResources(), scaledBitmap);

        // Draw mask image
        Bitmap mutableMask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas maskCanvas = new Canvas(mutableMask);
        Bitmap targetBitmap = mMaskImage == null ? mutableMask : mMaskImage;
        maskCanvas.drawBitmap(targetBitmap, 0, 0, new Paint());

        // Draw the scaled passed icon on top of the back image
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST));
        canvas.drawBitmap(scaledBitmap, (w - scaledBitmap.getWidth()) / 2,
                (h - scaledBitmap.getHeight()) / 2, null);
        canvas.drawBitmap(mutableMask, 0, 0, paint);

        // Draw front image, if available
        if (mFrontImage != null) {
            canvas.drawBitmap(mFrontImage, 0, 0, null);
        }

        // Create scaled final drawable for adaptive icon use
        float scale = normalizer.getScale(drawable, null, null, null);
        FixedScaleDrawable fsd = new FixedScaleDrawable();
        fsd.setDrawable(scaledDrawable);
        fsd.setScale(scale);

        // Create the final adaptive icon
        Drawable backImageDrawable = new BitmapDrawable(mContext.getResources(), backImage);
        AdaptiveIconDrawable adaptiveIconDrawable = new AdaptiveIconDrawable(backImageDrawable, fsd);

        // Save back & front drawables
        cacheStoreDrawable(cn.toString(), BACK,  backImageDrawable);
        cacheStoreDrawable(cn.toString(), FRONT, fsd);

        return adaptiveIconDrawable;
    }

    public Pair<List<String>, List<String>> getAllIconPacks() {
        //be sure to update the icon packs list
        loadAvailableIconPacks();

        List<String> iconPackNames = new ArrayList<>();
        List<String> iconPackLabels = new ArrayList<>();
        List<IconPackInfo> iconPacks = new ArrayList<IconPackInfo>(mIconPacks.values());
        Collections.sort(iconPacks, new Comparator<IconPackInfo>() {
            @Override
            public int compare(IconPackInfo info, IconPackInfo info2) {
                return info.label.toString().compareToIgnoreCase(info2.label.toString());
            }
        });
        for (IconPackInfo info : iconPacks) {
            iconPackNames.add(info.packageName);
            iconPackLabels.add(info.label.toString());
        }
        return new Pair<>(iconPackNames, iconPackLabels);
    }

    private void loadAvailableIconPacks() {
        List<ResolveInfo> launcherActivities = new ArrayList<>();
        mIconPacks.clear();
        for (String i : LAUNCHER_INTENTS) {
            launcherActivities.addAll(mPackageManager.queryIntentActivities(
                    new Intent(i), PackageManager.GET_META_DATA));
        }
        for (ResolveInfo ri : launcherActivities) {
            String packageName = ri.activityInfo.packageName;
            IconPackInfo info = new IconPackInfo(ri, mPackageManager);
            mIconPacks.put(packageName, info);
        }
    }

    private boolean isDrawableInCache(String key, String secondaryName) {
        File drawableFile = cacheGetFileName(key, secondaryName);
        return drawableFile.isFile();
    }

    private void cacheStoreDrawable(String key, String secondaryName, Drawable drawable) {
        if (isDrawableInCache(key, secondaryName)) return;
        File drawableFile = cacheGetFileName(key, secondaryName);
        Bitmap bitmap = drawableToBitmap(drawable);
        try (FileOutputStream fos = new FileOutputStream(drawableFile)) {
            bitmap.compress(CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "Unable to store drawable in cache " + e);
        }
    }

    private Drawable cacheGetDrawable(String key, String secondaryName) {
        if (!isDrawableInCache(key, secondaryName)) {
            return null;
        }

        try (FileInputStream fis = new FileInputStream(cacheGetFileName(key, secondaryName))) {
            BitmapDrawable drawable =
                    new BitmapDrawable(mContext.getResources(), BitmapFactory.decodeStream(fis));
            fis.close();
            return drawable;
        } catch (Exception e) {
            Log.e(TAG, "Unable to get drawable from cache " + e);
        }
        return null;
    }

    private File cacheGetFileName(String key, String secondaryName) {
        return new File(getIconsCacheDir() + mIconPackPackageName + "_" + key.hashCode()
                + (secondaryName == null ? "" : secondaryName) + ".png");
    }

    private File getIconsCacheDir() {
        File f = new File(mContext.getCacheDir().getPath() + "/icons/");
        if (!f.exists()) {
            if (!f.mkdirs()) {
                Log.e(TAG, "failed to create icons cache folder");
            }
        }
        return f;
    }

    private void clearCache() {
        File cacheDir = getIconsCacheDir();
        if (!cacheDir.isDirectory()) {
            return;
        }

        for (File item : cacheDir.listFiles()) {
            if (!item.delete()) {
                Log.w(TAG, "Failed to delete file: " + item.getAbsolutePath());
            }
        }
    }

    public static class IconPackInfo {
        String packageName;
        CharSequence label;
        Drawable icon;

        IconPackInfo(ResolveInfo r, PackageManager packageManager) {
            packageName = r.activityInfo.packageName;
            icon = r.loadIcon(packageManager);
            label = r.loadLabel(packageManager);
        }
    }

    private static class IconPackLoader extends AsyncTask<Void, Void, Void> {
        private IconCache iconCache;
        private String iconPackPackageName;
        private WeakReference<IconsHandler> handlerReference;

        private IconPackLoader(IconsHandler handler, String packageName) {
            handlerReference = new WeakReference<IconsHandler>(handler);
            iconPackPackageName = packageName;
            iconCache = LauncherAppState.getInstance(handler.mContext).getIconCache();
        }

        @Override
        public void onPreExecute() {
            IconsHandler handler = handlerReference.get();
            if (handler != null) {
                Context context = handler.mContext;
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString(Utilities.KEY_ICON_PACK, iconPackPackageName).apply();
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            IconsHandler handler = handlerReference.get();
            if (handler != null) {
                handler.loadIconPack(iconPackPackageName, false);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            IconsHandler handler = handlerReference.get();
            if (handler != null) {
                Context context = handler.mContext;

                iconCache.clearIconDataBase();
                iconCache.flush();
                LauncherAppState.getInstance(context).getModel().forceReload();
            }
        }
    }
}
