package com.unityagent.product.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of inspecting a directory path on the local filesystem for Unity project structure.
 */
public class UnityProjectScanResult {
    private boolean valid;
    private String projectPath;
    private String projectName;
    private String unityVersion;
    private boolean compatible;
    private boolean extensionInstalled;
    private String extensionVersion;
    private String existingProjectId;
    private boolean emptyProject;
    private List<String> warnings = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public UnityProjectScanResult() {}

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getUnityVersion() {
        return unityVersion;
    }

    public void setUnityVersion(String unityVersion) {
        this.unityVersion = unityVersion;
    }

    public boolean isCompatible() {
        return compatible;
    }

    public void setCompatible(boolean compatible) {
        this.compatible = compatible;
    }

    public boolean isExtensionInstalled() {
        return extensionInstalled;
    }

    public void setExtensionInstalled(boolean extensionInstalled) {
        this.extensionInstalled = extensionInstalled;
    }

    public String getExtensionVersion() {
        return extensionVersion;
    }

    public void setExtensionVersion(String extensionVersion) {
        this.extensionVersion = extensionVersion;
    }

    public String getExistingProjectId() {
        return existingProjectId;
    }

    public void setExistingProjectId(String existingProjectId) {
        this.existingProjectId = existingProjectId;
    }

    public boolean isEmptyProject() {
        return emptyProject;
    }

    public void setEmptyProject(boolean emptyProject) {
        this.emptyProject = emptyProject;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
