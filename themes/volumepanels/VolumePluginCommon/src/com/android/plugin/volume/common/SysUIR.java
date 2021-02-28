/*
 *
 * Copyright (C) 2020 The Potato Open Sauce Project
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
 * limitations under the License
 */

package com.android.plugin.volume.common;

import android.content.Context;
import android.content.res.Resources;
import android.content.pm.PackageManager.NameNotFoundException;

import java.util.HashMap;

public class SysUIR {
    private Context mContext;
    private HashMap<String, Integer> mCachedIds = new HashMap<String, Integer>();

    public SysUIR(Context context) {
        mContext = context;
    }

    public int color(String resName) {
        return sysuiResId("color", resName);
    }

    public int attr(String resName) {
        return sysuiResId("attr", resName);
    }

    public int id(String resName) {
        return sysuiResId("id", resName);
    }

    public int style(String resName) {
        return style(resName, mContext);
    }

    public int style(String resName, Context context) {
        return sysuiResId("style", resName, context);
    }

    public int drawable(String resName) {
        return sysuiResId("drawable", resName);
    }

    public int bool(String resName) {
        return sysuiResId("bool", resName);
    }

    public int string(String resName) {
        return sysuiResId("string", resName);
    }

    public int dimen(String resName) {
        return sysuiResId("dimen", resName);
    }

    private int sysuiResId(String resType, String resName) {
        return sysuiResId(resType, resName, mContext);
    }
    
    private int sysuiResId(String resType, String resName, Context context) {
        if(mCachedIds.containsKey(resName)) {
            return mCachedIds.get(resName);
        } else {
            int resId = 0;
            try {
                Resources res = context.getPackageManager().getResourcesForApplication("com.android.systemui");
                resId = res.getIdentifier(resName, resType, "com.android.systemui");
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            }

            mCachedIds.put(resName, resId);

            return resId;
        }
    }
}
