package com.unityagent.product;

import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ObjectiveValidator;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.product.model.ArtifactStatus;
import com.unityagent.product.model.BuildArtifact;
import com.unityagent.product.model.BuildProfile;
import com.unityagent.product.service.ArtifactManager;
import com.unityagent.product.service.BuildProfileManager;
import com.unityagent.product.service.MultiPlatformBuildPipeline;
import com.unityagent.studio.model.BuildRecord;
import com.unityagent.studio.service.StudioBuildService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Multi-Platform Build Pipeline Tests")
class MultiPlatformBuildTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private BuildProfileManager profileManager;
    private StudioBuildService buildService;
    private ArtifactManager artifactManager;
    private CompletionGate completionGate;
    private ObjectiveValidator objectiveValidator;
    private MultiPlatformBuildPipeline pipeline;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("multi_build_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();

        profileManager = Mockito.mock(BuildProfileManager.class);
        buildService = Mockito.mock(StudioBuildService.class);
        artifactManager = Mockito.mock(ArtifactManager.class);
        completionGate = Mockito.mock(CompletionGate.class);
        objectiveValidator = Mockito.mock(ObjectiveValidator.class);

        pipeline = new MultiPlatformBuildPipeline(
                profileManager, buildService, artifactManager,
                completionGate, objectiveValidator, db
        );
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    @DisplayName("Verify WebGL build pipeline execution through all 5 stages")
    void testWebGLBuildPipeline() throws Exception {
        BuildProfile webglProfile = new BuildProfile("WebGL Release", "WEBGL", "Wasm", false, "Gzip", "IL2CPP",
                List.of("Assets/Scenes/Main.unity"), "Builds/WebGL");

        ValidationReport report = new ValidationReport("webgl-goal");
        report.setCompilationSuccess(true);
        report.setBehaviorTestsPassed(true);
        report.setRuntimeErrorsClean(true);
        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(report);
        when(completionGate.canComplete(any())).thenReturn(true);

        BuildRecord mockRecord = new BuildRecord("b-webgl-1", "proj-webgl", "WEBGL", "WEBGL", "Release",
                BuildRecord.BuildStatus.SUCCEEDED, Instant.now().toString());
        when(buildService.queueBuild(eq("proj-webgl"), eq("WEBGL"), eq("Release"), anyString())).thenReturn(mockRecord);

        BuildArtifact mockArtifact = new BuildArtifact("art-webgl-1", "proj-webgl", null, "WEBGL", "Wasm",
                "Builds/WebGL/index.html", 4096L, "sha256-webgl-hash", ArtifactStatus.VERIFIED, Instant.now());
        when(artifactManager.registerArtifact(anyString(), eq("proj-webgl"), isNull(), eq("WEBGL"), eq("Wasm"), eq(tempDir), anyString()))
                .thenReturn(mockArtifact);

        MultiPlatformBuildPipeline.BuildPipelineResult result = pipeline.executePipeline("proj-webgl", tempDir, webglProfile);
        assertTrue(result.isSuccess());
        assertEquals("SUCCESS", result.getStage());
        assertNotNull(result.getArtifact());
        assertEquals("WEBGL", result.getArtifact().getPlatform());
    }

    @Test
    @DisplayName("Verify Linux build pipeline failure when CompletionGate rejects")
    void testLinuxBuildPipelineFailsGate() {
        BuildProfile linuxProfile = new BuildProfile("Linux Release", "LINUX", "x64", false, "LZ4HC", "Mono",
                List.of("Assets/Scenes/Main.unity"), "Builds/Linux");

        ValidationReport report = new ValidationReport("linux-goal");
        report.setCompilationSuccess(true);
        report.setBehaviorTestsPassed(false);
        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(report);
        when(completionGate.canComplete(any())).thenReturn(false);

        MultiPlatformBuildPipeline.BuildPipelineResult result = pipeline.executePipeline("proj-linux", tempDir, linuxProfile);
        assertFalse(result.isSuccess());
        assertEquals("STAGE_2_COMPILATION_FAILED", result.getStage());
    }
}
