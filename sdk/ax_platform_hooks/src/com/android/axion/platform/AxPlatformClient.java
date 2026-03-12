/*
 * Copyright (C) 2025-2026 AxionOS
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

package com.android.axion.platform;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AxPlatformClient {

    private static final String TAG = "AxPlatformClient";

    public static final String ACTION_BIND = "com.android.systemui.action.AX_PLATFORM";
    public static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    public static final String FEATURE_WIFI = "wifi";
    public static final String FEATURE_MOBILE_DATA = "mobile_data";
    public static final String FEATURE_BLUETOOTH = "bluetooth";
    public static final String FEATURE_HOTSPOT = "hotspot";
    public static final String FEATURE_FLASHLIGHT = "flashlight";
    public static final String FEATURE_LOCATION = "location";
    public static final String FEATURE_ROTATION = "rotation";
    public static final String FEATURE_BATTERY_SAVER = "battery_saver";
    public static final String FEATURE_ZEN = "zen";
    public static final String FEATURE_AOD = "aod";
    public static final String FEATURE_DATA_SAVER = "data_saver";
    public static final String FEATURE_AIRPLANE_MODE = "airplane_mode";
    public static final String FEATURE_NFC = "nfc";
    public static final String FEATURE_DARK_MODE = "dark_mode";
    public static final String FEATURE_NIGHT_LIGHT = "night_light";
    public static final String FEATURE_COLOR_INVERSION = "color_inversion";
    public static final String FEATURE_COLOR_CORRECTION = "color_correction";
    public static final String FEATURE_REDUCE_BRIGHTNESS = "reduce_brightness";
    public static final String FEATURE_ONE_HANDED_MODE = "one_handed_mode";
    public static final String FEATURE_HEADS_UP = "heads_up";
    public static final String FEATURE_AUTO_SYNC = "auto_sync";
    public static final String FEATURE_CAMERA_PRIVACY = "camera_privacy";
    public static final String FEATURE_MIC_PRIVACY = "mic_privacy";
    public static final String FEATURE_WORK_PROFILE = "work_profile";
    public static final String FEATURE_USB_TETHER = "usb_tether";
    public static final String FEATURE_DREAM = "dream";
    public static final String FEATURE_READING_MODE = "reading_mode";
    public static final String FEATURE_POWER_SHARE = "power_share";
    public static final String FEATURE_CAFFEINE = "caffeine";
    public static final String FEATURE_VPN = "vpn";
    public static final String FEATURE_CAST = "cast";
    public static final String FEATURE_PROFILES = "profiles";
    public static final String FEATURE_SMART_PIXELS = "smart_pixels";
    public static final String FEATURE_SCREEN_RECORD = "screen_record";
    public static final String FEATURE_SCREENSHOT = "screenshot";

    public static final String KEY_WIFI_SCAN = "wifi_scan";
    public static final String KEY_BATTERY = "battery";
    public static final String KEY_MEDIA = "media";
    public static final String KEY_ALARM = "alarm";
    public static final String KEY_CALENDAR = "calendar";
    public static final String KEY_CONFIG = "config";
    public static final String KEY_DOZE = "doze";
    public static final String KEY_KEYGUARD = "keyguard";
    public static final String KEY_NOW_PLAYING = "now_playing";

    public static final String ACTION_WIFI_CONNECT = "wifi_connect";
    public static final String ACTION_BT_CONNECT = "bt_connect";

    public static final int TILE_STATE_UNAVAILABLE = 0;
    public static final int TILE_STATE_INACTIVE = 1;
    public static final int TILE_STATE_ACTIVE = 2;

    public static final String CATEGORY_CONNECTIVITY = "connectivity";
    public static final String CATEGORY_DISPLAY = "display";
    public static final String CATEGORY_SOUND = "sound";
    public static final String CATEGORY_PRIVACY = "privacy";
    public static final String CATEGORY_POWER = "power";
    public static final String CATEGORY_SYSTEM = "system";

    private static final Map<String, String> SPEC_TO_FEATURE = new HashMap<>();
    private static final Map<String, String> FEATURE_TO_CATEGORY = new HashMap<>();

    static {
        SPEC_TO_FEATURE.put("wifi", FEATURE_WIFI);
        SPEC_TO_FEATURE.put("internet", FEATURE_WIFI);
        SPEC_TO_FEATURE.put("cell", FEATURE_MOBILE_DATA);
        SPEC_TO_FEATURE.put("mobiledata", FEATURE_MOBILE_DATA);
        SPEC_TO_FEATURE.put("bt", FEATURE_BLUETOOTH);
        SPEC_TO_FEATURE.put("bluetooth", FEATURE_BLUETOOTH);
        SPEC_TO_FEATURE.put("hotspot", FEATURE_HOTSPOT);
        SPEC_TO_FEATURE.put("flashlight", FEATURE_FLASHLIGHT);
        SPEC_TO_FEATURE.put("location", FEATURE_LOCATION);
        SPEC_TO_FEATURE.put("rotation", FEATURE_ROTATION);
        SPEC_TO_FEATURE.put("battery", FEATURE_BATTERY_SAVER);
        SPEC_TO_FEATURE.put("saver", FEATURE_BATTERY_SAVER);
        SPEC_TO_FEATURE.put("dnd", FEATURE_ZEN);
        SPEC_TO_FEATURE.put("aod", FEATURE_AOD);
        SPEC_TO_FEATURE.put("ambient_display", FEATURE_AOD);
        SPEC_TO_FEATURE.put("data_saver", FEATURE_DATA_SAVER);
        SPEC_TO_FEATURE.put("airplane", FEATURE_AIRPLANE_MODE);
        SPEC_TO_FEATURE.put("airplane_mode", FEATURE_AIRPLANE_MODE);
        SPEC_TO_FEATURE.put("nfc", FEATURE_NFC);
        SPEC_TO_FEATURE.put("dark", FEATURE_DARK_MODE);
        SPEC_TO_FEATURE.put("dark_mode", FEATURE_DARK_MODE);
        SPEC_TO_FEATURE.put("night", FEATURE_NIGHT_LIGHT);
        SPEC_TO_FEATURE.put("night_light", FEATURE_NIGHT_LIGHT);
        SPEC_TO_FEATURE.put("inversion", FEATURE_COLOR_INVERSION);
        SPEC_TO_FEATURE.put("color_inversion", FEATURE_COLOR_INVERSION);
        SPEC_TO_FEATURE.put("color_correction", FEATURE_COLOR_CORRECTION);
        SPEC_TO_FEATURE.put("reduce_brightness", FEATURE_REDUCE_BRIGHTNESS);
        SPEC_TO_FEATURE.put("onehanded", FEATURE_ONE_HANDED_MODE);
        SPEC_TO_FEATURE.put("one_handed_mode", FEATURE_ONE_HANDED_MODE);
        SPEC_TO_FEATURE.put("heads_up", FEATURE_HEADS_UP);
        SPEC_TO_FEATURE.put("sync", FEATURE_AUTO_SYNC);
        SPEC_TO_FEATURE.put("auto_sync", FEATURE_AUTO_SYNC);
        SPEC_TO_FEATURE.put("camera", FEATURE_CAMERA_PRIVACY);
        SPEC_TO_FEATURE.put("camera_privacy", FEATURE_CAMERA_PRIVACY);
        SPEC_TO_FEATURE.put("cameratoggle", FEATURE_CAMERA_PRIVACY);
        SPEC_TO_FEATURE.put("mic", FEATURE_MIC_PRIVACY);
        SPEC_TO_FEATURE.put("mic_privacy", FEATURE_MIC_PRIVACY);
        SPEC_TO_FEATURE.put("mictoggle", FEATURE_MIC_PRIVACY);
        SPEC_TO_FEATURE.put("work", FEATURE_WORK_PROFILE);
        SPEC_TO_FEATURE.put("work_profile", FEATURE_WORK_PROFILE);
        SPEC_TO_FEATURE.put("usb_tether", FEATURE_USB_TETHER);
        SPEC_TO_FEATURE.put("dream", FEATURE_DREAM);
        SPEC_TO_FEATURE.put("screensaver", FEATURE_DREAM);
        SPEC_TO_FEATURE.put("reading_mode", FEATURE_READING_MODE);
        SPEC_TO_FEATURE.put("power_share", FEATURE_POWER_SHARE);
        SPEC_TO_FEATURE.put("reverse", FEATURE_POWER_SHARE);
        SPEC_TO_FEATURE.put("caffeine", FEATURE_CAFFEINE);
        SPEC_TO_FEATURE.put("vpn", FEATURE_VPN);
        SPEC_TO_FEATURE.put("cast", FEATURE_CAST);
        SPEC_TO_FEATURE.put("profiles", FEATURE_PROFILES);
        SPEC_TO_FEATURE.put("smart_pixels", FEATURE_SMART_PIXELS);
        SPEC_TO_FEATURE.put("screen_record", FEATURE_SCREEN_RECORD);
        SPEC_TO_FEATURE.put("screenrecord", FEATURE_SCREEN_RECORD);
        SPEC_TO_FEATURE.put("screenshot", FEATURE_SCREENSHOT);

        FEATURE_TO_CATEGORY.put(FEATURE_WIFI, CATEGORY_CONNECTIVITY);
        FEATURE_TO_CATEGORY.put(FEATURE_MOBILE_DATA, CATEGORY_CONNECTIVITY);
        FEATURE_TO_CATEGORY.put(FEATURE_BLUETOOTH, CATEGORY_CONNECTIVITY);
        FEATURE_TO_CATEGORY.put(FEATURE_HOTSPOT, CATEGORY_CONNECTIVITY);
        FEATURE_TO_CATEGORY.put(FEATURE_AIRPLANE_MODE, CATEGORY_CONNECTIVITY);
        FEATURE_TO_CATEGORY.put(FEATURE_NFC, CATEGORY_CONNECTIVITY);
        FEATURE_TO_CATEGORY.put(FEATURE_USB_TETHER, CATEGORY_CONNECTIVITY);
        FEATURE_TO_CATEGORY.put(FEATURE_DATA_SAVER, CATEGORY_CONNECTIVITY);
        FEATURE_TO_CATEGORY.put(FEATURE_DARK_MODE, CATEGORY_DISPLAY);
        FEATURE_TO_CATEGORY.put(FEATURE_NIGHT_LIGHT, CATEGORY_DISPLAY);
        FEATURE_TO_CATEGORY.put(FEATURE_COLOR_INVERSION, CATEGORY_DISPLAY);
        FEATURE_TO_CATEGORY.put(FEATURE_COLOR_CORRECTION, CATEGORY_DISPLAY);
        FEATURE_TO_CATEGORY.put(FEATURE_REDUCE_BRIGHTNESS, CATEGORY_DISPLAY);
        FEATURE_TO_CATEGORY.put(FEATURE_ROTATION, CATEGORY_DISPLAY);
        FEATURE_TO_CATEGORY.put(FEATURE_AOD, CATEGORY_DISPLAY);
        FEATURE_TO_CATEGORY.put(FEATURE_READING_MODE, CATEGORY_DISPLAY);
        FEATURE_TO_CATEGORY.put(FEATURE_ZEN, CATEGORY_SOUND);
        FEATURE_TO_CATEGORY.put(FEATURE_HEADS_UP, CATEGORY_SOUND);
        FEATURE_TO_CATEGORY.put(FEATURE_CAMERA_PRIVACY, CATEGORY_PRIVACY);
        FEATURE_TO_CATEGORY.put(FEATURE_MIC_PRIVACY, CATEGORY_PRIVACY);
        FEATURE_TO_CATEGORY.put(FEATURE_WORK_PROFILE, CATEGORY_PRIVACY);
        FEATURE_TO_CATEGORY.put(FEATURE_BATTERY_SAVER, CATEGORY_POWER);
        FEATURE_TO_CATEGORY.put(FEATURE_FLASHLIGHT, CATEGORY_POWER);
        FEATURE_TO_CATEGORY.put(FEATURE_POWER_SHARE, CATEGORY_POWER);
        FEATURE_TO_CATEGORY.put(FEATURE_LOCATION, CATEGORY_SYSTEM);
        FEATURE_TO_CATEGORY.put(FEATURE_AUTO_SYNC, CATEGORY_SYSTEM);
        FEATURE_TO_CATEGORY.put(FEATURE_ONE_HANDED_MODE, CATEGORY_SYSTEM);
        FEATURE_TO_CATEGORY.put(FEATURE_DREAM, CATEGORY_SYSTEM);
        FEATURE_TO_CATEGORY.put(FEATURE_CAFFEINE, CATEGORY_SYSTEM);
        FEATURE_TO_CATEGORY.put(FEATURE_VPN, CATEGORY_CONNECTIVITY);
        FEATURE_TO_CATEGORY.put(FEATURE_CAST, CATEGORY_CONNECTIVITY);
        FEATURE_TO_CATEGORY.put(FEATURE_PROFILES, CATEGORY_SYSTEM);
        FEATURE_TO_CATEGORY.put(FEATURE_SMART_PIXELS, CATEGORY_DISPLAY);
        FEATURE_TO_CATEGORY.put(FEATURE_SCREEN_RECORD, CATEGORY_SYSTEM);
        FEATURE_TO_CATEGORY.put(FEATURE_SCREENSHOT, CATEGORY_SYSTEM);
    }

    @Nullable
    public static String resolveFeature(@NonNull String spec) {
        String feature = SPEC_TO_FEATURE.get(spec.toLowerCase(Locale.ROOT));
        if (feature != null) return feature;
        for (Map.Entry<String, String> entry : SPEC_TO_FEATURE.entrySet()) {
            if (spec.toLowerCase(Locale.ROOT).contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    @Nullable
    public static String getCategory(@NonNull String feature) {
        return FEATURE_TO_CATEGORY.get(feature);
    }

    @NonNull
    public static String[] getFeaturesForCategory(@NonNull String category) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : FEATURE_TO_CATEGORY.entrySet()) {
            if (category.equals(entry.getValue())) result.add(entry.getKey());
        }
        return result.toArray(new String[0]);
    }

    public static int getTileState(@NonNull Bundle state) {
        return state.getInt("tileState", TILE_STATE_INACTIVE);
    }

    @Nullable
    public static String getLabel(@NonNull Bundle state) {
        return state.getString("label");
    }

    @Nullable
    public static String getSecondaryLabel(@NonNull Bundle state) {
        return state.getString("secondaryLabel");
    }

    private static volatile AxPlatformClient sInstance;
    private volatile IAxPlatformService mService;
    private final Object mLock = new Object();
    private Context mContext;
    private boolean mBound;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<Listener, IAxPlatformCallback> mListeners =
            new ConcurrentHashMap<>();
    private final List<IAxPlatformCallback> mRawCallbacks =
            java.util.Collections.synchronizedList(new ArrayList<>());

    public abstract static class Listener {
        public void onUiModeChanged(boolean isDarkMode) {}
        public void onOrientationChanged(int orientation) {}
        public void onFontScaleChanged(float fontScale) {}
        public void onDensityChanged(int densityDpi) {}
        public void onLocaleChanged(String locale) {}
        public void onLayoutDirectionChanged(int layoutDirection) {}
        public void onKeyguardVisibilityChanged(boolean showing) {}
        public void onKeyguardGoingAway(boolean goingAway) {}
        public void onDeviceUnlocked(boolean unlocked) {}
        public void onDozeChanged(boolean dozing) {}
        public void onPulsingChanged(boolean pulsing) {}
        public void onDozeAmountChanged(float linear) {}
        public void onAodChanged(boolean enabled, boolean powerSave) {}
        public void onBatteryLevelChanged(int level, boolean charging, boolean pluggedIn) {}
        public void onPowerSaveChanged(boolean active) {}
        public void onWirelessChargingChanged(boolean wireless) {}
        public void onMediaStateChanged(boolean playing, String track, String artist,
                String packageName) {}
        public void onAlarmChanged(long triggerTime, String packageName) {}
        public void onCalendarChanged(String title, long startTime, long endTime,
                String location) {}
        public void onNowPlayingChanged(String action, Bundle data) {}
        public void onFeatureChanged(String feature, boolean active) {}
        public void onStateChanged(String key, Bundle state) {}
    }

    @FunctionalInterface
    private interface RemoteAction {
        void run(IAxPlatformService service) throws RemoteException;
    }

    @FunctionalInterface
    private interface RemoteQuery<T> {
        T run(IAxPlatformService service) throws RemoteException;
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IAxPlatformService.Stub.asInterface(service);
            Log.d(TAG, "Connected to AxPlatform service");
            reregisterListeners();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            Log.d(TAG, "Disconnected from AxPlatform service");
            scheduleReconnect();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            mService = null;
            mBound = false;
            Log.w(TAG, "Binding died, reconnecting");
            scheduleReconnect();
        }
    };

    private AxPlatformClient() {}

    public static AxPlatformClient getInstance() {
        if (sInstance == null) {
            synchronized (AxPlatformClient.class) {
                if (sInstance == null) {
                    sInstance = new AxPlatformClient();
                }
            }
        }
        return sInstance;
    }

    public void init(Context context) {
        synchronized (mLock) {
            if (mContext != null) return;
            mContext = context.getApplicationContext();
        }
        bind();
    }

    private void bind() {
        synchronized (mLock) {
            if (mBound || mContext == null) return;
            Intent intent = new Intent(ACTION_BIND);
            intent.setPackage(SYSTEMUI_PACKAGE);
            try {
                mBound = mContext.bindService(
                        intent, mConnection,
                        Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
                if (!mBound) {
                    Log.w(TAG, "Failed to bind to AxPlatform service");
                    scheduleReconnect();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "bind", e);
            }
        }
    }

    private void scheduleReconnect() {
        mHandler.removeCallbacksAndMessages(this);
        mHandler.postDelayed(this::bind, this, RECONNECT_DELAY_MS);
    }

    private void reregisterListeners() {
        for (IAxPlatformCallback callback : mListeners.values()) {
            call("reregisterCallback", s -> s.registerCallback(callback));
        }
        synchronized (mRawCallbacks) {
            for (IAxPlatformCallback callback : mRawCallbacks) {
                call("reregisterRawCallback", s -> s.registerCallback(callback));
            }
        }
    }

    private IAxPlatformService getService() {
        return mService;
    }

    private void call(String method, RemoteAction action) {
        try {
            IAxPlatformService service = getService();
            if (service != null) {
                action.run(service);
            } else {
                Log.w(TAG, method + ": service not connected, attempting rebind");
                bind();
            }
        } catch (RemoteException e) {
            Log.e(TAG, method, e);
        }
    }

    private <T> T query(String method, RemoteQuery<T> action, T fallback) {
        try {
            IAxPlatformService service = getService();
            if (service != null) return action.run(service);
        } catch (RemoteException e) {
            Log.e(TAG, method, e);
        }
        return fallback;
    }

    public boolean isAvailable() {
        return getService() != null;
    }

    public void toggle(String feature) {
        call("toggle:" + feature, s -> s.toggle(feature));
    }

    public void setEnabled(String feature, boolean enabled) {
        call("setEnabled:" + feature, s -> s.setEnabled(feature, enabled));
    }

    public void setValue(String feature, int value) {
        call("setValue:" + feature, s -> s.setValue(feature, value));
    }

    public void performAction(String feature, String param) {
        call("performAction:" + feature, s -> s.performAction(feature, param));
    }

    public Bundle getState(String feature) {
        return query("getState:" + feature, s -> s.getState(feature), Bundle.EMPTY);
    }

    public Bundle getAllStates() {
        return query("getAllStates", IAxPlatformService::getAllStates, Bundle.EMPTY);
    }

    public String[] getSupportedFeatures() {
        return query("getSupportedFeatures", IAxPlatformService::getSupportedFeatures, new String[0]);
    }

    public void registerCallback(IAxPlatformCallback callback) {
        mRawCallbacks.add(callback);
        call("registerCallback", s -> s.registerCallback(callback));
    }

    public void unregisterCallback(IAxPlatformCallback callback) {
        mRawCallbacks.remove(callback);
        call("unregisterCallback", s -> s.unregisterCallback(callback));
    }

    public void addListener(Listener listener) {
        if (mListeners.containsKey(listener)) return;
        IAxPlatformCallback callback = new IAxPlatformCallback.Stub() {
            @Override
            public void onStateChanged(String key, Bundle state) {
                mHandler.post(() -> dispatchToListener(listener, key, state));
            }
        };
        mListeners.put(listener, callback);
        registerCallback(callback);
    }

    public void removeListener(Listener listener) {
        IAxPlatformCallback callback = mListeners.remove(listener);
        if (callback != null) unregisterCallback(callback);
    }

    private void dispatchToListener(Listener listener, String key, Bundle state) {
        listener.onStateChanged(key, state);
        switch (key) {
            case KEY_CONFIG:
                listener.onUiModeChanged(state.getBoolean("isDarkMode"));
                listener.onOrientationChanged(state.getInt("orientation", 1));
                listener.onFontScaleChanged(state.getFloat("fontScale", 1.0f));
                listener.onDensityChanged(state.getInt("densityDpi", 0));
                listener.onLocaleChanged(state.getString("locale", ""));
                listener.onLayoutDirectionChanged(state.getInt("layoutDirection", 0));
                break;
            case KEY_KEYGUARD:
                listener.onKeyguardVisibilityChanged(state.getBoolean("isShowing"));
                listener.onKeyguardGoingAway(state.getBoolean("isGoingAway"));
                listener.onDeviceUnlocked(state.getBoolean("isUnlocked"));
                break;
            case KEY_DOZE:
                listener.onDozeChanged(state.getBoolean("isDozing"));
                listener.onPulsingChanged(state.getBoolean("isPulsing"));
                listener.onDozeAmountChanged(state.getFloat("dozeAmount", 0f));
                listener.onAodChanged(
                        state.getBoolean("aodEnabled"),
                        state.getBoolean("isAodPowerSave"));
                break;
            case KEY_BATTERY:
                listener.onBatteryLevelChanged(
                        state.getInt("level", -1),
                        state.getBoolean("isCharging"),
                        state.getBoolean("isPluggedIn"));
                listener.onPowerSaveChanged(state.getBoolean("powerSave"));
                listener.onWirelessChargingChanged(state.getBoolean("wireless"));
                break;
            case KEY_MEDIA:
                listener.onMediaStateChanged(
                        state.getBoolean("isPlaying"),
                        state.getString("track", ""),
                        state.getString("artist", ""),
                        state.getString("packageName", ""));
                break;
            case KEY_ALARM:
                listener.onAlarmChanged(
                        state.getLong("triggerTime", 0L),
                        state.getString("packageName", ""));
                break;
            case KEY_CALENDAR:
                listener.onCalendarChanged(
                        state.getString("title", ""),
                        state.getLong("startTime", 0L),
                        state.getLong("endTime", 0L),
                        state.getString("location", ""));
                break;
            case KEY_NOW_PLAYING:
                listener.onNowPlayingChanged(
                        state.getString("action", ""),
                        state);
                break;
            default:
                if (state.containsKey("active")) {
                    listener.onFeatureChanged(key, state.getBoolean("active"));
                }
                break;
        }
    }

    public void connectWifi(String networkKey) {
        performAction(ACTION_WIFI_CONNECT, networkKey);
    }

    public void connectBluetoothDevice(String address) {
        performAction(ACTION_BT_CONNECT, address);
    }

    public boolean isFeatureActive(String feature) {
        return getState(feature).getBoolean("active", false);
    }

    public boolean isFeatureStarting(String feature) {
        return getState(feature).getBoolean("starting", false);
    }

    public boolean isFeatureAvailable(String feature) {
        return getState(feature).getBoolean("available", true);
    }

    public boolean isDarkMode() {
        return getState(KEY_CONFIG).getBoolean("isDarkMode", false);
    }

    public int getOrientation() {
        return getState(KEY_CONFIG).getInt("orientation", 1);
    }

    public float getFontScale() {
        return getState(KEY_CONFIG).getFloat("fontScale", 1.0f);
    }

    public int getDensityDpi() {
        return getState(KEY_CONFIG).getInt("densityDpi", 0);
    }

    public String getLocale() {
        return getState(KEY_CONFIG).getString("locale", "");
    }

    public int getLayoutDirection() {
        return getState(KEY_CONFIG).getInt("layoutDirection", 0);
    }

    public boolean isKeyguardShowing() {
        return getState(KEY_KEYGUARD).getBoolean("isShowing", false);
    }

    public boolean isKeyguardGoingAway() {
        return getState(KEY_KEYGUARD).getBoolean("isGoingAway", false);
    }

    public boolean isDeviceUnlocked() {
        return getState(KEY_KEYGUARD).getBoolean("isUnlocked", false);
    }

    public boolean isDozing() {
        return getState(KEY_DOZE).getBoolean("isDozing", false);
    }

    public boolean isPulsing() {
        return getState(KEY_DOZE).getBoolean("isPulsing", false);
    }

    public float getDozeAmount() {
        return getState(KEY_DOZE).getFloat("dozeAmount", 0f);
    }

    public boolean isAodEnabled() {
        return getState(KEY_DOZE).getBoolean("aodEnabled", false);
    }

    public boolean isAodPowerSave() {
        return getState(KEY_DOZE).getBoolean("isAodPowerSave", false);
    }

    public int getBatteryLevel() {
        return getState(KEY_BATTERY).getInt("level", -1);
    }

    public boolean isBatteryCharging() {
        return getState(KEY_BATTERY).getBoolean("isCharging", false);
    }

    public boolean isBatteryPluggedIn() {
        return getState(KEY_BATTERY).getBoolean("isPluggedIn", false);
    }

    public boolean isWirelessCharging() {
        return getState(KEY_BATTERY).getBoolean("wireless", false);
    }

    public boolean isPowerSave() {
        return getState(KEY_BATTERY).getBoolean("powerSave", false);
    }

    public boolean isMediaPlaying() {
        return getState(KEY_MEDIA).getBoolean("isPlaying", false);
    }

    public String getMediaTrack() {
        return getState(KEY_MEDIA).getString("track", "");
    }

    public String getMediaArtist() {
        return getState(KEY_MEDIA).getString("artist", "");
    }

    public String getMediaPackage() {
        return getState(KEY_MEDIA).getString("packageName", "");
    }

    public long getNextAlarmTime() {
        return getState(KEY_ALARM).getLong("triggerTime", 0L);
    }

    public String getNextAlarmPackage() {
        return getState(KEY_ALARM).getString("packageName", "");
    }

    private static final long RECONNECT_DELAY_MS = 3000L;
}
