package com.android.server.wm;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.AxSandboxManager.AppLockState.LOCKED;
import static android.app.AxSandboxManager.AppLockState.NONE;
import static android.app.AxSandboxManager.AppLockState.UNLOCKED;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AxSandboxManager.AppLockState;
import android.app.WindowConfiguration;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;

import com.android.internal.app.IAppLockStateListener;
import com.android.internal.app.IAppSessionListener;
import com.android.server.wm.ActivityRecord;
import com.android.server.wm.sandbox.applock.AppLockRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AppLockService {
    public static final String SANDBOX_PACKAGE = "com.android.axion.sandbox";
    public static final String APPLOCKER_PACKAGE = "com.android.applocker";
    private static final String APPLOCKER_ACTIVITY = "com.android.applocker.AuthenticateActivity";

    private static final String EXTRA_LOCKED_UID = "LOCKED_UID";
    private static final String EXTRA_LOCKED_PACKAGE = "LOCKED_PACKAGE";
    private static final String EXTRA_LOCKED_COMPONENT = "LOCKED_COMPONENT";
    private static final String EXTRA_USER_ID = "user_id";
    private static final String ACTION_SYSTEM_UNLOCK = "com.android.axion.sandbox.action.SYSTEM_UNLOCK";

    public static final int LOCK_BEHAVIOR_ON_LEAVE = 0;
    public static final int LOCK_BEHAVIOR_TIMEOUT = 1;
    public static final int LOCK_BEHAVIOR_ON_SCREEN_OFF = 2;
    public static final int LOCK_BEHAVIOR_ON_KILL = 3;

    public static final Set<String> BLACKLISTED_PACKAGES = Set.of(
            "com.android.systemui",
            "com.android.launcher3",
            "android",
            "com.android.settings",
            SANDBOX_PACKAGE,
            APPLOCKER_PACKAGE
    );

    private static volatile AppLockService sInstance;

    public static AppLockService getInstance() {
        return sInstance;
    }

    private final Context mContext;
    private final ActivityTaskManagerService mAtms;
    private final AppLockRepository mRepository;
    private LauncherApps mLauncherApps;

    private final RemoteCallbackList<IAppLockStateListener> mAppLockStateListeners = new RemoteCallbackList<>();
    private final RemoteCallbackList<IAppSessionListener> mAppSessionListeners = new RemoteCallbackList<>();

    private final Set<String> mUnlockedApps = ConcurrentHashMap.newKeySet();
    private final Set<String> mPendingUnlocks = ConcurrentHashMap.newKeySet();
    private final Map<String, ActivityRecord> mPendingTargets = new ConcurrentHashMap<>();
    private final Map<String, Long> mUnlockTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Runnable> mTimeoutRunnables = new ConcurrentHashMap<>();

    private Intent mConfirmIntent;
    private ResolveInfo mSandboxResolveInfo;
    private int mRequestCode;
    private int mCurrentUserId = 0;
    private boolean mKeyguardDone = true;
    private String mLastFocusedAppKey = null;
    private int mLastFocusedTaskId = INVALID_TASK_ID;

    public static final int CONFIRM_REQUEST_CODE = 0x0A584C4B;

    public AppLockService(Context context, ActivityTaskManagerService atms, AppLockRepository repository) {
        mContext = context;
        mAtms = atms;
        mRepository = repository;
        mRequestCode = CONFIRM_REQUEST_CODE;
        sInstance = this;
    }

    private LauncherApps getLauncherApps() {
        if (mLauncherApps == null && mContext != null) {
            mLauncherApps = mContext.getSystemService(LauncherApps.class);
        }
        return mLauncherApps;
    }

    public void setKeyguardDone(boolean done) {
        mKeyguardDone = done;
        if (!done && mRepository.getLockBehavior() == LOCK_BEHAVIOR_ON_SCREEN_OFF) {
            clearUnlockedApp();
        }
    }

    public boolean hasAppLock(String packageName, int userId) {
        return computeAppLockState(packageName, userId).hasAppLock();
    }

    public AppLockState computeAppLockState(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName)) return NONE;
        if (BLACKLISTED_PACKAGES.contains(packageName)) return NONE;
        if (!mRepository.isAppLocked(packageName, userId)) return NONE;
        if (!mKeyguardDone) return LOCKED;
        return isSessionUnlocked(packageName, userId) ? UNLOCKED : LOCKED;
    }

    public int getAppLockState(String packageName, int userId) {
        return computeAppLockState(packageName, userId).ordinal();
    }

    public boolean isAppLocked(String packageName, int userId) {
        return computeAppLockState(packageName, userId) == LOCKED;
    }

    public boolean isAppLocked(ActivityRecord r) {
        if (r == null || !hasLockedPackages() || r.isNoDisplay() || r.isActivityTypeHomeOrRecents()) {
            return false;
        }
        ensureResolveInfo(r.mUserId);
        return isAppLocked(r.packageName, r.getUid(), r.mActivityComponent);
    }

    public boolean isAppLocked(String packageName, int uid, ComponentName component) {
        if (TextUtils.isEmpty(packageName)) return false;
        if (BLACKLISTED_PACKAGES.contains(packageName)) return false;
        int userId = UserHandle.getUserId(uid);
        if (!mRepository.isAppLocked(packageName, userId)) return false;
        if (component != null && isAuthActivity(component)) return false;
        return !isSessionUnlocked(packageName, userId);
    }

    public boolean hasLockedPackages() {
        return mRepository.hasLockedPackages();
    }

    public void addLockedApp(String packageName, int userId) {
        if (isPackageLockable(packageName, userId)) {
            if (mRepository.addLockedApp(packageName, userId)) {
                notifyAppLockStateChanged(packageName, true);
            }
        }
    }

    public void removeLockedApp(String packageName, int userId) {
        if (mRepository.removeLockedApp(packageName, userId)) {
            markSessionLocked(packageName, userId);
            notifyAppLockStateChanged(packageName, false);
        }
    }

    public List<String> getLockedPackages(int userId) {
        return mRepository.getLockedPackages(userId);
    }

    public List<String> getLockablePackages(int userId) {
        List<String> result = new ArrayList<>();
        LauncherApps launcherApps = getLauncherApps();
        if (launcherApps != null) {
            try {
                List<LauncherActivityInfo> activities = launcherApps.getActivityList(
                        null, UserHandle.of(userId));
                Set<String> seen = new HashSet<>();
                for (LauncherActivityInfo info : activities) {
                    String pkgName = info.getApplicationInfo().packageName;
                    if (!BLACKLISTED_PACKAGES.contains(pkgName) && !seen.contains(pkgName)) {
                        result.add(pkgName);
                        seen.add(pkgName);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (result.isEmpty()) {
            try {
                PackageManager pm = mContext.getPackageManager();
                if (pm != null) {
                    List<ApplicationInfo> apps = pm.getInstalledApplicationsAsUser(0, userId);
                    for (ApplicationInfo appInfo : apps) {
                        if (BLACKLISTED_PACKAGES.contains(appInfo.packageName)) continue;
                        if (isSystemUid(appInfo.uid)) continue;
                        if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                            result.add(appInfo.packageName);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private static boolean isSystemUid(int uid) {
        int appId = UserHandle.getAppId(uid);
        return appId == Process.ROOT_UID || appId == Process.SYSTEM_UID;
    }

    public boolean isPackageLockable(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName) || BLACKLISTED_PACKAGES.contains(packageName)) {
            return false;
        }
        if (mRepository.isAppLocked(packageName, userId)) {
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

    public void unlockApp(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName)) return;
        markSessionUnlocked(packageName, userId);
        clearPendingUnlock(packageName, userId);
    }

    public void promptUnlock(String packageName, int userId) {
        if (TextUtils.isEmpty(packageName)) return;
        Intent intent = new Intent(getConfirmIntent());
        intent.putExtra(EXTRA_LOCKED_PACKAGE, packageName);
        intent.putExtra(EXTRA_LOCKED_UID, userId);
        intent.putExtra(EXTRA_USER_ID, userId);
        intent.putExtra("app_label", resolveAppLabel(packageName, userId));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        long identity = Binder.clearCallingIdentity();
        try {
            mAtms.getActivityStartController()
                    .obtainStarter(intent, "promptUnlock->AxSandbox")
                    .setCallingUid(0)
                    .setActivityInfo(mSandboxResolveInfo != null ? mSandboxResolveInfo.activityInfo : null)
                    .execute();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public void lockTopApp(Task task, String reason) {
        if (task == null || !hasLockedPackages()) return;
        ActivityRecord r = task.topRunningActivityLocked();
        if (!isAppLocked(r)) return;
        if (startAuthPrompt(r, reason)) {
            hideBlockedTarget(r);
        }
    }

    public boolean checkLockApp(ActivityRecord prev, ActivityRecord next) {
        if (next == null || next.finishing || !next.canBeTopRunning() || !hasLockedPackages()) {
            return false;
        }
        clearUnlockedApp(next);
        if (!isAppLocked(next)) return false;
        if (!startAuthPrompt(next, "lockApp->AxSandbox")) {
            return false;
        }
        if (prev != null && prev.finishing) {
            prev.setVisibility(false);
        }
        hideBlockedTarget(next);
        return true;
    }

    public boolean checkUnlockApp(ActivityRecord r, int resultCode, Intent data) {
        if (r.requestCode != mRequestCode || r.intent == null || !isAuthActivity(r.intent.getComponent())) {
            return false;
        }
        String packageName = getResultPackageName(r, data);
        int userId = getResultUserId(r, data, packageName);
        if (data == null) {
            ActivityRecord target = r.resultTo;
            if (target == null) {
                if (!TextUtils.isEmpty(packageName)) {
                    clearPendingUnlock(packageName, userId);
                }
                return true;
            }
            lockSession(packageName, userId);
            if (!target.finishing) {
                target.finishIfPossible("applock-canceled", false);
            }
            return true;
        }
        try {
            if (resultCode == Activity.RESULT_OK && !TextUtils.isEmpty(packageName)) {
                clearPendingUnlock(packageName, userId);
                markSessionUnlocked(packageName, userId);
            } else if (r.resultTo != null) {
                lockSession(packageName, userId);
                r.resultTo.finishIfPossible("applock-canceled", false);
            } else {
                lockSession(packageName, userId);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getResultPackageName(ActivityRecord r, Intent data) {
        if (data != null) {
            String pkg = data.getStringExtra(EXTRA_LOCKED_PACKAGE);
            if (!TextUtils.isEmpty(pkg)) return pkg;
        }
        return r.intent != null ? r.intent.getStringExtra(EXTRA_LOCKED_PACKAGE) : null;
    }

    private int getResultUserId(ActivityRecord r, Intent data, String packageName) {
        if (data != null) {
            if (data.hasExtra(EXTRA_USER_ID)) {
                return data.getIntExtra(EXTRA_USER_ID, r.mUserId);
            }
            int uid = data.getIntExtra(EXTRA_LOCKED_UID, -1);
            if (uid >= 0) {
                return UserHandle.getUserId(uid);
            }
        }
        if (r.intent != null) {
            if (r.intent.hasExtra(EXTRA_USER_ID)) {
                return r.intent.getIntExtra(EXTRA_USER_ID, r.mUserId);
            }
            int uid = r.intent.getIntExtra(EXTRA_LOCKED_UID, -1);
            if (uid >= 0) {
                return UserHandle.getUserId(uid);
            }
        }
        return r.mUserId;
    }

    private boolean startAuthPrompt(ActivityRecord target, String reason) {
        if (target == null) return false;
        String pendingKey = sessionKey(target);

        if (isSessionUnlocked(target.packageName, target.mUserId)) {
            return true;
        }

        ActivityRecord duplicateTarget = null;
        boolean hideTarget = false;
        if (!mPendingUnlocks.add(pendingKey)) {
            ActivityRecord pendingTarget = mPendingTargets.get(pendingKey);
            boolean hasLivePendingTarget = isLivePendingTarget(pendingTarget, pendingKey);
            if (hasLivePendingTarget || hasPendingAuthPrompt(target, pendingKey)) {
                if (hasLivePendingTarget && pendingTarget != target) {
                    duplicateTarget = target;
                } else {
                    hideTarget = true;
                }
            }
        }
        if (duplicateTarget == null && !hideTarget) {
            mPendingTargets.put(pendingKey, target);
        }
        if (duplicateTarget != null) {
            finishDuplicatePendingTarget(duplicateTarget);
            return true;
        }
        if (hideTarget) {
            hideBlockedTarget(target);
            return true;
        }

        try {
            Intent intent = new Intent(getConfirmIntent());
            intent.putExtra(EXTRA_LOCKED_UID, target.getUid());
            intent.putExtra(EXTRA_LOCKED_PACKAGE, target.packageName);
            intent.putExtra(EXTRA_USER_ID, target.mUserId);
            intent.putExtra(EXTRA_LOCKED_COMPONENT,
                    target.intent.getComponent() != null
                            ? target.intent.getComponent().flattenToString() : "");
            intent.putExtra("app_label", resolveAppLabel(target.packageName, target.mUserId));

            mAtms.getActivityStartController()
                    .obtainStarter(intent, reason)
                    .setCallingUid(0)
                    .setResultTo(target.token)
                    .setRequestCode(mRequestCode)
                    .setActivityInfo(mSandboxResolveInfo != null ? mSandboxResolveInfo.activityInfo : null)
                    .execute();

            hideBlockedTarget(target);
            return true;
        } catch (Exception e) {
            lockSession(target.packageName, target.mUserId);
            if (!target.finishing) {
                target.finishIfPossible("applock-prompt-failed", false);
            }
            return true;
        }
    }

    public void onAppFocusChanged(ActivityRecord newFocus, Task newTask) {
        if (!hasLockedPackages()) {
            mLastFocusedAppKey = null;
            mLastFocusedTaskId = INVALID_TASK_ID;
            return;
        }

        final String newKey = (newFocus != null) ? sessionKey(newFocus) : null;
        final int newTaskId = (newTask != null) ? newTask.mTaskId : INVALID_TASK_ID;

        if (isTransientFocusInUnlockedTask(newFocus, newKey, newTaskId)) {
            cancelTimeoutLock(mLastFocusedAppKey);
            mUnlockTimestamps.put(mLastFocusedAppKey, SystemClock.elapsedRealtime());
            lockTopApp(newTask, "onAppFocusChanged");
            return;
        }

        if (mLastFocusedAppKey != null && !mLastFocusedAppKey.equals(newKey)) {
            scheduleTimeoutLock(mLastFocusedAppKey);
            if (mRepository.getLockBehavior() == LOCK_BEHAVIOR_ON_LEAVE
                    && mUnlockedApps.contains(mLastFocusedAppKey)) {
                lockSessionKey(mLastFocusedAppKey);
            }
        }
        if (newKey != null) {
            cancelTimeoutLock(newKey);
            if (mUnlockedApps.contains(newKey)) {
                mUnlockTimestamps.put(newKey, SystemClock.elapsedRealtime());
            }
        }
        mLastFocusedAppKey = newKey;
        mLastFocusedTaskId = newTaskId;
        lockTopApp(newTask, "onAppFocusChanged");
    }

    private boolean isTransientFocusInUnlockedTask(ActivityRecord newFocus, String newKey,
            int newTaskId) {
        return newFocus != null
                && mLastFocusedAppKey != null
                && !mLastFocusedAppKey.equals(newKey)
                && newTaskId != INVALID_TASK_ID
                && newTaskId == mLastFocusedTaskId
                && mUnlockedApps.contains(mLastFocusedAppKey)
                && !hasAppLock(newFocus.packageName, newFocus.mUserId);
    }

    public void onWindowingModeChanged(Task task, int prevMode) {
        if (task == null || !hasLockedPackages()) return;
        if (mRepository.getLockBehavior() != LOCK_BEHAVIOR_ON_LEAVE) return;

        int currMode = task.getWindowingMode();
        if (!WindowConfiguration.isFloating(prevMode) && WindowConfiguration.isFloating(currMode) && task.isVisible()) {
            ActivityRecord r = task.topRunningActivityLocked();
            if (isAppLocked(r)) {
                markSessionUnlocked(r.packageName, r.mUserId);
            }
            return;
        }
        if (!mUnlockedApps.isEmpty()) {
            lockVisibleMultiWindowApps(task.getDisplayContent());
        }
    }

    public void onAppDied(String packageName, int userId) {
        if (mRepository.getLockBehavior() == LOCK_BEHAVIOR_ON_KILL) {
            markSessionLocked(packageName, userId);
        }
    }

    public boolean isTopAppLocked(ActivityManager.RecentTaskInfo rti, int topUserId) {
        if (!mRepository.isCheckRecentTasks() || rti == null || rti.baseActivity == null || !hasLockedPackages()) {
            return false;
        }
        return isAppLocked(rti.baseActivity.getPackageName(), topUserId, rti.baseActivity);
    }

    public void getRecentTasksCheck(int callingUid, int userId) {
        if (!mRepository.isCheckRecentTasks() || !hasLockedPackages()) return;
        if (mRepository.getLockBehavior() == LOCK_BEHAVIOR_ON_LEAVE) {
            lockAllSessionsAndNotify();
        }
    }

    public void clearUnlockedApp() {
        lockAllSessionsExcept(Collections.emptySet());
    }

    public void clearUnlockedApp(ActivityRecord r) {
        if (r == null) return;
        if (!r.occludesParent() && !r.isActivityTypeHomeOrRecents()) return;
        if (r.isActivityTypeHomeOrRecents() && r.mTransitionController.isTransientLaunch(r)) {
            return;
        }

        final Set<String> keep = new HashSet<>(2);
        final String key = sessionKey(r);
        if (mUnlockedApps.contains(key)) {
            keep.add(key);
        }
        final String ownerKey = getUnlockedTaskOwnerKey(r);
        if (ownerKey != null) {
            keep.add(ownerKey);
        }
        lockAllSessionsExcept(keep);

        final long now = SystemClock.elapsedRealtime();
        for (String k : keep) {
            mUnlockTimestamps.put(k, now);
        }

        if (WindowConfiguration.isFloating(r.getWindowingMode())) {
            lockVisibleFullscreenApps(mAtms.mWindowManager.getDefaultDisplayContentLocked());
        }
    }

    private String getUnlockedTaskOwnerKey(ActivityRecord r) {
        if (r.isActivityTypeHomeOrRecents()) return null;
        final Task task = r.getTask();
        if (task == null) return null;
        final String basePkg = task.getBasePackageName();
        if (TextUtils.isEmpty(basePkg) || basePkg.equals(r.packageName)) return null;
        if (hasAppLock(r.packageName, r.mUserId)) return null;
        final String ownerKey = sessionKey(task.mUserId, basePkg);
        return mUnlockedApps.contains(ownerKey) ? ownerKey : null;
    }

    private boolean isTransientTopOfTaskOwner(ActivityRecord top, String ownerKey) {
        return top != null && ownerKey.equals(getUnlockedTaskOwnerKey(top));
    }

    public void removeTask(Task task, String reason) {
        if (task == null || !hasLockedPackages()) return;
        String pkg = task.getBasePackageName();
        if (pkg != null && isAppLocked(pkg, task.mUserId)) {
            markSessionLocked(pkg, task.mUserId);
        }
    }

    public void cleanupPackage(String packageName, int userId) {
        removeLockedApp(packageName, userId);
        String targetKey = sessionKey(userId, packageName);
        mUnlockedApps.remove(targetKey);
        mUnlockTimestamps.remove(targetKey);
        cancelTimeoutLock(targetKey);
        clearPendingUnlock(packageName, userId);
    }

    public void cleanupPackage(String packageName) {
        cleanupPackage(packageName, 0);
        cleanupPackage(packageName, 999);
    }

    public boolean isSandboxActivity(ComponentName componentName) {
        if (componentName == null) return false;
        return SANDBOX_PACKAGE.equals(componentName.getPackageName());
    }

    public boolean isAppLockerActivity(ComponentName componentName) {
        if (componentName == null) return false;
        return APPLOCKER_PACKAGE.equals(componentName.getPackageName());
    }

    public boolean isAuthActivity(ComponentName componentName) {
        return isSandboxActivity(componentName) || isAppLockerActivity(componentName);
    }

    public void registerAppLockStateListener(IAppLockStateListener listener) {
        if (listener != null) mAppLockStateListeners.register(listener);
    }

    public void unregisterAppLockStateListener(IAppLockStateListener listener) {
        if (listener != null) mAppLockStateListeners.unregister(listener);
    }

    public void registerAppSessionListener(IAppSessionListener listener) {
        if (listener != null) mAppSessionListeners.register(listener);
    }

    public void unregisterAppSessionListener(IAppSessionListener listener) {
        if (listener != null) mAppSessionListeners.unregister(listener);
    }

    public void notifyAppLockStateChanged(String packageName, boolean isLocked) {
        synchronized (mAppLockStateListeners) {
            int count = mAppLockStateListeners.beginBroadcast();
            try {
                for (int i = 0; i < count; i++) {
                    try {
                        mAppLockStateListeners.getBroadcastItem(i)
                                .onAppLockStateChanged(packageName, isLocked);
                    } catch (RemoteException ignored) {
                    }
                }
            } finally {
                mAppLockStateListeners.finishBroadcast();
            }
        }
    }

    private void notifyAppSessionChanged(String packageName, int userId, boolean isUnlocked) {
        synchronized (mAppSessionListeners) {
            int count = mAppSessionListeners.beginBroadcast();
            try {
                for (int i = 0; i < count; i++) {
                    try {
                        if (isUnlocked) {
                            mAppSessionListeners.getBroadcastItem(i).onAppUnlocked(packageName, userId);
                        } else {
                            mAppSessionListeners.getBroadcastItem(i).onAppLocked(packageName, userId);
                        }
                    } catch (RemoteException ignored) {
                    }
                }
            } finally {
                mAppSessionListeners.finishBroadcast();
            }
        }
    }

    private boolean isSessionUnlocked(String packageName, int userId) {
        String key = sessionKey(userId, packageName);
        if (!mUnlockedApps.contains(key)) {
            return false;
        }
        if (mRepository.getLockBehavior() != LOCK_BEHAVIOR_TIMEOUT) {
            return true;
        }
        Long lastUsed = mUnlockTimestamps.get(key);
        if (lastUsed == null || (SystemClock.elapsedRealtime() - lastUsed) <= (mRepository.getLockTimeout() * 1000L)) {
            return true;
        }
        if (isActiveTopApp(packageName, userId)) {
            mUnlockTimestamps.put(key, SystemClock.elapsedRealtime());
            return true;
        }
        return false;
    }

    private boolean isActiveTopApp(String packageName, int userId) {
        synchronized (mAtms.mGlobalLock) {
            ActivityRecord top = mAtms.mRootWindowContainer.getTopResumedActivity();
            return top != null && top.mUserId == userId && TextUtils.equals(top.packageName, packageName);
        }
    }

    private void markSessionUnlocked(String packageName, int userId) {
        String key = sessionKey(userId, packageName);
        if (mUnlockedApps.add(key)) {
            mUnlockTimestamps.put(key, SystemClock.elapsedRealtime());
            notifyAppSessionChanged(packageName, userId, true);
        }
    }

    public void markSessionLocked(String packageName, int userId) {
        String key = sessionKey(userId, packageName);
        if (mUnlockedApps.remove(key)) {
            mUnlockTimestamps.remove(key);
            cancelTimeoutLock(key);
            notifyAppSessionChanged(packageName, userId, false);
        }
    }

    private void lockSession(String packageName, int userId) {
        clearPendingUnlock(packageName, userId);
        markSessionLocked(packageName, userId);
    }

    private void lockAllSessionsAndNotify() {
        lockAllSessionsExcept(Collections.emptySet());
    }

    private void lockAllSessionsExcept(Set<String> keep) {
        for (String key : mUnlockedApps.toArray(new String[0])) {
            if (keep.contains(key)) {
                cancelTimeoutLock(key);
                continue;
            }
            if (mUnlockedApps.remove(key)) {
                mUnlockTimestamps.remove(key);
                cancelTimeoutLock(key);
                notifySessionKeyLocked(key);
            }
        }
        if (keep.isEmpty()) {
            clearAllTimeouts();
        }
        mUnlockTimestamps.keySet().retainAll(mUnlockedApps);
    }

    private void lockSessionKey(String key) {
        int colon = key.indexOf(':');
        if (colon <= 0 || colon >= key.length() - 1) return;
        try {
            markSessionLocked(key.substring(colon + 1), Integer.parseInt(key.substring(0, colon)));
        } catch (NumberFormatException ignored) {
        }
    }

    private void notifySessionKeyLocked(String key) {
        int colon = key.indexOf(':');
        if (colon <= 0 || colon >= key.length() - 1) return;
        try {
            notifyAppSessionChanged(key.substring(colon + 1),
                    Integer.parseInt(key.substring(0, colon)), false);
        } catch (NumberFormatException ignored) {
        }
    }

    private void scheduleTimeoutLock(String key) {
        if (mRepository.getLockBehavior() != LOCK_BEHAVIOR_TIMEOUT || !mUnlockedApps.contains(key)) {
            return;
        }
        cancelTimeoutLock(key);
        Runnable r = () -> {
            synchronized (mAtms.mGlobalLock) {
                ActivityRecord top = mAtms.mRootWindowContainer.getTopResumedActivity();
                String topKey = (top != null) ? sessionKey(top) : null;
                if (!key.equals(topKey) && !isTransientTopOfTaskOwner(top, key)) {
                    lockSessionKey(key);
                }
                mTimeoutRunnables.remove(key);
            }
        };
        mTimeoutRunnables.put(key, r);
        mAtms.mH.postDelayed(r, mRepository.getLockTimeout() * 1000L);
    }

    private void cancelTimeoutLock(String key) {
        Runnable r = mTimeoutRunnables.remove(key);
        if (r != null) {
            mAtms.mH.removeCallbacks(r);
        }
    }

    private void clearAllTimeouts() {
        for (Runnable r : mTimeoutRunnables.values()) {
            mAtms.mH.removeCallbacks(r);
        }
        mTimeoutRunnables.clear();
    }

    private boolean clearPendingUnlock(String packageName, int userId) {
        String key = sessionKey(userId, packageName);
        boolean pending = mPendingUnlocks.remove(key);
        mPendingTargets.remove(key);
        return pending;
    }

    private boolean hasPendingAuthPrompt(ActivityRecord target, String pendingKey) {
        if (hasAppLockerActivity(target.getTask(), pendingKey)) {
            return true;
        }
        DisplayContent dc = target.mDisplayContent != null
                ? target.mDisplayContent : mAtms.mWindowManager.getDefaultDisplayContentLocked();
        return dc != null && dc.getActivity(r -> isMatchingAuthPrompt(r, pendingKey)) != null;
    }

    private boolean hasAppLockerActivity(Task task, String pendingKey) {
        return task != null && task.getActivity(r -> isMatchingAuthPrompt(r, pendingKey)) != null;
    }

    private boolean isMatchingAuthPrompt(ActivityRecord r, String pendingKey) {
        if (r == null || r.finishing || r.intent == null || !isAuthActivity(r.intent.getComponent())) {
            return false;
        }
        String packageName = r.intent.getStringExtra(EXTRA_LOCKED_PACKAGE);
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        int userId = r.intent.hasExtra(EXTRA_USER_ID)
                ? r.intent.getIntExtra(EXTRA_USER_ID, UserHandle.USER_SYSTEM)
                : getUserIdFromLockedUidExtra(r.intent);
        return TextUtils.equals(pendingKey, sessionKey(userId, packageName));
    }

    private int getUserIdFromLockedUidExtra(Intent intent) {
        int uid = intent.getIntExtra(EXTRA_LOCKED_UID, -1);
        return uid >= 0 ? UserHandle.getUserId(uid) : UserHandle.USER_SYSTEM;
    }

    private boolean isLivePendingTarget(ActivityRecord target, String pendingKey) {
        if (target == null || target.finishing || target.getTask() == null) {
            return false;
        }
        return TextUtils.equals(pendingKey, sessionKey(target));
    }

    private void finishDuplicatePendingTarget(ActivityRecord target) {
        if (target == null || target.finishing) {
            return;
        }
        target.setVisibility(false);
        abortAnimation(target);
        target.finishIfPossible("applock-duplicate-pending", false);
    }

    private void hideBlockedTarget(ActivityRecord target) {
        if (target == null) {
            return;
        }
        if (!target.finishing) {
            target.setVisibility(false);
        }
        abortAnimation(target);
    }

    private void abortAnimation(ActivityRecord r) {
        if (r == null) return;
        try {
            if (r.getOptions() != null && r.getOptions().getRemoteAnimationAdapter() != null) {
                r.getOptions().getRemoteAnimationAdapter().getRunner().onAnimationCancelled();
            }
        } catch (Exception ignored) {
        }
        r.abortAndClearOptionsAnimation();
    }

    private void lockVisibleMultiWindowApps(DisplayContent dc) {
        if (dc == null) {
            dc = mAtms.mWindowManager.getDefaultDisplayContentLocked();
        }
        if (dc == null) return;
        dc.getDefaultTaskDisplayArea().forAllTasks(task -> {
            if (task.isLeafTask()
                    && (WindowConfiguration.inMultiWindowMode(task.getWindowingMode())
                            || WindowConfiguration.isFloating(task.getWindowingMode()))
                    && task.isVisible()) {
                ActivityRecord r = task.topRunningActivityLocked();
                if (isAppLocked(r)) {
                    markSessionUnlocked(r.packageName, r.mUserId);
                }
            }
        });
    }

    private void lockVisibleFullscreenApps(DisplayContent dc) {
        if (dc == null) {
            dc = mAtms.mWindowManager.getDefaultDisplayContentLocked();
        }
        if (dc == null) return;
        dc.getDefaultTaskDisplayArea().forAllTasks(task -> {
            if (task.isLeafTask() && task.getWindowingMode() == WindowConfiguration.WINDOWING_MODE_FULLSCREEN
                    && task.isVisible()) {
                ActivityRecord r = task.topRunningActivityLocked();
                if (isAppLocked(r)) {
                    markSessionUnlocked(r.packageName, r.mUserId);
                }
            }
        });
    }

    private void ensureResolveInfo(int userId) {
        if (mSandboxResolveInfo == null || mCurrentUserId != userId) {
            try {
                List<ResolveInfo> list = mContext.getPackageManager()
                        .queryIntentActivitiesAsUser(getConfirmIntent(), PackageManager.MATCH_SYSTEM_ONLY, userId);
                if (list != null) {
                    for (ResolveInfo ri : list) {
                        if (ri.activityInfo != null && (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                            mSandboxResolveInfo = ri;
                            mCurrentUserId = userId;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private Intent getConfirmIntent() {
        if (mConfirmIntent == null) {
            Intent appLockerIntent = new Intent("com.android.applocker.action.SYSTEM_UNLOCK");
            appLockerIntent.setClassName(APPLOCKER_PACKAGE, APPLOCKER_ACTIVITY);
            appLockerIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

            PackageManager pm = (mContext != null) ? mContext.getPackageManager() : null;
            if (pm != null) {
                try {
                    if (pm.resolveActivity(appLockerIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                        mConfirmIntent = appLockerIntent;
                        return mConfirmIntent;
                    }
                } catch (Exception ignored) {
                }
            }

            Intent sandboxIntent = new Intent(ACTION_SYSTEM_UNLOCK);
            sandboxIntent.setClassName(SANDBOX_PACKAGE, "com.android.axion.sandbox.AuthenticateActivity");
            sandboxIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

            if (pm != null) {
                mConfirmIntent = sandboxIntent;
            }
            return (pm != null) ? sandboxIntent : appLockerIntent;
        }
        return mConfirmIntent;
    }

    private String resolveAppLabel(String packageName, int userId) {
        try {
            PackageManager pm = mContext.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfoAsUser(packageName, 0, userId);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    private int getPackageUid(String packageName) {
        try {
            return mContext.getPackageManager().getApplicationInfo(packageName, 0).uid;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    private static String sessionKey(int userId, String packageName) {
        return userId + ":" + packageName;
    }

    private static String sessionKey(ActivityRecord r) {
        return sessionKey(r.mUserId, r.packageName);
    }
}
