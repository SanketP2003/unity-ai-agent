package com.unityagent.product.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable project template metadata for autonomous game creation.
 */
public class ProjectTemplate {
    private String templateId;
    private String name;
    private String unityVersion;
    private List<String> requiredPackages = new ArrayList<>();
    private List<String> defaultScenes = new ArrayList<>();
    private List<String> supportedPlatforms = new ArrayList<>();
    private String version;

    public ProjectTemplate() {}

    public ProjectTemplate(String templateId, String name, String unityVersion, List<String> requiredPackages,
                           List<String> defaultScenes, List<String> supportedPlatforms, String version) {
        this.templateId = templateId;
        this.name = name;
        this.unityVersion = unityVersion;
        if (requiredPackages != null) this.requiredPackages = requiredPackages;
        if (defaultScenes != null) this.defaultScenes = defaultScenes;
        if (supportedPlatforms != null) this.supportedPlatforms = supportedPlatforms;
        this.version = version;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnityVersion() {
        return unityVersion;
    }

    public void setUnityVersion(String unityVersion) {
        this.unityVersion = unityVersion;
    }

    public List<String> getRequiredPackages() {
        return requiredPackages;
    }

    public void setRequiredPackages(List<String> requiredPackages) {
        this.requiredPackages = requiredPackages;
    }

    public List<String> getDefaultScenes() {
        return defaultScenes;
    }

    public void setDefaultScenes(List<String> defaultScenes) {
        this.defaultScenes = defaultScenes;
    }

    public List<String> getSupportedPlatforms() {
        return supportedPlatforms;
    }

    public void setSupportedPlatforms(List<String> supportedPlatforms) {
        this.supportedPlatforms = supportedPlatforms;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
