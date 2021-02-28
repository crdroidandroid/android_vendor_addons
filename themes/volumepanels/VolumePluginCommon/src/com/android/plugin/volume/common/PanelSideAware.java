/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2020 The Potato Open Sauce Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.plugin.volume.common;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;

public abstract class PanelSideAware {
    protected boolean mPanelOnLeftSide = false;

    protected void initObserver(Context sysUIContext, Context localContext) {
        SideObserver observer = new SideObserver(sysUIContext, localContext);
        observer.observe();
    }

    private class SideObserver extends ContentObserver {
        private Context mSysUIContext;
        private Context mLocalContext;
        private SysUIR mSysUIR;

        SideObserver(Context sysUIContext, Context localContext) {
            super(new Handler(Looper.getMainLooper()));
            mSysUIContext = sysUIContext;
            mLocalContext = localContext;
	    mSysUIR = new SysUIR(localContext);
            updateSideVar();
        }

        public void observe() {
            ContentResolver resolver = mLocalContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                "volume_panel_on_left"),
                false, this, UserHandle.USER_CURRENT);
        }

        private void updateSideVar() {
            int defaultValue;

            try {
                defaultValue = mSysUIContext.getResources().getBoolean(
                    mSysUIR.bool("config_audioPanelOnLeftSide")) ? 1 : 0;
            } catch(Exception e) {
                defaultValue = 0;
            }

            int panelOnLeftSide = Settings.System.getIntForUser(
                mLocalContext.getContentResolver(),
                "volume_panel_on_left", defaultValue,
                UserHandle.USER_CURRENT);

            mPanelOnLeftSide = panelOnLeftSide == 1;
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.System.getUriFor("volume_panel_on_left"))) {
                 updateSideVar();
		 onSideChange();
            }
        }
    }

    abstract protected void onSideChange();
}
