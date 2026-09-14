package com.android.server.wm;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.UserHandle;

import com.android.internal.app.HiddenNotificationInfo;
import com.android.internal.app.IAppLockStateListener;
import com.android.internal.app.IAppSessionListener;
import com.android.internal.app.IHiddenNotificationListener;
import com.android.server.SystemService;
import com.android.server.wm.ActivityRecord;
import com.android.server.wm.ActivityTaskManagerService;
import com.android.server.wm.Task;
import com.android.server.wm.sandbox.apphide.AppHideRepository;
import com.android.server.wm.sandbox.apphide.AppHideService;
import com.android.server.wm.sandbox.apphide.HiddenNotificationController;
import com.android.server.wm.sandbox.applock.AppLockRepository;
import com.android.server.wm.sandbox.isolation.SandboxIsolationRepository;
import com.android.server.wm.sandbox.isolation.SandboxIsolationService;
import com.android.server.wm.sandbox.spoof.SettingsSpoofRepository;
import com.android.server.wm.sandbox.spoof.SettingsSpoofService;

import java.util.List;
import java.util.Set;

public final class AxSandboxService extends SystemService {

    public static final String SANDBOX_PACKAGE = AppLockService.SANDBOX_PACKAGE;
    public static final String APPLOCKER_PACKAGE = AppLockService.APPLOCKER_PACKAGE;
    public static final Set<String> BLACKLISTED_PACKAGES = AppLockService.BLACKLISTED_PACKAGES;

    private final ActivityTaskManagerService mAtms;
    private final Context mContext;

    private final AppLockRepository mAppLockRepository;
    private final AppHideRepository mAppHideRepository;
    private final SandboxIsolationRepository mIsolationRepository;
    private final SettingsSpoofRepository mSpoofRepository;

    private final AppLockService mAppLockService;
    private final AppHideService mAppHideService;
    private final SandboxIsolationService mIsolationService;
    private final SettingsSpoofService mSpoofService;

