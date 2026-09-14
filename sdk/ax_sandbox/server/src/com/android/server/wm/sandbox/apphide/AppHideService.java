package com.android.server.wm.sandbox.apphide;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.text.TextUtils;

import com.android.internal.app.HiddenNotificationInfo;
import com.android.internal.app.IHiddenNotificationListener;
import com.android.server.wm.AppLockService;

import java.util.Collections;
import java.util.List;

public class AppHideService {
    private static volatile AppHideService sInstance;

    public static AppHideService getInstance() {
        return sInstance;
    }

    private final Context mContext;
    private final AppHideRepository mRepository;
    private final HiddenNotificationController mNotificationController;
    private LauncherApps mLauncherApps;

    public AppHideService(Context context, AppHideRepository repository, HiddenNotificationController notificationController) {
        mContext = context;
        mRepository = repository;
        mNotificationController = notificationController;
        if (mNotificationController != null) {
            mNotificationController.setPackageHiddenChecker((pkg) -> mRepository.isPackageHidden(pkg, 0));
        }
        sInstance = this;
    }

    private LauncherApps getLauncherApps() {
        if (mLauncherApps == null && mContext != null) {
            mLauncherApps = mContext.getSystemService(LauncherApps.class);
        }
        return mLauncherApps;
    }

    public boolean isPackageHidden(String packageName, int userId) {
        return mRepository.isPackageHidden(packageName, userId);
    }

    public boolean isPackageHiddenFromLauncher(String packageName, int userId) {
        return mRepository.isPackageHiddenFromLauncher(packageName, userId);
    }

    public void setPackageHidden(String packageName, boolean hidden, int userId) {
        if (hidden && !isPackageLockable(packageName, userId)) return;
        mRepository.setPackageHidden(packageName, hidden, userId);
    }

    public void setPackageHiddenFromLauncher(String packageName, boolean hidden, int userId) {
        if (hidden && !isPackageLockable(packageName, userId)) return;
        mRepository.setPackageHiddenFromLauncher(packageName, hidden, userId);
    }

    public List<String> getHiddenPackages(int userId) {
        return mRepository.getHiddenPackages(userId);
    }

    public List<String> getHiddenFromLauncherPackages(int userId) {
        return mRepository.getHiddenFromLauncherPackages(userId);
    }

    public void registerHiddenNotificationListener(IHiddenNotificationListener listener) {
        if (mNotificationController != null) mNotificationController.registerListener(listener);
    }

    public void unregisterHiddenNotificationListener(IHiddenNotificationListener listener) {
        if (mNotificationController != null) mNotificationController.unregisterListener(listener);
    }

    public List<HiddenNotificationInfo> getHiddenNotifications() {
        return mNotificationController != null ? mNotificationController.getHiddenNotifications() : Collections.emptyList();
    }

    public void onHiddenNotificationPosted(HiddenNotificationInfo info) {
        if (mNotificationController != null) mNotificationController.onHiddenNotificationPosted(info);
    }

    public void onHiddenNotificationRemoved(String key) {
        if (mNotificationController != null) mNotificationController.onHiddenNotificationRemoved(key);
    }

    public void clearNotificationsForPackage(String packageName) {
        if (mNotificationController != null) mNotificationController.clearNotificationsForPackage(packageName);
    }

    public boolean isPackageLockable(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName) || AppLockService.BLACKLISTED_PACKAGES.contains(packageName)) {
            return false;
        }
        if (mRepository.isPackageHidden(packageName, userId) || mRepository.isPackageHiddenFromLauncher(packageName, userId)) {
            return true;
        }
        LauncherApps launcherApps = getLauncherApps();
        if (launcherApps != null) {
            try {
                List<LauncherActivityInfo> activities = launcherApps.getActivityList(
                        packageName, UserHandle.of(userId));
                if (activities != null && !activities.isEmpty()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            PackageManager pm = mContext.getPackageManager();
            return pm != null && pm.getLaunchIntentForPackage(packageName) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
