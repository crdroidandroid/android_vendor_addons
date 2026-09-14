package android.app;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.Context;
import android.os.RemoteException;

import com.android.internal.app.IAppLockStateListener;
import com.android.internal.app.IAppSessionListener;

import java.util.Collections;
import java.util.List;

public class AxAppLockManager {

    public static final int LOCK_BEHAVIOR_ON_LEAVE = 0;
    public static final int LOCK_BEHAVIOR_TIMEOUT = 1;
    public static final int LOCK_BEHAVIOR_ON_SCREEN_OFF = 2;
    public static final int LOCK_BEHAVIOR_ON_KILL = 3;

    public static final String SETTING_LOCK_BEHAVIOR = "sandbox_locked_app_behavior";
    public static final String SETTING_LOCK_TIMEOUT = "sandbox_locked_app_timeout";

    public static final String EXTRA_LOCKED_PACKAGE = "LOCKED_PACKAGE";
    public static final String EXTRA_LOCKED_UID = "LOCKED_UID";
    public static final String EXTRA_NOTIFICATION_APP_LOCKED = "android.app.extra.AX_APP_LOCKED";

    public static final int DEFAULT_LOCK_TIMEOUT = 30;

    public enum AppLockState {
        NONE,
        UNLOCKED,
        LOCKED;

        public boolean hasAppLock() {
            return this != NONE;
        }

        public boolean needsAuth() {
            return this == LOCKED;
        }

        public static AppLockState fromOrdinal(int ordinal) {
            AppLockState[] values = values();
            if (ordinal < 0 || ordinal >= values.length) return NONE;
            return values[ordinal];
        }
    }

    private static volatile AxAppLockManager sInstance;

    public static AxAppLockManager getInstance() {
        if (sInstance == null) {
            synchronized (AxAppLockManager.class) {
                if (sInstance == null) {
                    sInstance = new AxAppLockManager();
                }
            }
        }
        return sInstance;
    }

    public AxAppLockManager() {
    }

    public AxAppLockManager(Context context) {
    }

    public boolean isAppLocked(String packageName, int userId) {
        return getAppLockState(packageName, userId).needsAuth();
    }

    public AppLockState getAppLockState(String packageName, int userId) {
        try {
            return AppLockState.fromOrdinal(getService().getSandboxAppLockState(packageName, userId));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void addLockedApp(String packageName, int userId) {
        try {
            getService().addSandboxLockedApp(packageName, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void removeLockedApp(String packageName, int userId) {
        try {
            getService().removeSandboxLockedApp(packageName, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public List<String> getLockedPackages(int userId) {
        try {
            List<String> result = getService().getSandboxLockedPackages(userId);
            return result != null ? result : Collections.emptyList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public List<String> getLockablePackages(int userId) {
        try {
            List<String> result = getService().getSandboxLockablePackages(userId);
            return result != null ? result : Collections.emptyList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean isPackageLockable(String packageName, int userId) {
        try {
            return getService().isSandboxPackageLockable(packageName, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void unlockApp(String packageName, int userId) {
        try {
            getService().unlockSandboxApp(packageName, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void promptUnlock(String packageName, int userId) {
        try {
            getService().promptSandboxUnlock(packageName, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void registerAppLockStateListener(IAppLockStateListener listener) {
        try {
            getService().registerSandboxAppLockStateListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void unregisterAppLockStateListener(IAppLockStateListener listener) {
        try {
            getService().unregisterSandboxAppLockStateListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void registerAppSessionListener(IAppSessionListener listener) {
        try {
            getService().registerSandboxAppSessionListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void unregisterAppSessionListener(IAppSessionListener listener) {
        try {
            getService().unregisterSandboxAppSessionListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private IActivityManager getService() {
        return ActivityManager.getService();
    }
}
