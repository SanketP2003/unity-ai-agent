package com.unityagent.product.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Detailed validation report required before any release candidate can be approved.
 */
public class ReleaseValidationReport {
    private String releaseId;
    private boolean valid;
    private boolean compilationClean;
    private boolean behaviorPassed;
    private boolean completionGatePassed;
    private boolean artifactVerified;
    private boolean hashVerified;
    private boolean securityAuditPassed;
    private List<String> errors = new ArrayList<>();
    private Instant validatedAt;

    public ReleaseValidationReport() {}

    public ReleaseValidationReport(String releaseId) {
        this.releaseId = releaseId;
        this.validatedAt = Instant.now();
    }

    public String getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(String releaseId) {
        this.releaseId = releaseId;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isCompilationClean() {
        return compilationClean;
    }

    public void setCompilationClean(boolean compilationClean) {
        this.compilationClean = compilationClean;
    }

    public boolean isBehaviorPassed() {
        return behaviorPassed;
    }

    public void setBehaviorPassed(boolean behaviorPassed) {
        this.behaviorPassed = behaviorPassed;
    }

    public boolean isCompletionGatePassed() {
        return completionGatePassed;
    }

    public void setCompletionGatePassed(boolean completionGatePassed) {
        this.completionGatePassed = completionGatePassed;
    }

    public boolean isArtifactVerified() {
        return artifactVerified;
    }

    public void setArtifactVerified(boolean artifactVerified) {
        this.artifactVerified = artifactVerified;
    }

    public boolean isHashVerified() {
        return hashVerified;
    }

    public void setHashVerified(boolean hashVerified) {
        this.hashVerified = hashVerified;
    }

    public boolean isSecurityAuditPassed() {
        return securityAuditPassed;
    }

    public void setSecurityAuditPassed(boolean securityAuditPassed) {
        this.securityAuditPassed = securityAuditPassed;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }

    public void addError(String error) {
        if (error != null) {
            this.errors.add(error);
            this.valid = false;
        }
    }
}
