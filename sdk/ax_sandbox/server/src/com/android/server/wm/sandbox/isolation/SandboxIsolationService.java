package com.android.server.wm.sandbox.isolation;

import java.util.List;

public class SandboxIsolationService {
    private static volatile SandboxIsolationService sInstance;

    public static SandboxIsolationService getInstance() {
        return sInstance;
    }

    private final SandboxIsolationRepository mRepository;

    public SandboxIsolationService(SandboxIsolationRepository repository) {
        mRepository = repository;
        sInstance = this;
    }

    public boolean isPackageSandboxed(String packageName, int userId) {
        return mRepository.isPackageSandboxed(packageName, userId);
    }

    public void addSandboxedPackage(String packageName, int userId) {
        mRepository.setPackageSandboxed(packageName, true, userId);
    }

    public void removeSandboxedPackage(String packageName, int userId) {
        mRepository.setPackageSandboxed(packageName, false, userId);
    }

    public List<String> getSandboxedPackages(int userId) {
        return mRepository.getSandboxedPackages(userId);
    }

    public void setRestrictedGids(String packageName, int[] gids, int userId) {
        mRepository.setRestrictedGids(packageName, gids, userId);
    }

    public int[] getRestrictedGids(String packageName, int userId) {
        return mRepository.getRestrictedGids(packageName, userId);
    }

    public boolean isSandboxDataIsolationEnabled(String packageName, int userId) {
        return mRepository.isDataIsolationEnabled(packageName, userId);
    }

    public void setSandboxDataIsolationEnabled(String packageName, boolean enabled, int userId) {
        mRepository.setDataIsolationEnabled(packageName, enabled, userId);
    }

    public boolean isDevOptionsHidden(String packageName, int userId) {
        return mRepository.isDevOptionsHidden(packageName, userId);
    }

    public void setDevOptionsHidden(String packageName, boolean hidden, int userId) {
        mRepository.setDevOptionsHidden(packageName, hidden, userId);
    }

    public List<String> getDevOptionsHiddenPackages(int userId) {
        return mRepository.getDevOptionsHiddenPackages(userId);
    }
}
