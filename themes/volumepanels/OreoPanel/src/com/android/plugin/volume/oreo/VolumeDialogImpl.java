/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.plugin.volume.oreo;

import static android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
import static android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC;
import static android.media.AudioManager.RINGER_MODE_NORMAL;
import static android.media.AudioManager.RINGER_MODE_SILENT;
import static android.media.AudioManager.RINGER_MODE_VIBRATE;
import static android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.animation.ObjectAnimator;
import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.transition.AutoTransition;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Slog;
import android.util.SparseBooleanArray;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.View.OnAttachStateChangeListener;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.InternalInsetsInfo;
import android.view.ViewTreeObserver.OnComputeInternalInsetsListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.android.plugin.volume.common.*;

import com.android.plugin.volume.oreo.R;

import com.android.systemui.plugins.PluginDependency;
import com.android.systemui.plugins.VolumeDialog;
import com.android.systemui.plugins.VolumeDialogController;
import com.android.systemui.plugins.VolumeDialogController.State;
import com.android.systemui.plugins.VolumeDialogController.StreamState;
import com.android.systemui.plugins.annotations.Requires;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual presentation of the volume dialog.
 *
 * A client of VolumeDialogControllerImpl and its state model.
 *
 * Methods ending in "H" must be called on the (ui) handler.
 */
@Requires(target = VolumeDialog.class, version = VolumeDialog.VERSION)
@Requires(target = VolumeDialog.Callback.class, version = VolumeDialog.Callback.VERSION)
@Requires(target = VolumeDialogController.class, version = VolumeDialogController.VERSION)
public class VolumeDialogImpl implements VolumeDialog {
    private static final String TAG = Utils.logTag(VolumeDialogImpl.class);

    public static final String SHOW_FULL_ZEN = "sysui_show_full_zen";

    private static final long USER_ATTEMPT_GRACE_PERIOD = 1000;
    private static final int UPDATE_ANIMATION_DURATION = 80;

    private SysUIR mSysUIR;
    private Context mContext;
    private Context mSysUIContext;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mWindowParams;
    private final H mHandler = new H();
    private VolumeDialogController mController;

    private View mDialog;
    private ViewGroup mDialogView;
    private ViewGroup mDialogRowsView;
    private ViewGroup mRinger;
    private ImageButton mRingerIcon;
    private FrameLayout mZenIcon;
    private ViewGroup mDialogContentView;
    private ExpandableIndicator mExpandButton;
    private final List<VolumeRow> mRows = new ArrayList<>();
    private ConfigurableTexts mConfigurableTexts;
    private final SparseBooleanArray mDynamic = new SparseBooleanArray();
    private KeyguardManager mKeyguard;
    private AudioManager mAudioManager;
    private AccessibilityManager mAccessibilityMgr;
    private int mExpandButtonAnimationDuration;
    private final Object mSafetyWarningLock = new Object();
    private final Accessibility mAccessibility = new Accessibility();
    private ColorStateList mActiveSliderTint;
    private ColorStateList mInactiveSliderTint;
    private VolumeDialogMotion mMotion;
    private int mWindowType;

    private boolean mShowing;
    private boolean mExpanded;
    private boolean mShowA11yStream;

    private int mActiveStream;
    private int mPrevActiveStream;
    private boolean mAutomute = VolumePrefs.DEFAULT_ENABLE_AUTOMUTE;
    private boolean mSilentMode = VolumePrefs.DEFAULT_ENABLE_SILENT_MODE;
    private State mState;
    private boolean mExpandButtonAnimationRunning;
    private SafetyWarningDialog mSafetyWarning;
    private Callback mCallback;
    private boolean mPendingStateChanged;
    private boolean mPendingRecheckAll;
    private long mCollapseTime;
    private boolean mHovering = false;
    private int mDensity;

    public VolumeDialogImpl() {}

    @Override
    public void onCreate(Context sysuiContext, Context pluginContext) {
        mSysUIR = new SysUIR(pluginContext);
        mContext = pluginContext;
        mSysUIContext = 
                new ContextThemeWrapper(sysuiContext, mSysUIR.style("qs_theme", sysuiContext));
        mController = PluginDependency.get(this, VolumeDialogController.class);
        mKeyguard = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mAccessibilityMgr =
                (AccessibilityManager) mContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    public void init(int windowType, Callback callback) {
        mCallback = callback;
        mWindowType = windowType;

        initDialog();

        mAccessibility.init();

        mController.addCallback(mControllerCallbackH, mHandler);
        mController.getState();

        final Configuration currentConfig = mContext.getResources().getConfiguration();
        mDensity = currentConfig.densityDpi;
    }

    @Override
    public void destroy() {
        mAccessibility.destroy();
        mController.removeCallback(mControllerCallbackH);
        mHandler.removeCallbacksAndMessages(null);
    }

    private void initDialog() {
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        
        mSysUIContext.getTheme().applyStyle(mSysUIContext.getThemeResId(), true);
        mSysUIContext.getTheme().rebase();
        mContext.getTheme().setTo(mSysUIContext.getTheme());
        mActiveSliderTint = ColorStateList.valueOf(Utils.getColorAccentDefaultColor(mContext));
        mInactiveSliderTint = loadColorStateList(R.color.volume_slider_inactive);

        mConfigurableTexts = new ConfigurableTexts(mContext);
        mHovering = false;
        mShowing = false;
        mWindowParams = new WindowManager.LayoutParams();
        mWindowParams.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        mWindowParams.flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        mWindowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        mWindowParams.type = mWindowType;
        mWindowParams.format = PixelFormat.TRANSLUCENT;
        mWindowParams.windowAnimations = -1;
        mDialog = LayoutInflater.from(mContext).inflate(R.layout.volume_dialog_oreo, (ViewGroup) null, false);

        mDialog.setOnTouchListener((v, event) -> {
            if (mShowing) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_OUTSIDE:
                    case MotionEvent.ACTION_DOWN:
                        dismissH(Events.DISMISS_REASON_TOUCH_OUTSIDE);
                        return true;
                }
            }
            return false;
        });

        mDialogView = (ViewGroup) mDialog.findViewById(R.id.volume_dialog);
        mDialogView.setOnHoverListener(new View.OnHoverListener() {
            @Override
            public boolean onHover(View v, MotionEvent event) {
                int action = event.getActionMasked();
                mHovering = (action == MotionEvent.ACTION_HOVER_ENTER)
                        || (action == MotionEvent.ACTION_HOVER_MOVE);
                rescheduleTimeoutH();
                return true;
            }
        });

