package com.android.server.wm.sandbox.applock;

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

import java.lang.NumberFormatException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AppLockRepository {
    private static final String KEY_LOCKED_PKGS = "locked_pkgs";

    public static final String SETTING_LOCK_BEHAVIOR = "sandbox_locked_app_behavior";
    public static final String SETTING_LOCK_TIMEOUT = "sandbox_locked_app_timeout";
    public static final String SETTING_CHECK_RECENT_TASKS = "sandbox_lock_recent_tasks";

    public static final int LOCK_BEHAVIOR_ON_LEAVE = 0;
    public static final int LOCK_BEHAVIOR_TIMEOUT = 1;
    public static final int LOCK_BEHAVIOR_ON_SCREEN_OFF = 2;
    public static final int LOCK_BEHAVIOR_ON_KILL = 3;

    private final ContentResolver mContentResolver;
    private final Handler mBgHandler;
    private final Set<String> mLockedPackages = ConcurrentHashMap.newKeySet();

    private volatile int mLockBehavior = LOCK_BEHAVIOR_ON_LEAVE;
    private volatile int mLockTimeout = 30;
    private volatile boolean mCheckRecentTasks = false;

    public AppLockRepository(Context context) {
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
        mContentResolver.registerContentObserver(
                Settings.Secure.getUriFor(SETTING_LOCK_BEHAVIOR),
                false, observer, UserHandle.USER_ALL);
        mContentResolver.registerContentObserver(
                Settings.Secure.getUriFor(SETTING_LOCK_TIMEOUT),
                false, observer, UserHandle.USER_ALL);
        mContentResolver.registerContentObserver(
                Settings.Secure.getUriFor(SETTING_CHECK_RECENT_TASKS),
                false, observer, UserHandle.USER_ALL);
        reload();
    }

    public int getLockBehavior() {
        return mLockBehavior;
    }

    public int getLockTimeout() {
        return mLockTimeout;
    }

    public static String toKey(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName)) return "";
        int colon = packageName.indexOf(':');
        if (colon >= 0) {
            return packageName;
        }
        return packageName + ":" + userId;
    }

    public static String getPackageName(String key) {
        if (key == null) return "";
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(0, colon) : key;
    }

    public static int getUserId(String key) {
        if (key == null) return 0;
        int colon = key.indexOf(':');
        if (colon >= 0) {
            try {
                return Integer.parseInt(key.substring(colon + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    public boolean isCheckRecentTasks() {
        return mCheckRecentTasks;
    }

    public boolean isAppLocked(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName)) return false;
        return mLockedPackages.contains(toKey(packageName, userId));
    }

    public boolean hasLockedPackages() {
        return !mLockedPackages.isEmpty();
    }

    public List<String> getLockedPackages(int userId) {
        List<String> result = new ArrayList<>();
        for (String key : mLockedPackages) {
            if (getUserId(key) == userId) {
                result.add(getPackageName(key));
            }
        }
        return result;
    }

    public boolean addLockedApp(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName)) return false;
        boolean changed = mLockedPackages.add(toKey(packageName, userId));
        if (changed) {
            scheduleSave();
        }
        return changed;
    }

    public boolean removeLockedApp(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName)) return false;
        boolean changed = mLockedPackages.remove(toKey(packageName, userId));
        if (changed) {
            scheduleSave();
        }
        return changed;
    }

    private void reload() {
        mLockBehavior = Settings.Secure.getIntForUser(mContentResolver,
                SETTING_LOCK_BEHAVIOR, LOCK_BEHAVIOR_ON_LEAVE, UserHandle.USER_CURRENT);
        mLockTimeout = Settings.Secure.getIntForUser(mContentResolver,
                SETTING_LOCK_TIMEOUT, 30, UserHandle.USER_CURRENT);
        mCheckRecentTasks = Settings.Secure.getIntForUser(mContentResolver,
                SETTING_CHECK_RECENT_TASKS, 0, UserHandle.USER_CURRENT) == 1;

        JSONObject config = SandboxConfigStorage.readConfig(mContentResolver);
        JSONArray arr = config.optJSONArray(KEY_LOCKED_PKGS);
        Set<String> newPkgs = new HashSet<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String entry = arr.optString(i);
                if (!TextUtils.isEmpty(entry)) {
                    newPkgs.add(entry.contains(":") ? entry : entry + ":0");
                }
            }
        }
        mLockedPackages.removeIf(p -> !newPkgs.contains(p));
        mLockedPackages.addAll(newPkgs);
    }

    private void scheduleSave() {
        mBgHandler.post(() -> {
            JSONArray arr = new JSONArray(mLockedPackages);
            SandboxConfigStorage.updateConfig(mContentResolver, config -> {
                try {
                    config.put(KEY_LOCKED_PKGS, arr);
                } catch (JSONException ignored) {
                }
            });
        });
    }
}
