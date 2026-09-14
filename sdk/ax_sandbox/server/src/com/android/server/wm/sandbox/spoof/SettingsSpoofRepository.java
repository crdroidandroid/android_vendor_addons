package com.android.server.wm.sandbox.spoof;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.internal.os.BackgroundThread;
import com.android.server.wm.sandbox.SandboxConfigStorage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SettingsSpoofRepository {
    public static final String KEY_SPOOF_SETTINGS_MAP = "spoof_settings_map";

    private final ContentResolver mContentResolver;
    private final Handler mBgHandler;
    private final Map<String, Set<String>> mSpoofSettingsMap = new ConcurrentHashMap<>();

    public SettingsSpoofRepository(Context context) {
        mContentResolver = context.getContentResolver();
        mBgHandler = BackgroundThread.getHandler();
    }

    public void init() {
        ContentObserver observer = new ContentObserver(mBgHandler) {
            @Override
            public void onChange(boolean selfChange) {
                reload();
            }
        };
        mContentResolver.registerContentObserver(
                Settings.Secure.getUriFor(SandboxConfigStorage.SETTING_SANDBOX_CONFIG),
                false, observer, UserHandle.USER_ALL);
        reload();
    }

    public static String toKey(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName)) return "";
        int colon = packageName.indexOf(':');
        if (colon >= 0) {
            return packageName;
        }
        return packageName + ":" + userId;
    }

    public boolean isSettingsSpoofEnabled(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName)) return false;
        return mSpoofSettingsMap.containsKey(toKey(packageName, userId));
    }

    public boolean isSpoofSettingEnabled(String packageName, String settingKey, int userId) {
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(settingKey)) return false;
        Set<String> settings = mSpoofSettingsMap.get(toKey(packageName, userId));
        return settings != null && settings.contains(settingKey);
    }

    public boolean setSpoofSettingEnabled(String packageName, String settingKey, boolean enabled, int userId) {
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(settingKey)) return false;
        String key = toKey(packageName, userId);
        boolean changed;
        if (enabled) {
            Set<String> settings = mSpoofSettingsMap.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
            changed = settings.add(settingKey);
        } else {
            Set<String> settings = mSpoofSettingsMap.get(key);
            if (settings == null) return false;
            changed = settings.remove(settingKey);
            if (settings.isEmpty()) {
                mSpoofSettingsMap.remove(key);
            }
        }
        if (changed) {
            scheduleSave();
        }
        return changed;
    }

    public List<String> getEnabledSpoofSettings(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName)) return Collections.emptyList();
        Set<String> settings = mSpoofSettingsMap.get(toKey(packageName, userId));
        if (settings == null || settings.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(settings);
    }

    private void reload() {
        JSONObject config = SandboxConfigStorage.readConfig(mContentResolver);
        JSONObject mapObj = config.optJSONObject(KEY_SPOOF_SETTINGS_MAP);
        if (mapObj == null) {
            mSpoofSettingsMap.clear();
            return;
        }
        Map<String, Set<String>> newMap = new HashMap<>();
        Iterator<String> keys = mapObj.keys();
        while (keys.hasNext()) {
            String rawKey = keys.next();
            String key = rawKey.contains(":") ? rawKey : rawKey + ":0";
            JSONArray arr = mapObj.optJSONArray(rawKey);
            if (arr != null && arr.length() > 0) {
                Set<String> settings = ConcurrentHashMap.newKeySet();
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i);
                    if (!TextUtils.isEmpty(s)) settings.add(s);
                }
                if (!settings.isEmpty()) {
                    newMap.put(key, settings);
                }
            }
        }
        mSpoofSettingsMap.keySet().removeIf(k -> !newMap.containsKey(k));
        mSpoofSettingsMap.putAll(newMap);
    }

    private void scheduleSave() {
        mBgHandler.post(() -> {
            JSONObject mapObj = new JSONObject();
            try {
                for (Map.Entry<String, Set<String>> entry : mSpoofSettingsMap.entrySet()) {
                    mapObj.put(entry.getKey(), new JSONArray(entry.getValue()));
                }
            } catch (JSONException ignored) {
            }
            SandboxConfigStorage.updateConfig(mContentResolver, config -> {
                try {
                    config.put(KEY_SPOOF_SETTINGS_MAP, mapObj);
                } catch (JSONException ignored) {
                }
            });
        });
    }
}
