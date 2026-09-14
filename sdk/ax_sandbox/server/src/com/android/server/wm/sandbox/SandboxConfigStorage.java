package com.android.server.wm.sandbox;

import android.content.ContentResolver;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.function.Consumer;

public final class SandboxConfigStorage {
    private static final String TAG = "AxSandbox.Storage";
    public static final String SETTING_SANDBOX_CONFIG = "sandbox_config";

    private static final Object sFileLock = new Object();

    private SandboxConfigStorage() {
    }

    public static JSONObject readConfig(ContentResolver resolver) {
        synchronized (sFileLock) {
            String str = Settings.Secure.getString(resolver, SETTING_SANDBOX_CONFIG);
            if (TextUtils.isEmpty(str)) {
                return new JSONObject();
            }
            try {
                return new JSONObject(str);
            } catch (JSONException e) {
                Slog.e(TAG, "Failed to parse sandbox_config JSON", e);
                return new JSONObject();
            }
        }
    }

    public static void updateConfig(ContentResolver resolver, Consumer<JSONObject> updater) {
        synchronized (sFileLock) {
            String str = Settings.Secure.getString(resolver, SETTING_SANDBOX_CONFIG);
            JSONObject config;
            try {
                config = TextUtils.isEmpty(str) ? new JSONObject() : new JSONObject(str);
            } catch (JSONException e) {
                config = new JSONObject();
            }
            try {
                updater.accept(config);
                Settings.Secure.putString(resolver, SETTING_SANDBOX_CONFIG, config.toString());
            } catch (Exception e) {
                Slog.e(TAG, "Failed to update sandbox_config JSON", e);
            }
        }
    }
}
