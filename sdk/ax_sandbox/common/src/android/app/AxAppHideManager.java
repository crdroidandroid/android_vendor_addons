package android.app;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.Context;
import android.os.RemoteException;

import com.android.internal.app.HiddenNotificationInfo;
import com.android.internal.app.IHiddenNotificationListener;

import java.util.Collections;
import java.util.List;

public class AxAppHideManager {

    private static volatile AxAppHideManager sInstance;

    public static AxAppHideManager getInstance() {
        if (sInstance == null) {
            synchronized (AxAppHideManager.class) {
                if (sInstance == null) {
                    sInstance = new AxAppHideManager();
                }
            }
        }
        return sInstance;
    }

    public AxAppHideManager() {
    }

    public AxAppHideManager(Context context) {
    }

    public boolean isPackageHidden(String packageName, int userId) {
        try {
            return getService().isSandboxPackageHidden(packageName, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void setPackageHidden(String packageName, boolean hidden, int userId) {
        try {
            getService().setSandboxPackageHidden(packageName, hidden, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public List<String> getHiddenPackages(int userId) {
        try {
            List<String> result = getService().getSandboxHiddenPackages(userId);
            return result != null ? result : Collections.emptyList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean isPackageHiddenFromLauncher(String packageName, int userId) {
        try {
            return getService().isSandboxPackageHiddenFromLauncher(packageName, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void setPackageHiddenFromLauncher(String packageName, boolean hidden, int userId) {
        try {
            getService().setSandboxPackageHiddenFromLauncher(packageName, hidden, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public List<String> getHiddenFromLauncherPackages(int userId) {
        try {
            List<String> result = getService().getSandboxHiddenFromLauncherPackages(userId);
            return result != null ? result : Collections.emptyList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public List<HiddenNotificationInfo> getHiddenNotifications() {
        try {
            List<HiddenNotificationInfo> result = getService().getSandboxHiddenNotifications();
            return result != null ? result : Collections.emptyList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void registerHiddenNotificationListener(IHiddenNotificationListener listener) {
        try {
            getService().registerSandboxHiddenNotificationListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void unregisterHiddenNotificationListener(IHiddenNotificationListener listener) {
        try {
            getService().unregisterSandboxHiddenNotificationListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private IActivityManager getService() {
        return ActivityManager.getService();
    }
}
