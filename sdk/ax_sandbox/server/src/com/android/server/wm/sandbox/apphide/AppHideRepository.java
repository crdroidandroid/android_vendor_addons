package com.android.server.wm.sandbox.apphide;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
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

public class AppHideRepository {
    private static final String KEY_HIDDEN_PKGS = "hidden_pkgs";
    private static final String KEY_LAUNCHER_HIDDEN_PKGS = "launcher_hidden_pkgs";

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final Handler mBgHandler;

    private final Set<String> mHiddenPackages = ConcurrentHashMap.newKeySet();
    private final Set<String> mLauncherHiddenPackages = ConcurrentHashMap.newKeySet();

    public AppHideRepository(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mBgHandler = BackgroundThread.getHandler();
    }

    public void init() {
        ContentObserver observer = new ContentObserver(mBgHandler) {
            @Override
            public void onChange(boolean selfChange) {
                loadConfig();
            }
        };
        mContentResolver.registerContentObserver(
                Settings.Secure.getUriFor(SandboxConfigStorage.SETTING_SANDBOX_CONFIG),
                false, observer, UserHandle.USER_ALL);
        loadConfig();
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

    public boolean isPackageHidden(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName)) return false;
        return mHiddenPackages.contains(toKey(packageName, userId));
    }

    public boolean isPackageHiddenFromLauncher(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName)) return false;
        return mLauncherHiddenPackages.contains(toKey(packageName, userId));
    }

    public List<String> getHiddenPackages(int userId) {
        List<String> result = new ArrayList<>();
        for (String key : mHiddenPackages) {
            if (getUserId(key) == userId) {
                result.add(getPackageName(key));
            }
        }
        return result;
    }

    public List<String> getHiddenFromLauncherPackages(int userId) {
        List<String> result = new ArrayList<>();
        for (String key : mLauncherHiddenPackages) {
            if (getUserId(key) == userId) {
                result.add(getPackageName(key));
            }
        }
        return result;
    }

    public Set<String> getAllHiddenPackages(int userId) {
        Set<String> all = new HashSet<>(getHiddenPackages(userId));
        all.addAll(getHiddenFromLauncherPackages(userId));
        return all;
    }

    public boolean setPackageHidden(String packageName, boolean hidden, int userId) {
        if (TextUtils.isEmpty(packageName)) return false;
        String key = toKey(packageName, userId);
        boolean changed = hidden ? mHiddenPackages.add(key) : mHiddenPackages.remove(key);
        if (changed) {
            scheduleSave();
            broadcastPackageChange(packageName, userId);
        }
        return changed;
    }

    public boolean setPackageHiddenFromLauncher(String packageName, boolean hidden, int userId) {
        if (TextUtils.isEmpty(packageName)) return false;
        String key = toKey(packageName, userId);
        boolean changed = hidden ? mLauncherHiddenPackages.add(key) : mLauncherHiddenPackages.remove(key);
        if (changed) {
            scheduleSave();
            broadcastPackageChange(packageName, userId);
        }
        return changed;
    }

    private void loadConfig() {
        JSONObject config = SandboxConfigStorage.readConfig(mContentResolver);
        Set<String> hiddenPkgs = loadSet(config, KEY_HIDDEN_PKGS);
        Set<String> launcherHiddenPkgs = loadSet(config, KEY_LAUNCHER_HIDDEN_PKGS);

        mHiddenPackages.removeIf(p -> !hiddenPkgs.contains(p));
        mHiddenPackages.addAll(hiddenPkgs);
        mLauncherHiddenPackages.removeIf(p -> !launcherHiddenPkgs.contains(p));
        mLauncherHiddenPackages.addAll(launcherHiddenPkgs);
    }

    private Set<String> loadSet(JSONObject config, String key) {
        Set<String> result = new HashSet<>();
        JSONArray arr = config.optJSONArray(key);
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String entry = arr.optString(i);
                if (!TextUtils.isEmpty(entry)) {
                    result.add(entry.contains(":") ? entry : entry + ":0");
                }
            }
        }
        return result;
    }

    private void scheduleSave() {
        mBgHandler.post(() -> {
            JSONArray hiddenArr = new JSONArray(mHiddenPackages);
            JSONArray launcherHiddenArr = new JSONArray(mLauncherHiddenPackages);
            SandboxConfigStorage.updateConfig(mContentResolver, config -> {
                try {
                    config.put(KEY_HIDDEN_PKGS, hiddenArr);
                    config.put(KEY_LAUNCHER_HIDDEN_PKGS, launcherHiddenArr);
                } catch (JSONException ignored) {
                }
            });
        });
    }

    private void broadcastPackageChange(String packageName, int userId) {
        mBgHandler.post(() -> {
            try {
                int uid = mContext.getPackageManager().getApplicationInfoAsUser(packageName, 0, userId).uid;
                Intent intent = new Intent(Intent.ACTION_PACKAGE_CHANGED);
                intent.setData(Uri.fromParts("package", packageName, null));
                intent.putExtra(Intent.EXTRA_UID, uid);
                intent.putExtra(Intent.EXTRA_USER_HANDLE, userId);
                intent.putExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST, new String[]{packageName});
                intent.putExtra(Intent.EXTRA_DONT_KILL_APP, true);
                mContext.sendBroadcastAsUser(intent, UserHandle.of(userId));
            } catch (Exception ignored) {
            }
        });
    }
}
