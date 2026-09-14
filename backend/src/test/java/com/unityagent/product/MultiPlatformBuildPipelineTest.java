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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MultiPlatformBuildPipelineTest {

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
        File dbFile = tempDir.resolve("pipeline_test.db").toFile();
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
    void testPipelineFailsStage1WhenProfileInvalid() {
        BuildProfile profile = new BuildProfile("Invalid", "UNSUPPORTED", "x64", false, "LZ4", "Mono", List.of(), "out");
        doThrow(new IllegalStateException("BUILD_PROFILE_INVALID: Unsupported platform"))
                .when(profileManager).validateProfile(eq("proj-p1"), eq(profile));

        MultiPlatformBuildPipeline.BuildPipelineResult res = pipeline.executePipeline("proj-p1", tempDir, profile);
        assertFalse(res.isSuccess());
        assertEquals("STAGE_1_CONFIG_INVALID", res.getStage());
    }

    @Test
    void testPipelineFailsStage2WhenCompilationNotClean() {
        BuildProfile profile = new BuildProfile("Win", "WINDOWS", "x64", false, "LZ4", "Mono", List.of("Scene.unity"), "out");
        doNothing().when(profileManager).validateProfile(any(), any());

        ValidationReport report = new ValidationReport("goal-p1");
        report.setCompilationSuccess(false);
        report.setBehaviorTestsPassed(false);
        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(report);

        MultiPlatformBuildPipeline.BuildPipelineResult res = pipeline.executePipeline("proj-p1", tempDir, profile);
        assertFalse(res.isSuccess());
        assertEquals("STAGE_2_COMPILATION_FAILED", res.getStage());
    }

    @Test
    void testPipelineSucceedsAll5Stages() throws Exception {
        BuildProfile profile = new BuildProfile("Win", "WINDOWS", "x64", false, "LZ4", "Mono", List.of("Scene.unity"), "Builds/Windows");
        doNothing().when(profileManager).validateProfile(any(), any());

        ValidationReport report = new ValidationReport("goal-p1");
        report.setCompilationSuccess(true);
        report.setBehaviorTestsPassed(true);
        report.setRuntimeErrorsClean(true);
        when(objectiveValidator.validate(any(), any(), eq(true), eq(true), eq(true))).thenReturn(report);
        when(completionGate.canComplete(any())).thenReturn(true);

        BuildRecord mockRecord = new BuildRecord("b-100", "proj-p1", "WINDOWS", "WINDOWS", "Release", BuildRecord.BuildStatus.SUCCEEDED, Instant.now().toString());
        when(buildService.queueBuild(eq("proj-p1"), eq("WINDOWS"), eq("Release"), anyString())).thenReturn(mockRecord);

        BuildArtifact mockArtifact = new BuildArtifact("art-win-1", "proj-p1", null, "WINDOWS", "x64",
                "Builds/Windows/Game.exe", 1024L, "a1b2c3d4e5", ArtifactStatus.VERIFIED, Instant.now());
        when(artifactManager.registerArtifact(anyString(), eq("proj-p1"), isNull(), eq("WINDOWS"), eq("x64"), eq(tempDir), anyString()))
                .thenReturn(mockArtifact);

        MultiPlatformBuildPipeline.BuildPipelineResult res = pipeline.executePipeline("proj-p1", tempDir, profile);
        assertTrue(res.isSuccess());
        assertEquals("SUCCESS", res.getStage());
        assertNotNull(res.getArtifact());
        assertEquals("art-win-1", res.getArtifact().getArtifactId());
    }
}
