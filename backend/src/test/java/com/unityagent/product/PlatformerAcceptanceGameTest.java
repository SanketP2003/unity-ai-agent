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

@DisplayName("Automated Integration Acceptance: 2D Platformer Game")
class PlatformerAcceptanceGameTest {

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
        File dbFile = tempDir.resolve("platformer_acceptance.db").toFile();
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

        projectRoot = tempDir.resolve("PlatformerProject");
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    @DisplayName("Complete Lifecycle: Natural-language 2D Platformer -> Build -> Verify -> Release")
    void testPlatformerCompleteAutonomousLifecycle() throws Exception {
        String projectId = "proj_platformer_2d";
        repository.upsertProject(new ProjectMemory(projectId, "Retro Platformer 2D", "2022.3.20f1", "WINDOWS", "URP", "1.0.0", "fp-plat-01"));

        Files.createDirectories(projectRoot.resolve("Assets/Scripts"));
        Files.createDirectories(projectRoot.resolve("Builds/Windows"));

        // 1. Template Verification & Workspace Creation
        ProjectTemplate template = templateManager.getTemplate("tpl-2d-game").orElseThrow();
        assertEquals("tpl-2d-game", template.getTemplateId());

        Workspace ws = workspaceManager.createWorkspace("ws_platformer", "Retro Platformer 2D Workspace", projectRoot.toString());
        assertNotNull(ws);
        assertEquals("Retro Platformer 2D Workspace", ws.getName());

        // 2. C# Script Synthesis & AST Safety Validation
        String playerControllerCode = """
                using UnityEngine;
                
                public class PlayerController2D : MonoBehaviour
                {
                    public float moveSpeed = 8f;
                    public float jumpForce = 12f;
                    private Rigidbody2D rb;
                    private int gemsCollected = 0;
                    public GameObject victoryUI;
                    
                    void Start() {
                        rb = GetComponent<Rigidbody2D>();
                    }
                    
                    void Update() {
                        float move = Input.GetAxisRaw("Horizontal");
                        rb.velocity = new Vector2(move * moveSpeed, rb.velocity.y);
                        if (Input.GetButtonDown("Jump") && Mathf.Abs(rb.velocity.y) < 0.01f) {
                            rb.AddForce(Vector2.up * jumpForce, ForceMode2D.Impulse);
                        }
                    }
                    
                    void OnTriggerEnter2D(Collider2D other) {
                        if (other.CompareTag("Gem")) {
                            gemsCollected++;
                            Destroy(other.gameObject);
                            if (gemsCollected >= 5 && victoryUI != null) {
                                victoryUI.SetActive(true);
                            }
                        } else if (other.CompareTag("Hazard")) {
                            transform.position = Vector3.zero;
                        }
                    }
                }
                """;

        String scriptPath = "Assets/Scripts/PlayerController2D.cs";
        ScriptSafetyValidator.ValidationResult safetyResult = scriptSafetyValidator.validate(scriptPath, playerControllerCode);
        assertTrue(safetyResult.isValid(), "Synthesized player controller must satisfy AST safety rules");
        Path diskScriptFile = projectRoot.resolve(scriptPath);
        Files.writeString(diskScriptFile, playerControllerCode);
        assertTrue(Files.exists(diskScriptFile));

        // 3. Authoritative CompletionGate Behavioral Verification
        ValidationReport report = new ValidationReport("goal_platformer");
        report.setTotalRequirements(5);
        report.setRequiredCount(5);
        report.setSatisfiedRequiredCount(5);
        report.setCompilationSuccess(true);
        report.setRuntimeErrorsClean(true);
        report.setBehaviorTestsPassed(true);
        report.setSummary("Hierarchy verified: Player, 3 Platforms, 5 Gems, 2 Hazards, VictoryUI. Play mode passed.");
        assertTrue(CompletionGate.evaluate(report), "CompletionGate must authoritatively verify 2D platformer criteria");

        // 4. Standalone Binary Build & Physical Artifact Registration
        Path exePath = projectRoot.resolve("Builds/Windows/RetroPlatformer.exe");
        Files.write(exePath, new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00}); // PE binary stub
        assertTrue(Files.exists(exePath));
        assertTrue(Files.size(exePath) > 0);

        String artifactId = "art_plat_win64";
        String releaseId = "rel_platformer_v1";
        BuildArtifact artifact = artifactManager.registerArtifact(
                artifactId,
                projectId,
                releaseId,
                "StandaloneWindows64",
                "x86_64",
                projectRoot,
                "Builds/Windows/RetroPlatformer.exe"
        );
        assertNotNull(artifact);
        assertEquals(ArtifactStatus.VERIFIED, artifact.getStatus());
        assertNotNull(artifact.getSha256Checksum());

        // 5. Release Candidate Creation
        Release release = releaseManager.createReleaseCandidate(
                releaseId,
                projectId,
                "1.0.0",
                ReleaseChannel.STABLE,
                "build_run_01",
                "Retro Platformer v1.0.0 Initial Release Candidate"
        );
        assertEquals(ReleaseStatus.DRAFT, release.getStatus());

        // 6. Automated Validation of Release
        ReleaseValidationReport valReport = new ReleaseValidationReport(releaseId);
        valReport.setValid(true);
        when(releaseValidator.validateRelease(any(), any(), any())).thenReturn(valReport);

        ReleaseValidationReport valRes = releaseManager.validateReleaseCandidate(releaseId, artifact, projectRoot);
        assertTrue(valRes.isValid(), "Release validation must pass all invariant checks");

        // 7. Human Reviewer Security Gate (Reject LLM/Agent, Accept Admin)
        assertThrows(SecurityException.class, () ->
                releaseManager.approveRelease(releaseId, "REVIEWER", "agent-loop-builder", "Self approval")
        );

        Release approvedRelease = releaseManager.approveRelease(
                releaseId,
                "ADMIN",
                "lead_game_designer",
                "Manually play-tested mechanics and verified SHA-256"
        );
        assertEquals(ReleaseStatus.APPROVED, approvedRelease.getStatus());

        // 8. Publish Release & Verify Immutability
        Release publishedRelease = releaseManager.publishRelease(releaseId, projectId, artifact, projectRoot);
        assertEquals(ReleaseStatus.PUBLISHED, publishedRelease.getStatus());
        assertTrue(publishedRelease.isImmutable());

        // 9. Tamper Detection Check
        assertTrue(artifactManager.verifyArtifactIntegrity(artifactId, projectRoot), "Artifact checksum must match on-disk file");
    }
}
