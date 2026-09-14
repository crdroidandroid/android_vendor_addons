package com.android.server.wm.sandbox.spoof;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsSpoofService {
    private static volatile SettingsSpoofService sInstance;

    public static SettingsSpoofService getInstance() {
        return sInstance;
    }

    private static final Map<String, String> STATIC_SPOOFED_SETTINGS = new HashMap<>();

    static {
        STATIC_SPOOFED_SETTINGS.put("adb_enabled", "0");
        STATIC_SPOOFED_SETTINGS.put("development_settings_enabled", "0");
        STATIC_SPOOFED_SETTINGS.put("adb_wifi_enabled", "0");
        STATIC_SPOOFED_SETTINGS.put("package_verifier_user_consent", "0");
        STATIC_SPOOFED_SETTINGS.put("verify_apps_over_usb", "0");
        STATIC_SPOOFED_SETTINGS.put("accessibility_enabled", "0");
        STATIC_SPOOFED_SETTINGS.put("enabled_accessibility_services", "");
        STATIC_SPOOFED_SETTINGS.put("accessibility_display_inversion_enabled", "0");
    }

    private final SettingsSpoofRepository mRepository;

    public SettingsSpoofService(SettingsSpoofRepository repository) {
        mRepository = repository;
        sInstance = this;
    }

    public static String getStaticSpoofedValue(String settingName) {
        if (settingName == null) return null;
        return STATIC_SPOOFED_SETTINGS.get(settingName);
    }

    public boolean isSpoofSettingEnabled(String packageName, String settingKey, int userId) {
        return mRepository.isSpoofSettingEnabled(packageName, settingKey, userId);
    }

    public void setSpoofSettingEnabled(String packageName, String settingKey, boolean enabled, int userId) {
        mRepository.setSpoofSettingEnabled(packageName, settingKey, enabled, userId);
    }

    public List<String> getEnabledSpoofSettings(String packageName, int userId) {
        return mRepository.getEnabledSpoofSettings(packageName, userId);
    }

    public String getSpoofedSetting(String callingPackage, String settingName, int userId) {
        if (TextUtils.isEmpty(callingPackage) || TextUtils.isEmpty(settingName)) {
            return null;
        }
        if (!mRepository.isSpoofSettingEnabled(callingPackage, settingName, userId)) {
            return null;
        }
        return getStaticSpoofedValue(settingName);
    }
}