        mDialogContentView = mDialog.findViewById(R.id.volume_dialog_content);
        mDialogRowsView = mDialogContentView.findViewById(R.id.volume_dialog_rows);
        mRinger = mDialog.findViewById(R.id.ringer);
        if (mRinger != null) {
            mRingerIcon = mRinger.findViewById(R.id.ringer_icon);
            mZenIcon = mRinger.findViewById(R.id.dnd_icon);
        }
        mExpanded = false;
        mExpandButton = (ExpandableIndicator) mDialogView.findViewById(R.id.volume_expand_button);
        mExpandButton.setOnClickListener(mClickExpand);

        mExpandButton.setVisibility(View.VISIBLE);
        updateWindowWidthH();
        updateExpandButtonH();

        mMotion = new VolumeDialogMotion(mDialog, mWindowManager, mWindowParams,
                mDialogView, mDialogContentView, mExpandButton,
                new VolumeDialogMotion.Callback() {
                    @Override
                    public void onAnimatingChanged(boolean animating) {
                        if (animating) return;
                        if (mPendingStateChanged) {
                            mHandler.sendEmptyMessage(H.STATE_CHANGED);
                            mPendingStateChanged = false;
                        }
                        if (mPendingRecheckAll) {
                            mHandler.sendEmptyMessage(H.RECHECK_ALL);
                            mPendingRecheckAll = false;
                        }
                    }
                });

