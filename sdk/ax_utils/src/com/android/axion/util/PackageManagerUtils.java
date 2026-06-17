/*
 * Copyright 2025-2026 AxionOS
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

package com.android.axion.util;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

public final class PackageManagerUtils {
    private PackageManagerUtils() { }

    public static PackageInfo getPackageInfo(Context context, String packageName) {
        return getPackageInfo(context, packageName, 0);
    }

    public static PackageInfo getPackageInfo(Context context, String packageName, int flags) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public static ApplicationInfo getApplicationInfo(Context context, String packageName) {
        return getApplicationInfo(context, packageName, 0);
    }

    public static ApplicationInfo getApplicationInfo(Context context, String packageName,
            int flags) {
        try {
            return context.getPackageManager().getApplicationInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public static Resources getResources(Context context, String packageName) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            return context.getPackageManager().getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public static boolean isPackageInstalled(Context context, String packageName) {
        return getPackageInfo(context, packageName) != null;
    }

    public static Intent getLaunchIntentForPackage(Context context, String packageName) {
        return context.getPackageManager().getLaunchIntentForPackage(packageName);
    }

    public static boolean isPackageLaunchable(Context context, String packageName) {
        return getLaunchIntentForPackage(context, packageName) != null;
    }

    public static Drawable getApplicationIcon(Context context, String packageName) {
        try {
            return context.getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public static Drawable getApplicationIconOrDefault(Context context, String packageName) {
        Drawable icon = getApplicationIcon(context, packageName);
        return icon != null ? icon : context.getPackageManager().getDefaultActivityIcon();
    }

    public static String getPackageVersionName(Context context, String packageName) {
        PackageInfo info = getPackageInfo(context, packageName);
        if (info == null || TextUtils.isEmpty(info.versionName)) {
            return null;
        }
        return info.versionName;
    }

    public static Drawable loadApplicationIcon(PackageManager packageManager,
            ApplicationInfo info) {
        try {
            return info.loadIcon(packageManager);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static CharSequence loadApplicationLabel(PackageManager packageManager,
            ApplicationInfo info) {
        try {
            CharSequence label = info.loadLabel(packageManager);
            return label == null ? info.packageName : label;
        } catch (RuntimeException e) {
            return info.packageName;
        }
    }

    public static CharSequence loadAppWidgetProviderLabel(Context context,
            AppWidgetProviderInfo info) {
        CharSequence label = info.loadLabel(context.getPackageManager());
        if (hasText(label)) {
            return label;
        }
        if (hasText(info.label)) {
            return info.label;
        }
        return info.provider.getPackageName();
    }

    public static ResolveInfo resolveActivity(Context context, Intent intent) {
        return resolveActivity(context, intent, 0);
    }

    public static ResolveInfo resolveActivity(Context context, Intent intent, int flags) {
        return context.getPackageManager().resolveActivity(intent, flags);
    }

    public static boolean isActivityResolvable(Context context, Intent intent) {
        return isActivityResolvable(context, intent, 0);
    }

    public static boolean isActivityResolvable(Context context, Intent intent, int flags) {
        return resolveActivity(context, intent, flags) != null;
    }

    private static boolean hasText(CharSequence text) {
        return text != null && TextUtils.getTrimmedLength(text) > 0;
    }
}
