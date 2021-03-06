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

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class CustomIconProvider extends IconProvider {

    private Context mContext;

    public CustomIconProvider(Context context) {
        super();
        mContext = context;
    }

    @Override
    public Drawable getIcon(LauncherActivityInfo info, int iconDpi, boolean flattenDrawable) {
        IconsHandler handler = LauncherAppState.getInstance(mContext)
                .getIconsHandler();

        IconCache cache = LauncherAppState.getInstance(mContext).getIconCache();

        boolean hasCustomIcon = cache.hasCustomIcon(cache.getCacheEntry(info));
        if (hasCustomIcon) {
            IconCache.CacheEntry entry = cache.getCacheEntry(info);
            Drawable drawable = cache.getCustomIcon(entry);
            if (drawable != null) {
                return drawable;
            }
            return new BitmapDrawable(mContext.getResources(), cache.getNonNullIcon(
                    entry, info.getUser()));
        }

        Drawable drawable = handler.getIconFromHandler(info);
        return drawable != null ? drawable : info.getIcon(iconDpi);
    }
}