        if (mRows.isEmpty()) {
            addRow(AudioManager.STREAM_MUSIC,
                    mSysUIR.drawable("ic_volume_media"), mSysUIR.drawable("ic_volume_media_mute"), true, true);
            if (!AudioSystem.isSingleVolume(mContext)) {
                addRow(AudioManager.STREAM_RING,
                        mSysUIR.drawable("ic_volume_ringer"), mSysUIR.drawable("ic_volume_ringer_mute"), true, false);
                addRow(AudioManager.STREAM_ALARM,
                        mSysUIR.drawable("ic_volume_alarm"), mSysUIR.drawable("ic_volume_alarm_mute"), true, false);
                addRow(AudioManager.STREAM_VOICE_CALL,
                        com.android.internal.R.drawable.ic_phone,
                        com.android.internal.R.drawable.ic_phone, false, false);
                addRow(AudioManager.STREAM_BLUETOOTH_SCO,
                        mSysUIR.drawable("ic_volume_bt_sco"), mSysUIR.drawable("ic_volume_bt_sco"), false, false);
                addRow(AudioManager.STREAM_SYSTEM, mSysUIR.drawable("ic_volume_system"),
                        mSysUIR.drawable("ic_volume_system_mute"), false, false);
                addRow(AudioManager.STREAM_ACCESSIBILITY, mSysUIR.drawable("ic_volume_accessibility"),
                        mSysUIR.drawable("ic_volume_accessibility"), true, false);
            }
        } else {
            addExistingRows();
        }
        mExpandButtonAnimationDuration = 300;
        initRingerH();
    }
    
    private final OnComputeInternalInsetsListener mInsetsListener = internalInsetsInfo -> {
        internalInsetsInfo.touchableRegion.setEmpty();
        internalInsetsInfo.setTouchableInsets(InternalInsetsInfo.TOUCHABLE_INSETS_REGION);
        int[] dialogViewLocation = new int[2];
        mDialogView.getLocationInWindow(dialogViewLocation);
        internalInsetsInfo.touchableRegion.set(new Region(
            dialogViewLocation[0],
            dialogViewLocation[1],
            dialogViewLocation[0] + mDialogView.getWidth(),
            dialogViewLocation[1] + mDialogView.getHeight()
        ));
    };

    private ColorStateList loadColorStateList(int colorResId) {
        return ColorStateList.valueOf(mContext.getColor(colorResId));
    }

    private void updateWindowWidthH() {
        final ViewGroup.LayoutParams lp = mDialogView.getLayoutParams();
        final DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        if (D.BUG) Log.d(TAG, "updateWindowWidth dm.w=" + dm.widthPixels);
        int w = dm.widthPixels;
        final int max = mContext.getResources()
                .getDimensionPixelSize(R.dimen.volume_dialog_oreo_panel_width);
        if (w > max) {
            w = max;
        }
        lp.width = w;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mDialogView.setLayoutParams(lp);
    }

    public void setStreamImportant(int stream, boolean important) {
        mHandler.obtainMessage(H.SET_STREAM_IMPORTANT, stream, important ? 1 : 0).sendToTarget();
    }

    public void setAutomute(boolean automute) {
        if (mAutomute == automute) return;
        mAutomute = automute;
        mHandler.sendEmptyMessage(H.RECHECK_ALL);
    }

    public void setSilentMode(boolean silentMode) {
        if (mSilentMode == silentMode) return;
        mSilentMode = silentMode;
        mHandler.sendEmptyMessage(H.RECHECK_ALL);
    }

    private void addRow(int stream, int iconRes, int iconMuteRes, boolean important) {
        addRow(stream, iconRes, iconMuteRes, important, false);
    }

    private void addRow(int stream, int iconRes, int iconMuteRes, boolean important,
            boolean dynamic) {
        VolumeRow row = new VolumeRow();
        initRow(row, stream, iconRes, iconMuteRes, important);
        int rowSize;
        int viewSize;
        if (mShowA11yStream && dynamic && (rowSize = mRows.size()) > 1
                && (viewSize = mDialogRowsView.getChildCount()) > 1) {
            // A11y Stream should be the last in the list
            mDialogRowsView.addView(row.view, viewSize - 2);
            mRows.add(rowSize - 2, row);
        } else {
            mDialogRowsView.addView(row.view);
            mRows.add(row);
        }
    }

    private void addExistingRows() {
        int N = mRows.size();
        for (int i = 0; i < N; i++) {
            final VolumeRow row = mRows.get(i);
            initRow(row, row.stream, row.iconRes, row.iconMuteRes, row.important);
            mDialogRowsView.addView(row.view);
        }
    }


    private boolean isAttached() {
        return mDialogContentView != null && mDialogContentView.isAttachedToWindow();
    }

    private VolumeRow getActiveRow() {
        for (VolumeRow row : mRows) {
            if (row.stream == mActiveStream) {
                return row;
            }
        }
        return mRows.get(0);
    }

    private VolumeRow findRow(int stream) {
        for (VolumeRow row : mRows) {
            if (row.stream == stream) return row;
        }
        return null;
    }

    public void dump(PrintWriter writer) {
        writer.println(VolumeDialogImpl.class.getSimpleName() + " state:");
        writer.print("  mShowing: "); writer.println(mShowing);
        writer.print("  mExpanded: "); writer.println(mExpanded);
        writer.print("  mExpandButtonAnimationRunning: ");
        writer.println(mExpandButtonAnimationRunning);
        writer.print("  mActiveStream: "); writer.println(mActiveStream);
        writer.print("  mDynamic: "); writer.println(mDynamic);
        writer.print("  mAutomute: "); writer.println(mAutomute);
        writer.print("  mSilentMode: "); writer.println(mSilentMode);
        writer.print("  mCollapseTime: "); writer.println(mCollapseTime);
        writer.print("  mAccessibility.mFeedbackEnabled: ");
        writer.println(mAccessibility.mFeedbackEnabled);
    }

    private static int getImpliedLevel(SeekBar seekBar, int progress) {
        final int m = seekBar.getMax();
        final int n = m / 100 - 1;
        final int level = progress == 0 ? 0
                : progress == m ? (m / 100) : (1 + (int)((progress / (float) m) * n));
        return level;
    }

    @SuppressLint("InflateParams")
    private void initRow(final VolumeRow row, final int stream, int iconRes, int iconMuteRes,
            boolean important) {
        row.stream = stream;
        row.iconRes = iconRes;
        row.iconMuteRes = iconMuteRes;
        row.important = important;
        row.view = LayoutInflater.from(mContext).inflate(R.layout.volume_dialog_oreo_row, null);
        row.view.setId(row.stream);
        row.view.setTag(row);
        row.header = (TextView) row.view.findViewById(R.id.volume_row_header);
        row.header.setId(20 * row.stream);
        row.slider = (SeekBar) row.view.findViewById(R.id.volume_row_slider);
        row.slider.setOnSeekBarChangeListener(new VolumeSeekBarChangeListener(row));
        row.anim = null;

        // forward events above the slider into the slider
        row.view.setOnTouchListener(new OnTouchListener() {
            private final Rect mSliderHitRect = new Rect();
            private boolean mDragging;

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                row.slider.getHitRect(mSliderHitRect);
                if (!mDragging && event.getActionMasked() == MotionEvent.ACTION_DOWN
                        && event.getY() < mSliderHitRect.top) {
                    mDragging = true;
                }
                if (mDragging) {
                    event.offsetLocation(-mSliderHitRect.left, -mSliderHitRect.top);
                    row.slider.dispatchTouchEvent(event);
                    if (event.getActionMasked() == MotionEvent.ACTION_UP
                            || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        mDragging = false;
                    }
                    return true;
                }
                return false;
            }
        });
        row.icon = row.view.findViewById(R.id.volume_row_icon);
        Drawable iconResDrawable = mSysUIContext.getDrawable(iconRes);
        row.icon.setImageDrawable(iconResDrawable);
        if (row.stream != AudioSystem.STREAM_ACCESSIBILITY) {
            row.icon.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Events.writeEvent(Events.EVENT_ICON_CLICK, row.stream, row.iconState);
                    rescheduleTimeoutH();
                    mController.setActiveStream(row.stream);
                    final boolean vmute = row.ss.level == row.ss.levelMin;
                    mController.setStreamVolume(stream,
                            vmute ? row.lastAudibleLevel : row.ss.levelMin);
                    row.userAttempt = 0;  // reset the grace period, slider updates immediately
                }
            });
        } else {
            row.icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    public void show(int reason) {
        mHandler.obtainMessage(H.SHOW, reason, 0).sendToTarget();
    }

    public void dismiss(int reason) {
        mHandler.obtainMessage(H.DISMISS, reason, 0).sendToTarget();
    }

    private void showH(int reason) {
        if (D.BUG) Log.d(TAG, "showH r=" + Events.DISMISS_REASONS[reason]);
        mHandler.removeMessages(H.SHOW);
        mHandler.removeMessages(H.DISMISS);
        rescheduleTimeoutH();
        if (mShowing) return;
        mShowing = true;
        mDialog.getViewTreeObserver().addOnComputeInternalInsetsListener(mInsetsListener);
        mMotion.startShow();
        Events.writeEvent(Events.EVENT_SHOW_DIALOG, reason, mKeyguard.isKeyguardLocked());
        mController.notifyVisible(true);
    }

    protected void rescheduleTimeoutH() {
        mHandler.removeMessages(H.DISMISS);
        final int timeout = computeTimeoutH();
        mHandler.sendMessageDelayed(mHandler
                .obtainMessage(H.DISMISS, Events.DISMISS_REASON_TIMEOUT, 0), timeout);
        if (D.BUG) Log.d(TAG, "rescheduleTimeout " + timeout + " " + Debug.getCaller());
        mController.userActivity();
    }

    private int computeTimeoutH() {
        if (mAccessibility.mFeedbackEnabled) return 20000;
        if (mHovering) return 16000;
        if (mSafetyWarning != null) return 5000;
        if (mExpanded || mExpandButtonAnimationRunning) return 5000;
        if (mActiveStream == AudioManager.STREAM_MUSIC) return 1500;
        return 3000;
    }

    protected void dismissH(int reason) {
        if (mMotion.isAnimating()) {
            return;
        }
        mHandler.removeMessages(H.DISMISS);
        mHandler.removeMessages(H.SHOW);
        if (!mShowing) return;
        mShowing = false;
        mMotion.startDismiss(new Runnable() {
            @Override
            public void run() {
                updateExpandedH(false /* expanding */, true /* dismissing */);
            }
        });
        if (mAccessibilityMgr.isEnabled()) {
            AccessibilityEvent event =
                    AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            event.setPackageName(mContext.getPackageName());
            event.setClassName(VolumeDialogImpl.class.getName());
            event.getText().add(mSysUIContext.getString(
                    R.string.volume_dialog_accessibility_dismissed_message));
            mAccessibilityMgr.sendAccessibilityEvent(event);
        }
        Events.writeEvent(Events.EVENT_DISMISS_DIALOG, reason);
        mDialog.getViewTreeObserver().removeOnComputeInternalInsetsListener(mInsetsListener);
        mController.notifyVisible(false);
        synchronized (mSafetyWarningLock) {
            if (mSafetyWarning != null) {
                if (D.BUG) Log.d(TAG, "SafetyWarning dismissed");
                mSafetyWarning.dismiss();
            }
        }
    }

    private void updateDialogBottomMarginH() {
        final long diff = System.currentTimeMillis() - mCollapseTime;
        final boolean collapsing = mCollapseTime != 0 && diff < getConservativeCollapseDuration();
        final ViewGroup.MarginLayoutParams mlp = (MarginLayoutParams) mDialogView.getLayoutParams();
        final int bottomMargin = collapsing ? mDialogContentView.getHeight() :
                mContext.getResources().getDimensionPixelSize(R.dimen.volume_dialog_margin_bottom);
        if (bottomMargin != mlp.bottomMargin) {
            if (D.BUG) Log.d(TAG, "bottomMargin " + mlp.bottomMargin + " -> " + bottomMargin);
            mlp.bottomMargin = bottomMargin;
            mDialogView.setLayoutParams(mlp);
        }
    }

    private long getConservativeCollapseDuration() {
        return mExpandButtonAnimationDuration * 3;
    }

    private void prepareForCollapse() {
        mHandler.removeMessages(H.UPDATE_BOTTOM_MARGIN);
        mCollapseTime = System.currentTimeMillis();
        updateDialogBottomMarginH();
        mHandler.sendEmptyMessageDelayed(H.UPDATE_BOTTOM_MARGIN, getConservativeCollapseDuration());
    }

    private void updateExpandedH(final boolean expanded, final boolean dismissing) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        mExpandButtonAnimationRunning = isAttached();
        if (D.BUG) Log.d(TAG, "updateExpandedH " + expanded);
        updateExpandButtonH();
        TransitionManager.endTransitions(mDialogView);
        final VolumeRow activeRow = getActiveRow();
        if (!dismissing) {
            TransitionManager.beginDelayedTransition(mDialogView, getTransition());
        }
        updateRowsH(activeRow);
        rescheduleTimeoutH();
    }

    private void updateExpandButtonH() {
        if (D.BUG) Log.d(TAG, "updateExpandButtonH");
        mExpandButton.setClickable(!mExpandButtonAnimationRunning);
        if (!(mExpandButtonAnimationRunning && isAttached())) {
            mExpandButton.setExpanded(mExpanded);
        }
        if (mExpandButtonAnimationRunning) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mExpandButtonAnimationRunning = false;
                    updateExpandButtonH();
                    rescheduleTimeoutH();
                }
            }, mExpandButtonAnimationDuration);
        }
    }

    public void initRingerH() {
        if (mRingerIcon != null) {
            mRingerIcon.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
            mRingerIcon.setOnClickListener(v -> {
                rescheduleTimeoutH();
                Prefs.putBoolean(mSysUIContext, Prefs.Key.TOUCHED_RINGER_TOGGLE, true);
                final StreamState ss = mState.states.get(AudioManager.STREAM_RING);
                if (ss == null) {
                    return;
                }
                // normal -> vibrate -> silent -> normal (skip vibrate if device doesn't have
                // a vibrator.
                int newRingerMode;
                final boolean hasVibrator = mController.hasVibrator();
                if (mState.ringerModeInternal == AudioManager.RINGER_MODE_NORMAL) {
                    if (hasVibrator) {
                        newRingerMode = AudioManager.RINGER_MODE_VIBRATE;
                    } else {
                        newRingerMode = AudioManager.RINGER_MODE_SILENT;
                    }
                } else if (mState.ringerModeInternal == AudioManager.RINGER_MODE_VIBRATE) {
                    newRingerMode = AudioManager.RINGER_MODE_SILENT;
                } else {
                    newRingerMode = AudioManager.RINGER_MODE_NORMAL;
                    if (ss.level == 0) {
                        mController.setStreamVolume(AudioManager.STREAM_RING, 1);
                    }
                }
                Events.writeEvent(Events.EVENT_RINGER_TOGGLE, newRingerMode);
                incrementManualToggleCount();
                updateRingerH();
                provideTouchFeedbackH(newRingerMode);
                mController.setRingerMode(newRingerMode, false);
                maybeShowToastH(newRingerMode);
            });
        }
        updateRingerH();
    }

    private void incrementManualToggleCount() {
        ContentResolver cr = mContext.getContentResolver();
        int ringerCount = Settings.Secure.getInt(cr, Settings.Secure.MANUAL_RINGER_TOGGLE_COUNT, 0);
        Settings.Secure.putInt(cr, Settings.Secure.MANUAL_RINGER_TOGGLE_COUNT, ringerCount + 1);
    }

    private void provideTouchFeedbackH(int newRingerMode) {
        VibrationEffect effect = null;
        switch (newRingerMode) {
            case RINGER_MODE_NORMAL:
                mController.scheduleTouchFeedback();
                break;
            case RINGER_MODE_SILENT:
                effect = VibrationEffect.get(VibrationEffect.EFFECT_CLICK);
                break;
            case RINGER_MODE_VIBRATE:
            default:
                effect = VibrationEffect.get(VibrationEffect.EFFECT_DOUBLE_CLICK);
        }
        if (effect != null) {
            mController.vibrate(effect);
        }
    }

    private void maybeShowToastH(int newRingerMode) {
        int seenToastCount = Prefs.getInt(mSysUIContext, Prefs.Key.SEEN_RINGER_GUIDANCE_COUNT, 0);

        if (seenToastCount > VolumePrefs.SHOW_RINGER_TOAST_COUNT) {
            return;
        }
        CharSequence toastText = null;
        switch (newRingerMode) {
            case RINGER_MODE_NORMAL:
                final StreamState ss = mState.states.get(AudioManager.STREAM_RING);
                if (ss != null) {
                    toastText = mSysUIContext.getString(
                            mSysUIR.string("volume_dialog_ringer_guidance_ring"),
                            Utils.formatPercentage(ss.level, ss.levelMax));
                }
                break;
            case RINGER_MODE_SILENT:
                toastText = mSysUIContext.getString(
                        com.android.internal.R.string.volume_dialog_ringer_guidance_silent);
                break;
            case RINGER_MODE_VIBRATE:
            default:
                toastText = mSysUIContext.getString(
                        com.android.internal.R.string.volume_dialog_ringer_guidance_vibrate);
        }

        Toast.makeText(mContext, toastText, Toast.LENGTH_SHORT).show();
        seenToastCount++;
        Prefs.putInt(mSysUIContext, Prefs.Key.SEEN_RINGER_GUIDANCE_COUNT, seenToastCount);
    }

    private void updateRingerH() {
        if (mRinger != null && mState != null) {
            final StreamState ss = mState.states.get(AudioManager.STREAM_RING);
            if (ss == null) {
                return;
            }

            boolean isZenMuted = mState.zenMode == Global.ZEN_MODE_ALARMS
                    || mState.zenMode == Global.ZEN_MODE_NO_INTERRUPTIONS
                    || (mState.zenMode == Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS
                        && mState.disallowRinger);
            enableRingerViewsH(!isZenMuted);
            Drawable ringerDrawable;
            switch (mState.ringerModeInternal) {
                case AudioManager.RINGER_MODE_VIBRATE:
                    ringerDrawable = mSysUIContext.getDrawable(
                        mSysUIR.drawable("ic_volume_ringer_vibrate"));
                    addAccessibilityDescription(mRingerIcon, RINGER_MODE_VIBRATE,
                            mSysUIContext.getString(mSysUIR.string("volume_ringer_hint_mute")));
                    mRingerIcon.setTag(Events.ICON_STATE_VIBRATE);
                    break;
                case AudioManager.RINGER_MODE_SILENT:
                    ringerDrawable = mSysUIContext.getDrawable(
                        mSysUIR.drawable("ic_volume_ringer_mute"));
                    mRingerIcon.setTag(Events.ICON_STATE_MUTE);
                    addAccessibilityDescription(mRingerIcon, RINGER_MODE_SILENT,
                            mSysUIContext.getString(mSysUIR.string("volume_ringer_hint_unmute")));
                    break;
                case AudioManager.RINGER_MODE_NORMAL:
                default:
                    boolean muted = (mAutomute && ss.level == 0) || ss.muted;
                    if (!isZenMuted && muted) {
                        ringerDrawable = mSysUIContext.getDrawable(
                            mSysUIR.drawable("ic_volume_ringer_mute"));
                        addAccessibilityDescription(mRingerIcon, RINGER_MODE_NORMAL,
                                mSysUIContext.getString(mSysUIR.string("volume_ringer_hint_unmute")));
                        mRingerIcon.setTag(Events.ICON_STATE_MUTE);
                    } else {
                        ringerDrawable = mSysUIContext.getDrawable(
                            mSysUIR.drawable("ic_volume_ringer"));
                        if (mController.hasVibrator()) {
                            addAccessibilityDescription(mRingerIcon, RINGER_MODE_NORMAL,
                                    mSysUIContext.getString(mSysUIR.string("volume_ringer_hint_vibrate")));
                        } else {
                            addAccessibilityDescription(mRingerIcon, RINGER_MODE_NORMAL,
                                    mSysUIContext.getString(mSysUIR.string("volume_ringer_hint_mute")));
                        }
                        mRingerIcon.setTag(Events.ICON_STATE_UNMUTE);
                    }
                    break;
            }
            mRingerIcon.setImageDrawable(ringerDrawable);
        }
    }

    private void addAccessibilityDescription(View view, int currState, String hintLabel) {
        int currStateResId;
        switch (currState) {
            case RINGER_MODE_SILENT:
                currStateResId = mSysUIR.string("volume_ringer_status_silent");
                break;
            case RINGER_MODE_VIBRATE:
                currStateResId = mSysUIR.string("volume_ringer_status_vibrate");
                break;
            case RINGER_MODE_NORMAL:
            default:
                currStateResId = mSysUIR.string("volume_ringer_status_normal");
        }

        view.setContentDescription(mSysUIContext.getString(currStateResId));
        view.setAccessibilityDelegate(new AccessibilityDelegate() {
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                                AccessibilityNodeInfo.ACTION_CLICK, hintLabel));
            }
        });
    }

    /**
     * Toggles enable state of footer/ringer views
     * Hides/shows zen icon
     * @param enable whether to enable ringer views and hide dnd icon
     */
    private void enableRingerViewsH(boolean enable) {
        if (mRingerIcon != null) {
            mRingerIcon.setEnabled(enable);
        }
        if (mZenIcon != null) {
            mZenIcon.setVisibility(enable ? View.GONE : View.VISIBLE);
        }
    }

    private boolean shouldBeVisibleH(VolumeRow row, VolumeRow activeRow) {
        boolean isActive = row == activeRow;
        if (row.stream == AudioSystem.STREAM_ACCESSIBILITY) {
            return mShowA11yStream;
        }

        // if the active row is accessibility, then continue to display previous
        // active row since accessibility is dispalyed under it
        if (activeRow.stream == AudioSystem.STREAM_ACCESSIBILITY &&
                row.stream == mPrevActiveStream) {
            return true;
        }

        return mExpanded && row.view.getVisibility() == View.VISIBLE
                || (mExpanded && (row.important || isActive))
                || !mExpanded && isActive;
    }

    private void updateRowsH(final VolumeRow activeRow) {
        if (D.BUG) Log.d(TAG, "updateRowsH");
        if (!mShowing) {
            trimObsoleteH();
        }
        // apply changes to all rows
        for (final VolumeRow row : mRows) {
            final boolean isActive = row == activeRow;
            final boolean shouldBeVisible = shouldBeVisibleH(row, activeRow);
            Utils.setVisOrGone(row.view, shouldBeVisible);
            Utils.setVisOrGone(row.header, false);
            if (row.view.isShown()) {
                updateVolumeRowSliderTintH(row, isActive);
            }
        }
    }

    private void trimObsoleteH() {
        if (D.BUG) Log.d(TAG, "trimObsoleteH");
        for (int i = mRows.size() - 1; i >= 0; i--) {
            final VolumeRow row = mRows.get(i);
            if (row.ss == null || !row.ss.dynamic) continue;
            if (!mDynamic.get(row.stream)) {
                removeRow(row);
                mConfigurableTexts.remove(row.header);
            }
        }
    }

    private void removeRow(VolumeRow volumeRow) {
        mRows.remove(volumeRow);
        mDialogRowsView.removeView(volumeRow.view);
    }

    private void onStateChangedH(State state) {
        if (D.BUG) Log.d(TAG, "onStateChangedH() state: " + state.toString());
        if (mShowing && mState != null && state != null
                && mState.ringerModeInternal != -1
                && mState.ringerModeInternal != state.ringerModeInternal
                && state.ringerModeInternal == AudioManager.RINGER_MODE_VIBRATE) {
            mController.vibrate(VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK));
        }
        mState = state;
        final boolean animating = mMotion.isAnimating();
        if (D.BUG) Log.d(TAG, "onStateChangedH animating=" + animating);
        if (animating) {
            mPendingStateChanged = true;
            return;
        }
        mDynamic.clear();
        // add any new dynamic rows
        for (int i = 0; i < state.states.size(); i++) {
            final int stream = state.states.keyAt(i);
            final StreamState ss = state.states.valueAt(i);
            if (!ss.dynamic) continue;
            mDynamic.put(stream, true);
            if (findRow(stream) == null) {
                addRow(stream, mSysUIR.drawable("ic_volume_remote"),
                        mSysUIR.drawable("ic_volume_remote_mute"),
                        true, true);
            }
        }

        if (mActiveStream != state.activeStream) {
            mPrevActiveStream = mActiveStream;
            mActiveStream = state.activeStream;
            updateRowsH(getActiveRow());
            rescheduleTimeoutH();
        }
        for (VolumeRow row : mRows) {
            updateVolumeRowH(row);
        }
        updateRingerH();
    }

    private void updateVolumeRowH(VolumeRow row) {
        if (D.BUG) Log.d(TAG, "updateVolumeRowH s=" + row.stream);
        if (mState == null) return;
        final StreamState ss = mState.states.get(row.stream);
        if (ss == null) return;
        row.ss = ss;
        if (ss.level > 0) {
            row.lastAudibleLevel = ss.level;
        }
        if (ss.level == row.requestedLevel) {
            row.requestedLevel = -1;
        }
        final boolean isA11yStream = row.stream == AudioManager.STREAM_ACCESSIBILITY;
        final boolean isRingStream = row.stream == AudioManager.STREAM_RING;
        final boolean isSystemStream = row.stream == AudioManager.STREAM_SYSTEM;
        final boolean isAlarmStream = row.stream == AudioManager.STREAM_ALARM;
        final boolean isMusicStream = row.stream == AudioManager.STREAM_MUSIC;
        final boolean isNotificationStream = row.stream == AudioManager.STREAM_NOTIFICATION;
        final boolean isVibrate = mState.ringerModeInternal == AudioManager.RINGER_MODE_VIBRATE;
        final boolean isRingVibrate = isRingStream && isVibrate;
        final boolean isRingSilent = isRingStream
                && mState.ringerModeInternal == AudioManager.RINGER_MODE_SILENT;
        final boolean isZenPriorityOnly = mState.zenMode == Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;
        final boolean isZenAlarms = mState.zenMode == Global.ZEN_MODE_ALARMS;
        final boolean isZenNone = mState.zenMode == Global.ZEN_MODE_NO_INTERRUPTIONS;
        final boolean zenMuted =
                isZenAlarms ? (isRingStream || isSystemStream || isNotificationStream)
                : isZenNone ? (isRingStream || isSystemStream || isAlarmStream || isMusicStream || isNotificationStream)
                : isZenPriorityOnly ? ((isAlarmStream && mState.disallowAlarms) ||
                        (isMusicStream && mState.disallowMedia) ||
                        (isRingStream && mState.disallowRinger) ||
                        (isSystemStream && mState.disallowSystem))
                : isVibrate ? isNotificationStream
                : false;

        // update slider max
        final int max = ss.levelMax * 100;
        final boolean maxChanged = max != row.slider.getMax();
        if (maxChanged) {
            row.slider.setMax(max);
        }

        // update header text
        Utils.setText(row.header, getStreamLabelH(row.stream));
        row.slider.setContentDescription(row.header.getText());
        mConfigurableTexts.add(row.header, ss.name);

        // update icon
        final boolean iconEnabled = (mAutomute || ss.muteSupported) && !zenMuted;
        row.icon.setEnabled(iconEnabled);
        row.icon.setAlpha(iconEnabled ? 1 : 0.5f);
        row.icon.setImageTintList(row.stream == mActiveStream ? mActiveSliderTint : mInactiveSliderTint);
        final int iconRes =
                isRingVibrate ? mSysUIR.drawable("ic_volume_ringer_vibrate")
                        : isRingSilent || zenMuted ? row.iconMuteRes
                                : ss.routedToBluetooth
                                        ? isStreamMuted(ss) ? mSysUIR.drawable("ic_volume_media_bt_mute")
                                                : mSysUIR.drawable("ic_volume_media_bt")
                                        : isStreamMuted(ss) ? row.iconMuteRes : row.iconRes;
        Drawable iconResDrawable = mSysUIContext.getDrawable(iconRes);
        row.icon.setImageDrawable(iconResDrawable);
        row.iconState =
                iconRes == mSysUIR.drawable("ic_volume_ringer_vibrate") ? Events.ICON_STATE_VIBRATE
                : (iconRes == mSysUIR.drawable("ic_volume_media_bt_mute") || iconRes == row.iconMuteRes)
                        ? Events.ICON_STATE_MUTE
                : (iconRes == mSysUIR.drawable("ic_volume_media_bt") || iconRes == row.iconRes)
                        ? Events.ICON_STATE_UNMUTE
                : Events.ICON_STATE_UNKNOWN;
        if (iconEnabled) {
            if (isRingStream) {
                if (isRingVibrate) {
                    row.icon.setContentDescription(mSysUIContext.getString(
                            mSysUIR.string("volume_stream_content_description_unmute"),
                            getStreamLabelH(row.stream)));
                } else {
                    if (mController.hasVibrator()) {
                        row.icon.setContentDescription(mSysUIContext.getString(
                                mShowA11yStream
                                        ? mSysUIR.string("volume_stream_content_description_vibrate_a11y")
                                        : mSysUIR.string("volume_stream_content_description_vibrate"),
                                getStreamLabelH(row.stream)));
                    } else {
                        row.icon.setContentDescription(mSysUIContext.getString(
                                mShowA11yStream
                                        ? mSysUIR.string("volume_stream_content_description_mute_a11y")
                                        : mSysUIR.string("volume_stream_content_description_mute"),
                                getStreamLabelH(row.stream)));
                    }
                }
            } else if (isA11yStream) {
                row.icon.setContentDescription(getStreamLabelH(row.stream));
            } else {
                if (ss.muted || mAutomute && ss.level == 0) {
                   row.icon.setContentDescription(mSysUIContext.getString(
                           mSysUIR.string("volume_stream_content_description_unmute"),
                           getStreamLabelH(row.stream)));
                } else {
                    row.icon.setContentDescription(mSysUIContext.getString(
                            mShowA11yStream
                                    ? mSysUIR.string("volume_stream_content_description_mute_a11y")
                                    : mSysUIR.string("volume_stream_content_description_mute"),
                            getStreamLabelH(row.stream)));
                }
            }
        } else {
            row.icon.setContentDescription(getStreamLabelH(row.stream));
        }

        // ensure tracking is disabled if zenMuted
        if (zenMuted) {
            row.tracking = false;
        }

        // update slider
        final boolean enableSlider = !zenMuted;
        final int vlevel = row.ss.muted && (!isRingStream && !zenMuted) ? 0
                : row.ss.level;
        updateVolumeRowSliderH(row, enableSlider, vlevel, maxChanged);
    }

    private boolean isStreamMuted(final StreamState streamState) {
        return (mAutomute && streamState.level == streamState.levelMin) || streamState.muted;
    }

    private void updateVolumeRowSliderTintH(VolumeRow row, boolean isActive) {
        if (isActive && mExpanded) {
            row.slider.requestFocus();
        }
        final ColorStateList tint = isActive && row.slider.isEnabled() ? mActiveSliderTint
                : mInactiveSliderTint;
        row.slider.setProgressTintList(tint);
        row.slider.setThumbTintList(tint);
    }

    private void updateVolumeRowSliderH(VolumeRow row, boolean enable, int vlevel, boolean maxChanged) {
        row.slider.setEnabled(enable);
        updateVolumeRowSliderTintH(row, row.stream == mActiveStream);
        if (row.tracking) {
            return;  // don't update if user is sliding
        }
        final int progress = row.slider.getProgress();
        final int level = getImpliedLevel(row.slider, progress);
        final boolean rowVisible = row.view.getVisibility() == View.VISIBLE;
        final boolean inGracePeriod = (SystemClock.uptimeMillis() - row.userAttempt)
                < USER_ATTEMPT_GRACE_PERIOD;
        mHandler.removeMessages(H.RECHECK, row);
        if (mShowing && rowVisible && inGracePeriod) {
            if (D.BUG) Log.d(TAG, "inGracePeriod");
            mHandler.sendMessageAtTime(mHandler.obtainMessage(H.RECHECK, row),
                    row.userAttempt + USER_ATTEMPT_GRACE_PERIOD);
            return;  // don't update if visible and in grace period
        }
        if (vlevel == level) {
            if (mShowing && rowVisible) {
                return;  // don't clamp if visible
            }
        }
        final int newProgress = vlevel * 100;
        if (progress != newProgress || maxChanged) {
            if (mShowing && rowVisible) {
                // animate!
                if (row.anim != null && row.anim.isRunning()
                        && row.animTargetProgress == newProgress) {
                    return;  // already animating to the target progress
                }
                // start/update animation
                if (row.anim == null) {
                    row.anim = ObjectAnimator.ofInt(row.slider, "progress", progress, newProgress);
                    row.anim.setInterpolator(new DecelerateInterpolator());
                } else {
                    row.anim.cancel();
                    row.anim.setIntValues(progress, newProgress);
                }
                row.animTargetProgress = newProgress;
                row.anim.setDuration(UPDATE_ANIMATION_DURATION);
                row.anim.start();
            } else {
                // update slider directly to clamped value
                if (row.anim != null) {
                    row.anim.cancel();
                }
                row.slider.setProgress(newProgress, true);
            }
        }
    }

    private void recheckH(VolumeRow row) {
        if (row == null) {
            if (D.BUG) Log.d(TAG, "recheckH ALL");
            trimObsoleteH();
            for (VolumeRow r : mRows) {
                updateVolumeRowH(r);
            }
        } else {
            if (D.BUG) Log.d(TAG, "recheckH " + row.stream);
            updateVolumeRowH(row);
        }
    }

    private void setStreamImportantH(int stream, boolean important) {
        for (VolumeRow row : mRows) {
            if (row.stream == stream) {
                row.important = important;
                return;
            }
        }
    }

    private void showSafetyWarningH(int flags) {
        if ((flags & (AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_SHOW_UI_WARNINGS)) != 0
                || mShowing) {
            synchronized (mSafetyWarningLock) {
                if (mSafetyWarning != null) {
                    return;
                }
                mSafetyWarning = new SafetyWarningDialog(mContext, mController.getAudioManager()) {
                    @Override
                    protected void cleanUp() {
                        synchronized (mSafetyWarningLock) {
                            mSafetyWarning = null;
                        }
                        recheckH(null);
                    }
                };
                mSafetyWarning.show();
            }
            recheckH(null);
        }
        rescheduleTimeoutH();
    }

    private String getStreamLabelH(int stream) {
        return mSysUIContext.getString(getLabelResForStream(stream));
    }

    private int getLabelResForStream(int stream) {
        switch(stream) {
            case AudioSystem.STREAM_ALARM:
                return mSysUIR.string("stream_alarm");
            case AudioSystem.STREAM_BLUETOOTH_SCO:
                return mSysUIR.string("stream_bluetooth_sco");
            case AudioSystem.STREAM_DTMF:
                return mSysUIR.string("stream_dtmf");
            case AudioSystem.STREAM_MUSIC:
                return mSysUIR.string("stream_music");
            case AudioSystem.STREAM_ACCESSIBILITY:
                return mSysUIR.string("stream_accessibility");
            case AudioSystem.STREAM_NOTIFICATION:
                return mSysUIR.string("stream_notification");
            case AudioSystem.STREAM_RING:
                return mSysUIR.string("stream_ring");
            case AudioSystem.STREAM_SYSTEM:
                return mSysUIR.string("stream_system");
            case AudioSystem.STREAM_SYSTEM_ENFORCED:
                return mSysUIR.string("stream_system_enforced");
            case AudioSystem.STREAM_TTS:
                return mSysUIR.string("stream_tts");
            case AudioSystem.STREAM_VOICE_CALL:
                return mSysUIR.string("stream_voice_call");
            default:
                return mSysUIR.string("stream_system");
        }
    }

    private AutoTransition getTransition() {
        AutoTransition transition = new AutoTransition();
        transition.setDuration(mExpandButtonAnimationDuration);
        transition.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
        transition.addListener(new Transition.TransitionListener() {
            @Override
            public void onTransitionStart(Transition transition) {
            }

            @Override
            public void onTransitionEnd(Transition transition) {
                updateWindowWidthH();
            }

            @Override
            public void onTransitionCancel(Transition transition) {
            }

            @Override
            public void onTransitionPause(Transition transition) {
                updateWindowWidthH();
            }

            @Override
            public void onTransitionResume(Transition transition) {
            }
        });
        return transition;
    }

    private boolean hasTouchFeature() {
        final PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
    }

    private final VolumeDialogController.Callbacks mControllerCallbackH
            = new VolumeDialogController.Callbacks() {
        @Override
        public void onShowRequested(int reason) {
            showH(reason);
        }

        @Override
        public void onDismissRequested(int reason) {
            dismissH(reason);
        }

        @Override
        public void onScreenOff() {
            dismissH(Events.DISMISS_REASON_SCREEN_OFF);
        }

        @Override
        public void onStateChanged(State state) {
            onStateChangedH(state);
        }

        @Override
        public void onLayoutDirectionChanged(int layoutDirection) {
            mDialogView.setLayoutDirection(layoutDirection);
        }

        @Override
        public void onConfigurationChanged() {
            Configuration newConfig = mContext.getResources().getConfiguration();
            final int density = newConfig.densityDpi;
            if (mDialog.isShown()) mWindowManager.removeViewImmediate(mDialog);
            initDialog();
            if (density != mDensity) {
                mDensity = density;
            }
            updateWindowWidthH();
            mConfigurableTexts.update();
        }

        @Override
        public void onShowVibrateHint() {
            if (mSilentMode) {
                mController.setRingerMode(AudioManager.RINGER_MODE_SILENT, false);
            }
        }

        @Override
        public void onShowSilentHint() {
            if (mSilentMode) {
                mController.setRingerMode(AudioManager.RINGER_MODE_NORMAL, false);
            }
        }

        @Override
        public void onShowSafetyWarning(int flags) {
            showSafetyWarningH(flags);
        }

        @Override
        public void onAccessibilityModeChanged(Boolean showA11yStream) {
            boolean show = showA11yStream == null ? false : showA11yStream;
            mShowA11yStream = show;
            VolumeRow activeRow = getActiveRow();
            if (!mShowA11yStream && AudioManager.STREAM_ACCESSIBILITY == activeRow.stream) {
                dismissH(Events.DISMISS_STREAM_GONE);
            } else {
                updateRowsH(activeRow);
            }

        }

        @Override
        public void onCaptionComponentStateChanged(
                Boolean isComponentEnabled, Boolean fromTooltip) {}
    };

    private final OnClickListener mClickExpand = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mExpandButtonAnimationRunning) return;
            final boolean newExpand = !mExpanded;
            Events.writeEvent(Events.EVENT_EXPAND, newExpand);
            updateExpandedH(newExpand, false /* dismissing */);
        }
    };

    private final class H extends Handler {
        private static final int SHOW = 1;
        private static final int DISMISS = 2;
        private static final int RECHECK = 3;
        private static final int RECHECK_ALL = 4;
        private static final int SET_STREAM_IMPORTANT = 5;
        private static final int RESCHEDULE_TIMEOUT = 6;
        private static final int STATE_CHANGED = 7;
        private static final int UPDATE_BOTTOM_MARGIN = 8;
        private static final int UPDATE_FOOTER = 9;

        public H() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW: showH(msg.arg1); break;
                case DISMISS: dismissH(msg.arg1); break;
                case RECHECK: recheckH((VolumeRow) msg.obj); break;
                case RECHECK_ALL: recheckH(null); break;
                case SET_STREAM_IMPORTANT: setStreamImportantH(msg.arg1, msg.arg2 != 0); break;
                case RESCHEDULE_TIMEOUT: rescheduleTimeoutH(); break;
                case STATE_CHANGED: onStateChangedH(mState); break;
                case UPDATE_BOTTOM_MARGIN: updateDialogBottomMarginH(); break;
            }
        }
    }

    private final class VolumeSeekBarChangeListener implements OnSeekBarChangeListener {
        private final VolumeRow mRow;

        private VolumeSeekBarChangeListener(VolumeRow row) {
            mRow = row;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            rescheduleTimeoutH();
            if (mRow.ss == null) return;
            if (D.BUG) Log.d(TAG, AudioSystem.streamToString(mRow.stream)
                    + " onProgressChanged " + progress + " fromUser=" + fromUser);
            if (!fromUser) return;
            if (mRow.ss.levelMin > 0) {
                final int minProgress = mRow.ss.levelMin * 100;
                if (progress < minProgress) {
                    seekBar.setProgress(minProgress);
                    progress = minProgress;
                }
            }
            final int userLevel = getImpliedLevel(seekBar, progress);
            if (mRow.ss.level != userLevel || mRow.ss.muted && userLevel > 0) {
                mRow.userAttempt = SystemClock.uptimeMillis();
                if (mRow.requestedLevel != userLevel) {
                    mController.setStreamVolume(mRow.stream, userLevel);
                    mRow.requestedLevel = userLevel;
                    Events.writeEvent(Events.EVENT_TOUCH_LEVEL_CHANGED, mRow.stream,
                            userLevel);
                }
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            if (D.BUG) Log.d(TAG, "onStartTrackingTouch"+ " " + mRow.stream);
            mController.setActiveStream(mRow.stream);
            mRow.tracking = true;
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (D.BUG) Log.d(TAG, "onStopTrackingTouch"+ " " + mRow.stream);
            mRow.tracking = false;
            mRow.userAttempt = SystemClock.uptimeMillis();
            final int userLevel = getImpliedLevel(seekBar, seekBar.getProgress());
            Events.writeEvent(Events.EVENT_TOUCH_LEVEL_DONE, mRow.stream, userLevel);
            if (mRow.ss.level != userLevel) {
                mHandler.sendMessageDelayed(mHandler.obtainMessage(H.RECHECK, mRow),
                        USER_ATTEMPT_GRACE_PERIOD);
            }
        }
    }

    private final class Accessibility extends AccessibilityDelegate {
        private boolean mFeedbackEnabled;

        public void init() {
            mDialogView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewDetachedFromWindow(View v) {
                    if (D.BUG) Log.d(TAG, "onViewDetachedFromWindow");
                }

                @Override
                public void onViewAttachedToWindow(View v) {
                    if (D.BUG) Log.d(TAG, "onViewAttachedToWindow");
                    updateFeedbackEnabled();
                }
            });
            mDialogView.setAccessibilityDelegate(this);
            mAccessibilityMgr.addAccessibilityStateChangeListener(mListener);
            updateFeedbackEnabled();
        }

        public void destroy() {
            mAccessibilityMgr.removeAccessibilityStateChangeListener(mListener);
        }

        @Override
        public boolean onRequestSendAccessibilityEvent(ViewGroup host, View child,
                AccessibilityEvent event) {
            rescheduleTimeoutH();
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }

        private void updateFeedbackEnabled() {
            mFeedbackEnabled = computeFeedbackEnabled();
        }

        private boolean computeFeedbackEnabled() {
            // are there any enabled non-generic a11y services?
            final List<AccessibilityServiceInfo> services =
                    mAccessibilityMgr.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo asi : services) {
                if (asi.feedbackType != 0 && asi.feedbackType != FEEDBACK_GENERIC) {
                    return true;
                }
            }
            return false;
        }

        private final AccessibilityStateChangeListener mListener =
                enabled -> updateFeedbackEnabled();
    }

    private static class VolumeRow {
        private View view;
        private TextView header;
        private ImageButton icon;
        private SeekBar slider;
        private int stream;
        private StreamState ss;
        private long userAttempt;  // last user-driven slider change
        private boolean tracking;  // tracking slider touch
        private int requestedLevel = -1;  // pending user-requested level via progress changed
        private int iconRes;
        private int iconMuteRes;
        private boolean important;
        private int cachedIconRes;
        private ColorStateList cachedSliderTint;
        private int iconState;  // from Events
        private ObjectAnimator anim;  // slider progress animation for non-touch-related updates
        private int animTargetProgress;
        private int lastAudibleLevel = 1;
    }
}
