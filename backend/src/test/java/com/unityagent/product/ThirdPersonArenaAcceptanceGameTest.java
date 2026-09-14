package com.unityagent.product;

import com.unityagent.agent.security.ScriptSafetyValidator;
import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ValidationReport;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.model.*;
import com.unityagent.product.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Automated Integration Acceptance: Third-Person Arena Game")
class ThirdPersonArenaAcceptanceGameTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private SQLiteMemoryRepository repository;
    private WorkspaceManager workspaceManager;
    private ProjectTemplateManager templateManager;
    private VersionManager versionManager;
    private ArtifactManager artifactManager;
    private ReleaseValidator releaseValidator;
    private ReleaseManager releaseManager;
    private ScriptSafetyValidator scriptSafetyValidator;

    private Path projectRoot;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("arena_acceptance.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);

        workspaceManager = new WorkspaceManager(db);
        templateManager = new ProjectTemplateManager(db);
        templateManager.registerBuiltinTemplates();
        versionManager = new VersionManager(db);
        artifactManager = new ArtifactManager(db);
        scriptSafetyValidator = new ScriptSafetyValidator();

        releaseValidator = mock(ReleaseValidator.class);
        releaseManager = new ReleaseManager(db, releaseValidator, versionManager, artifactManager);

        projectRoot = tempDir.resolve("ArenaProject");
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    @DisplayName("Complete Lifecycle: 3D Third-Person Arena -> 3D Physics -> Melee Hazard Rings -> Release Candidate")
    void testThirdPersonArenaAutonomousLifecycle() throws Exception {
        String projectId = "proj_thirdperson_arena";
        repository.upsertProject(new ProjectMemory(projectId, "Gladiator 3D Arena", "2022.3.20f1", "OSX", "URP", "1.0.0", "fp-arena-01"));

        Files.createDirectories(projectRoot.resolve("Assets/Scripts"));
        Files.createDirectories(projectRoot.resolve("Builds/OSX/GladiatorArena.app/Contents/MacOS"));

        // 1. Template Scaffolding
        ProjectTemplate template = templateManager.getTemplate("tpl-third-person").orElseThrow();
        assertEquals("tpl-third-person", template.getTemplateId());

        Workspace ws = workspaceManager.createWorkspace("ws_arena", "Gladiator Arena Workspace", projectRoot.toString());
        assertNotNull(ws);

        // 2. 3D Character Controller C# Script Synthesis & AST Safety
        String arenaScript = """
                using UnityEngine;
                
                public class ArenaCharacterController : MonoBehaviour
                {
                    public float walkSpeed = 5f;
                    public float sprintSpeed = 9f;
                    public Transform cameraArm;
                    private CharacterController controller;
                    public int stamina = 100;
                    
                    void Start() {
                        controller = GetComponent<CharacterController>();
                    }
                    
                    void Update() {
                        float h = Input.GetAxis("Horizontal");
                        float v = Input.GetAxis("Vertical");
                        Vector3 dir = new Vector3(h, 0, v).normalized;
                        if (dir.magnitude >= 0.1f) {
                            float speed = Input.GetKey(KeyCode.LeftShift) && stamina > 10 ? sprintSpeed : walkSpeed;
                            controller.Move(dir * speed * Time.deltaTime);
                        }
                    }
                }
                """;

        String scriptPath = "Assets/Scripts/ArenaCharacterController.cs";
        ScriptSafetyValidator.ValidationResult safety = scriptSafetyValidator.validate(scriptPath, arenaScript);
        assertTrue(safety.isValid());
        Path diskScript = projectRoot.resolve(scriptPath);
        Files.writeString(diskScript, arenaScript);
        assertTrue(Files.exists(diskScript));

        // 3. Authoritative CompletionGate Behavioral Verification
        ValidationReport report = new ValidationReport("goal_arena");
        report.setTotalRequirements(5);
        report.setRequiredCount(5);
        report.setSatisfiedRequiredCount(5);
        report.setCompilationSuccess(true);
        report.setRuntimeErrorsClean(true);
        report.setBehaviorTestsPassed(true);
        report.setSummary("Hierarchy verified: Player3D, SpringArmCamera, ArenaHazardRings, EnemySpawners. Play Mode passed.");
        assertTrue(CompletionGate.evaluate(report));

        // 4. Physical Binary Artifact Registration (OSX Universal Target)
        Path appBinPath = projectRoot.resolve("Builds/OSX/GladiatorArena.app/Contents/MacOS/GladiatorArena");
        Files.write(appBinPath, new byte[]{(byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE}); // Mach-O 64-bit binary header
        assertTrue(Files.exists(appBinPath));

        String artifactId = "art_arena_osx";
        String releaseId = "rel_arena_v1";
        BuildArtifact artifact = artifactManager.registerArtifact(
                artifactId,
                projectId,
                releaseId,
                "StandaloneOSX",
                "universal",
                projectRoot,
                "Builds/OSX/GladiatorArena.app/Contents/MacOS/GladiatorArena"
        );
        assertNotNull(artifact);
        assertEquals(ArtifactStatus.VERIFIED, artifact.getStatus());

        // 5. Release Candidate & Validation
        Release release = releaseManager.createReleaseCandidate(
                releaseId,
                projectId,
                "1.0.0",
                ReleaseChannel.STABLE,
                "build_run_arena_01",
                "3D Gladiator Arena combat game"
        );
        ReleaseValidationReport valReport = new ReleaseValidationReport(releaseId);
        valReport.setValid(true);
        when(releaseValidator.validateRelease(any(), any(), any())).thenReturn(valReport);

        ReleaseValidationReport valRes = releaseManager.validateReleaseCandidate(releaseId, artifact, projectRoot);
        assertTrue(valRes.isValid());

        // 6. Approval & Publication
        Release approved = releaseManager.approveRelease(
                releaseId,
                "ADMIN",
                "executive_producer",
                "Approved multi-platform macOS universal build"
        );
        assertEquals(ReleaseStatus.APPROVED, approved.getStatus());

        Release published = releaseManager.publishRelease(releaseId, projectId, artifact, projectRoot);
        assertEquals(ReleaseStatus.PUBLISHED, published.getStatus());
        assertTrue(published.isImmutable());
    }
}
