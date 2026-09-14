package com.unityagent.product.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable build profile defining platform, architecture, settings, and scenes.
 */
public class BuildProfile {
    private String profileName;
    private String platform;
    private String architecture;
    private boolean developmentBuild;
    private String compression = "Default";
    private String scriptingBackend = "Mono";
    private List<String> scenes = new ArrayList<>();
    private String outputDirectory = "Builds";

    public BuildProfile() {}

    public BuildProfile(String profileName, String platform, String architecture,
                        boolean developmentBuild, String compression, String scriptingBackend,
                        List<String> scenes, String outputDirectory) {
        this.profileName = profileName;
        this.platform = platform;
        this.architecture = architecture;
        this.developmentBuild = developmentBuild;
        if (compression != null) this.compression = compression;
        if (scriptingBackend != null) this.scriptingBackend = scriptingBackend;
        if (scenes != null) this.scenes = scenes;
        if (outputDirectory != null) this.outputDirectory = outputDirectory;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public boolean isDevelopmentBuild() {
        return developmentBuild;
    }

    public void setDevelopmentBuild(boolean developmentBuild) {
        this.developmentBuild = developmentBuild;
    }

    public String getCompression() {
        return compression;
    }

    public void setCompression(String compression) {
        this.compression = compression;
    }

    public String getScriptingBackend() {
        return scriptingBackend;
    }

    public void setScriptingBackend(String scriptingBackend) {
        this.scriptingBackend = scriptingBackend;
    }

    public List<String> getScenes() {
        return scenes;
    }

    public void setScenes(List<String> scenes) {
        this.scenes = scenes;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }
}
