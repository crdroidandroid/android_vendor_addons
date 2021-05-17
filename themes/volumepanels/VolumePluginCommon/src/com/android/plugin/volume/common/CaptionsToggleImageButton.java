/*
 * Copyright (C) 2019 The Android Open Source Project
 * Copyright (C) 2020 The Potato Open Sauce Project
 * Copyright (C) 2021 crDroid Android Project
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

package com.android.plugin.volume.common;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;

/** Toggle button in Volume Dialog that allows extra state for when streams are opted-out */
public class CaptionsToggleImageButton extends AlphaOptimizedImageButton {

    private SysUIR mSysUIR;
    private ConfirmedTapListener mConfirmedTapListener;
    private boolean mCaptionsEnabled = false;
    private boolean mOptedOut = false;

    private GestureDetector mGestureDetector;
    private GestureDetector.SimpleOnGestureListener mGestureListener =
            new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return tryToSendTapConfirmedEvent();
        }
    };

    public CaptionsToggleImageButton(Context context) {
        this(context, null);
    }

    public CaptionsToggleImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSysUIR = new SysUIR(context);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mGestureDetector != null) mGestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        int[] state = super.onCreateDrawableState(extraSpace + 1);
        if (mOptedOut) {
            mergeDrawableStates(state, new int[] { mSysUIR.attr("optedOut") });
        }
        return state;
    }

    public Runnable setCaptionsEnabled(boolean areCaptionsEnabled) {
        this.mCaptionsEnabled = areCaptionsEnabled;
        Resources res;

        try {
            res = getContext().getPackageManager().getResourcesForApplication("com.android.systemui");
        } catch (NameNotFoundException e) {
            res = getContext().getResources();
        }

        ViewCompat.replaceAccessibilityAction(
                this,
                AccessibilityActionCompat.ACTION_CLICK,
                mCaptionsEnabled
                        ? res.getString(mSysUIR.string("volume_odi_captions_hint_disable"))
                        : res.getString(mSysUIR.string("volume_odi_captions_hint_enable")),
                (view, commandArguments) -> tryToSendTapConfirmedEvent());

        return this.setImageResourceAsync(mCaptionsEnabled
                ? mSysUIR.drawable("ic_volume_odi_captions")
                : mSysUIR.drawable("ic_volume_odi_captions_disabled"));
    }

    private boolean tryToSendTapConfirmedEvent() {
        if (mConfirmedTapListener != null) {
            mConfirmedTapListener.onConfirmedTap();
            return true;
        }
        return false;
    }

    public boolean getCaptionsEnabled() {
        return this.mCaptionsEnabled;
    }

    /** Sets whether or not the current stream has opted out of captions */
    public void setOptedOut(boolean isOptedOut) {
        this.mOptedOut = isOptedOut;
        refreshDrawableState();
    }

    public boolean getOptedOut() {
        return this.mOptedOut;
    }

    public void setOnConfirmedTapListener(ConfirmedTapListener listener, Handler handler) {
        mConfirmedTapListener = listener;

        if (mGestureDetector == null) {
            this.mGestureDetector = new GestureDetector(getContext(), mGestureListener, handler);
        }
    }

    /** Listener for confirmed taps rather than normal on click listener. */
    public interface ConfirmedTapListener {
        void onConfirmedTap();
    }
}
