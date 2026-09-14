package com.unityagent.product.model;

/**
 * Platform build capability information detected from Unity installation.
 */
public class PlatformCapability {
    private String platform; // WINDOWS, LINUX, ANDROID, WEBGL, OSX, IOS
    private boolean supported;
    private boolean moduleInstalled;
    private boolean buildAvailable;

    public PlatformCapability() {}

    public PlatformCapability(String platform, boolean supported, boolean moduleInstalled, boolean buildAvailable) {
        this.platform = platform;
        this.supported = supported;
        this.moduleInstalled = moduleInstalled;
        this.buildAvailable = buildAvailable;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public boolean isSupported() {
        return supported;
    }

    public void setSupported(boolean supported) {
        this.supported = supported;
    }

    public boolean isModuleInstalled() {
        return moduleInstalled;
    }

    public void setModuleInstalled(boolean moduleInstalled) {
        this.moduleInstalled = moduleInstalled;
    }

    public boolean isBuildAvailable() {
        return buildAvailable;
    }

    public void setBuildAvailable(boolean buildAvailable) {
        this.buildAvailable = buildAvailable;
    }
}
