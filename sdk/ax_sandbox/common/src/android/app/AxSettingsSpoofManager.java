package android.app;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.Context;
import android.os.RemoteException;

import java.util.Collections;
import java.util.List;

public class AxSettingsSpoofManager {

    private static volatile AxSettingsSpoofManager sInstance;

    public static AxSettingsSpoofManager getInstance() {
        if (sInstance == null) {
            synchronized (AxSettingsSpoofManager.class) {
                if (sInstance == null) {
                    sInstance = new AxSettingsSpoofManager();
                }
            }
        }
        return sInstance;
    }

    public AxSettingsSpoofManager() {
    }

    public AxSettingsSpoofManager(Context context) {
    }

    public boolean isSpoofSettingEnabled(String packageName, String settingKey, int userId) {
        try {
            return getService().isSandboxSpoofSettingEnabled(packageName, settingKey, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void setSpoofSettingEnabled(String packageName, String settingKey, boolean enabled, int userId) {
        try {
            getService().setSandboxSpoofSettingEnabled(packageName, settingKey, enabled, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public List<String> getEnabledSpoofSettings(String packageName, int userId) {
        try {
            List<String> result = getService().getSandboxEnabledSpoofSettings(packageName, userId);
            return result != null ? result : Collections.emptyList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public String getSpoofedSetting(String callingPackage, String settingName, int userId) {
        try {
            return getService().getSandboxSpoofedSetting(callingPackage, settingName, userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private IActivityManager getService() {
        return ActivityManager.getService();
    }
}