    private final BroadcastReceiver mPackageRemovedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())) {
                return;
            }
            Uri data = intent.getData();
            if (data == null) return;
            String packageName = data.getSchemeSpecificPart();
            if (packageName == null) return;

            int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
            int userId = uid >= 0 ? UserHandle.getUserId(uid) : 0;
            boolean removedForAll = intent.getBooleanExtra(Intent.EXTRA_REMOVED_FOR_ALL_USERS, false);

            mAppLockService.removeLockedApp(packageName, userId);
            mAppHideService.setPackageHidden(packageName, false, userId);
            mIsolationService.removeSandboxedPackage(packageName, userId);
            if (removedForAll || userId == 0) {
                mAppLockService.removeLockedApp(packageName, 999);
                mAppHideService.setPackageHidden(packageName, false, 999);
                mIsolationService.removeSandboxedPackage(packageName, 999);
            }
            mAppHideService.clearNotificationsForPackage(packageName);
        }
    };

    private static volatile AxSandboxService sInstance;

    public static AxSandboxService get() {
        return sInstance;
    }

    public static AxSandboxService getInstance() {
        return sInstance;
    }

    public AxSandboxService(Context context, ActivityTaskManagerService atms) {
        super(context);
        mContext = context;
        mAtms = atms;
        sInstance = this;

        mAppLockRepository = new AppLockRepository(mContext);
        mAppHideRepository = new AppHideRepository(mContext);
        mIsolationRepository = new SandboxIsolationRepository(mContext);
        mSpoofRepository = new SettingsSpoofRepository(mContext);

        HiddenNotificationController notifController = new HiddenNotificationController();
        mAppHideService = new AppHideService(mContext, mAppHideRepository, notifController);
        mAppLockService = new AppLockService(mContext, mAtms, mAppLockRepository);
        mIsolationService = new SandboxIsolationService(mIsolationRepository);
        mSpoofService = new SettingsSpoofService(mSpoofRepository);
    }

    @Override
    public void onStart() {
        sInstance = this;
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_ACTIVITY_MANAGER_READY) {
            systemReadyInternal();
        }
    }

    public void systemReadyInternal() {
        mAppLockRepository.init();
        mAppHideRepository.init();
        mIsolationRepository.init();
        mSpoofRepository.init();

        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_FULLY_REMOVED);
            filter.addDataScheme("package");
            mContext.registerReceiverAsUser(mPackageRemovedReceiver, UserHandle.ALL, filter, null, mAtms.mH);
        } catch (Exception ignored) {
        }
    }

    public boolean isAppLocked(String packageName, int userId) {
        return mAppLockService.isAppLocked(packageName, userId);
    }

    public boolean isAppLocked(ActivityRecord r) {
        return mAppLockService.isAppLocked(r);
    }

    public boolean isAppLocked(String packageName, int uid, ComponentName component) {
        return mAppLockService.isAppLocked(packageName, uid, component);
    }

    public int getAppLockState(String packageName, int userId) {
        return mAppLockService.getAppLockState(packageName, userId);
    }

    public boolean hasAppLock(String packageName, int userId) {
        return mAppLockService.hasAppLock(packageName, userId);
    }

    public void addLockedApp(String packageName, int userId) {
        mAppLockService.addLockedApp(packageName, userId);
    }

    public void removeLockedApp(String packageName, int userId) {
        mAppLockService.removeLockedApp(packageName, userId);
    }

    public List<String> getLockedPackages(int userId) {
        return mAppLockService.getLockedPackages(userId);
    }

    public List<String> getLockablePackages(int userId) {
        return mAppLockService.getLockablePackages(userId);
    }

    public boolean isPackageLockable(String packageName, int userId) {
        return mAppLockService.isPackageLockable(packageName, userId);
    }

    public void unlockApp(String packageName, int userId) {
        mAppLockService.unlockApp(packageName, userId);
    }

    public void promptUnlock(String packageName, int userId) {
        mAppLockService.promptUnlock(packageName, userId);
    }

    public boolean checkLockApp(ActivityRecord prev, ActivityRecord next) {
        return mAppLockService.checkLockApp(prev, next);
    }

    public boolean checkUnlockApp(ActivityRecord r, int resultCode, Intent resultData) {
        return mAppLockService.checkUnlockApp(r, resultCode, resultData);
    }

    public void lockTopApp(Task task, String reason) {
        mAppLockService.lockTopApp(task, reason);
    }

    public void onAppFocusChanged(ActivityRecord newFocus, Task newTask) {
        mAppLockService.onAppFocusChanged(newFocus, newTask);
    }

    public void onWindowingModeChanged(Task task, int prevWindowingMode) {
        mAppLockService.onWindowingModeChanged(task, prevWindowingMode);
    }

    public void onAppDied(String packageName, int userId) {
        mAppLockService.onAppDied(packageName, userId);
    }

    public boolean isTopAppLocked(ActivityManager.RecentTaskInfo rti, int topUserId) {
        return mAppLockService.isTopAppLocked(rti, topUserId);
    }

    public void getRecentTasksCheck(int callingUid, int userId) {
        mAppLockService.getRecentTasksCheck(callingUid, userId);
    }

    public void clearUnlockedApp() {
        mAppLockService.clearUnlockedApp();
    }

    public void clearUnlockedApp(ActivityRecord r) {
        mAppLockService.clearUnlockedApp(r);
    }

    public void removeTask(Task task, String reason) {
        mAppLockService.removeTask(task, reason);
    }

    public void setKeyguardDoneLocked(boolean done) {
        mAppLockService.setKeyguardDone(done);
    }

    public boolean isSandboxActivity(ComponentName componentName) {
        return mAppLockService.isSandboxActivity(componentName);
    }

    public boolean isAppLockerActivity(ComponentName componentName) {
        return mAppLockService.isAppLockerActivity(componentName);
    }

    public void registerAppLockStateListener(IAppLockStateListener listener) {
        mAppLockService.registerAppLockStateListener(listener);
    }

    public void unregisterAppLockStateListener(IAppLockStateListener listener) {
        mAppLockService.unregisterAppLockStateListener(listener);
    }

    public void registerAppSessionListener(IAppSessionListener listener) {
        mAppLockService.registerAppSessionListener(listener);
    }

    public void unregisterAppSessionListener(IAppSessionListener listener) {
        mAppLockService.unregisterAppSessionListener(listener);
    }

    public boolean isPackageHidden(String packageName, int userId) {
        return mAppHideService.isPackageHidden(packageName, userId);
    }

    public void setPackageHidden(String packageName, boolean hidden, int userId) {
        mAppHideService.setPackageHidden(packageName, hidden, userId);
    }

    public boolean isPackageHiddenFromLauncher(String packageName, int userId) {
        return mAppHideService.isPackageHiddenFromLauncher(packageName, userId);
    }

    public void setPackageHiddenFromLauncher(String packageName, boolean hidden, int userId) {
        mAppHideService.setPackageHiddenFromLauncher(packageName, hidden, userId);
    }

    public List<String> getHiddenPackages(int userId) {
        return mAppHideService.getHiddenPackages(userId);
    }

    public List<String> getHiddenFromLauncherPackages(int userId) {
        return mAppHideService.getHiddenFromLauncherPackages(userId);
    }

    public void registerHiddenNotificationListener(IHiddenNotificationListener listener) {
        mAppHideService.registerHiddenNotificationListener(listener);
    }

    public void unregisterHiddenNotificationListener(IHiddenNotificationListener listener) {
        mAppHideService.unregisterHiddenNotificationListener(listener);
    }

    public List<HiddenNotificationInfo> getHiddenNotifications() {
        return mAppHideService.getHiddenNotifications();
    }

    public void onHiddenNotificationPosted(HiddenNotificationInfo info) {
        mAppHideService.onHiddenNotificationPosted(info);
    }

    public void onHiddenNotificationRemoved(String key) {
        mAppHideService.onHiddenNotificationRemoved(key);
    }

    public boolean isPackageSandboxed(String packageName, int userId) {
        return mIsolationService.isPackageSandboxed(packageName, userId);
    }

    public void addSandboxedPackage(String packageName, int userId) {
        mIsolationService.addSandboxedPackage(packageName, userId);
    }

    public void removeSandboxedPackage(String packageName, int userId) {
        mIsolationService.removeSandboxedPackage(packageName, userId);
    }

    public List<String> getSandboxedPackages(int userId) {
        return mIsolationService.getSandboxedPackages(userId);
    }

    public void setRestrictedGids(String packageName, int[] gids, int userId) {
        mIsolationService.setRestrictedGids(packageName, gids, userId);
    }

    public int[] getRestrictedGids(String packageName, int userId) {
        return mIsolationService.getRestrictedGids(packageName, userId);
    }

    public boolean isSandboxDataIsolationEnabled(String packageName, int userId) {
        return mIsolationService.isSandboxDataIsolationEnabled(packageName, userId);
    }

    public void setSandboxDataIsolationEnabled(String packageName, boolean enabled, int userId) {
        mIsolationService.setSandboxDataIsolationEnabled(packageName, enabled, userId);
    }

    public boolean isSpoofSettingEnabled(String packageName, String settingKey, int userId) {
        return mSpoofService.isSpoofSettingEnabled(packageName, settingKey, userId);
    }

    public void setSpoofSettingEnabled(String packageName, String settingKey, boolean enabled, int userId) {
        mSpoofService.setSpoofSettingEnabled(packageName, settingKey, enabled, userId);
    }

    public List<String> getEnabledSpoofSettings(String packageName, int userId) {
        return mSpoofService.getEnabledSpoofSettings(packageName, userId);
    }

    public String getSpoofedSetting(String callingPackage, String settingName, int userId) {
        return mSpoofService.getSpoofedSetting(callingPackage, settingName, userId);
    }
}
