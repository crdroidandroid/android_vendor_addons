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

package com.android.axion.search;

import android.content.Intent;
import android.graphics.drawable.Drawable;

public final class UniversalSearchResult {
    public static final int TYPE_APP = 0;
    public static final int TYPE_SETTINGS = 1;
    public static final int TYPE_IMAGE = 2;
    public static final int TYPE_FILE = 3;
    public static final int TYPE_WEB = 4;
    public static final int TYPE_IN_APP = 5;
    public static final int TYPE_MEDIA = 6;
    public static final int TYPE_CONTACT = 7;
    public static final int TYPE_ANSWER = 8;
    public static final int TYPE_CALENDAR = 9;
    public static final int TYPE_MASK_APP = 1 << TYPE_APP;
    public static final int TYPE_MASK_SETTINGS = 1 << TYPE_SETTINGS;
    public static final int TYPE_MASK_IMAGE = 1 << TYPE_IMAGE;
    public static final int TYPE_MASK_FILE = 1 << TYPE_FILE;
    public static final int TYPE_MASK_WEB = 1 << TYPE_WEB;
    public static final int TYPE_MASK_IN_APP = 1 << TYPE_IN_APP;
    public static final int TYPE_MASK_MEDIA = 1 << TYPE_MEDIA;
    public static final int TYPE_MASK_CONTACT = 1 << TYPE_CONTACT;
    public static final int TYPE_MASK_ANSWER = 1 << TYPE_ANSWER;
    public static final int TYPE_MASK_CALENDAR = 1 << TYPE_CALENDAR;
    public static final int TYPE_MASK_ALL = TYPE_MASK_APP
            | TYPE_MASK_SETTINGS
            | TYPE_MASK_IMAGE
            | TYPE_MASK_FILE
            | TYPE_MASK_WEB
            | TYPE_MASK_IN_APP
            | TYPE_MASK_MEDIA
            | TYPE_MASK_CONTACT
            | TYPE_MASK_ANSWER
            | TYPE_MASK_CALENDAR;

    private final int mType;
    private final CharSequence mTitle;
    private final CharSequence mSubtitle;
    private final Drawable mIcon;
    private final Intent mIntent;
    private final boolean mIconTinted;
    private final boolean mIconFullBleed;
    private final boolean mExternal;
    private final String mSourcePackage;

    public UniversalSearchResult(int type, CharSequence title, CharSequence subtitle,
            Drawable icon, Intent intent, boolean iconTinted, boolean iconFullBleed,
            boolean external) {
        this(type, title, subtitle, icon, intent, iconTinted, iconFullBleed, external, null);
    }

    public UniversalSearchResult(int type, CharSequence title, CharSequence subtitle,
            Drawable icon, Intent intent, boolean iconTinted, boolean iconFullBleed,
            boolean external, String sourcePackage) {
        mType = type;
        mTitle = title;
        mSubtitle = subtitle;
        mIcon = icon;
        mIntent = intent;
        mIconTinted = iconTinted;
        mIconFullBleed = iconFullBleed;
        mExternal = external;
        mSourcePackage = sourcePackage;
    }

    public int getType() {
        return mType;
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public CharSequence getSubtitle() {
        return mSubtitle;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public Intent getIntent() {
        return mIntent;
    }

    public boolean isIconTinted() {
        return mIconTinted;
    }

    public boolean isIconFullBleed() {
        return mIconFullBleed;
    }

    public boolean isExternal() {
        return mExternal;
    }

    public String getSourcePackage() {
        return mSourcePackage;
    }

    public static int getTypeMask(int type) {
        return 1 << type;
    }
}
