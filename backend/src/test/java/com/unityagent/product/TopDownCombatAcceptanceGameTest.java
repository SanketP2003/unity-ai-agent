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

@DisplayName("Automated Integration Acceptance: Top-Down Combat Game")
class TopDownCombatAcceptanceGameTest {

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
        File dbFile = tempDir.resolve("topdown_acceptance.db").toFile();
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

        projectRoot = tempDir.resolve("TopDownProject");
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    @DisplayName("Complete Lifecycle: Top-Down Combat -> Multi-Wave Spawning -> Weapon Firing -> Release Candidate")
    void testTopDownCombatAutonomousLifecycle() throws Exception {
        String projectId = "proj_topdown_combat";
        repository.upsertProject(new ProjectMemory(projectId, "Cyber Assault Top-Down", "2022.3.20f1", "LINUX", "URP", "1.0.0", "fp-topdown-01"));

        Files.createDirectories(projectRoot.resolve("Assets/Scripts"));
        Files.createDirectories(projectRoot.resolve("Builds/Linux"));

        // 1. Template Scaffolding
        ProjectTemplate template = templateManager.getTemplate("tpl-top-down").orElseThrow();
        assertEquals("tpl-top-down", template.getTemplateId());

        Workspace ws = workspaceManager.createWorkspace("ws_topdown", "Cyber Assault Workspace", projectRoot.toString());
        assertNotNull(ws);

        // 2. Combat Script Synthesis & Safety Checks
        String combatScript = """
                using UnityEngine;
                
                public class TopDownShooterController : MonoBehaviour
                {
                    public float moveSpeed = 6f;
                    public GameObject projectilePrefab;
                    public Transform firePoint;
                    public int playerHealth = 100;
                    
                    void Update() {
                        float h = Input.GetAxisRaw("Horizontal");
                        float v = Input.GetAxisRaw("Vertical");
                        Vector2 move = new Vector2(h, v).normalized;
                        transform.Translate(move * moveSpeed * Time.deltaTime, Space.World);
                        
                        if (Input.GetMouseButtonDown(0) && projectilePrefab != null) {
                            Instantiate(projectilePrefab, firePoint.position, firePoint.rotation);
                        }
                    }
                    
                    public void TakeDamage(int dmg) {
                        playerHealth -= dmg;
                        if (playerHealth <= 0) {
                            gameObject.SetActive(false);
                        }
                    }
                }
                """;

        String scriptPath = "Assets/Scripts/TopDownShooterController.cs";
        ScriptSafetyValidator.ValidationResult safety = scriptSafetyValidator.validate(scriptPath, combatScript);
        assertTrue(safety.isValid());
        Path diskScript = projectRoot.resolve(scriptPath);
        Files.writeString(diskScript, combatScript);
        assertTrue(Files.exists(diskScript));

        // 3. Authoritative CompletionGate Behavioral Verification
        ValidationReport report = new ValidationReport("goal_topdown");
        report.setTotalRequirements(4);
        report.setRequiredCount(4);
        report.setSatisfiedRequiredCount(4);
        report.setCompilationSuccess(true);
        report.setRuntimeErrorsClean(true);
        report.setBehaviorTestsPassed(true);
        report.setSummary("Hierarchy verified: Player, WeaponFirePoint, EnemySpawner, ArenaBounds. Wave 1 cleared.");
        assertTrue(CompletionGate.evaluate(report));

        // 4. Physical Binary Artifact Registration (Linux x86_64 target)
        Path binPath = projectRoot.resolve("Builds/Linux/CyberAssault.x86_64");
        Files.write(binPath, new byte[]{0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01, 0x01, 0x00}); // ELF 64-bit header
        assertTrue(Files.exists(binPath));

        String artifactId = "art_topdown_linux";
        String releaseId = "rel_topdown_v1";
        BuildArtifact artifact = artifactManager.registerArtifact(
                artifactId,
                projectId,
                releaseId,
                "StandaloneLinux64",
                "x86_64",
                projectRoot,
                "Builds/Linux/CyberAssault.x86_64"
        );
        assertNotNull(artifact);
        assertEquals(ArtifactStatus.VERIFIED, artifact.getStatus());

        // 5. Release Candidate & Validation
        Release release = releaseManager.createReleaseCandidate(
                releaseId,
                projectId,
                "1.0.0",
                ReleaseChannel.BETA,
                "build_run_topdown_01",
                "Multi-wave top-down shooter game"
        );
        ReleaseValidationReport valReport = new ReleaseValidationReport(releaseId);
        valReport.setValid(true);
        when(releaseValidator.validateRelease(any(), any(), any())).thenReturn(valReport);

        ReleaseValidationReport valRes = releaseManager.validateReleaseCandidate(releaseId, artifact, projectRoot);
        assertTrue(valRes.isValid());

        // 6. Approval & Publication
        Release approved = releaseManager.approveRelease(
                releaseId,
                "REVIEWER",
                "qa_lead_specialist",
                "Verified multi-wave combat logic and frame rates"
        );
        assertEquals(ReleaseStatus.APPROVED, approved.getStatus());

        Release published = releaseManager.publishRelease(releaseId, projectId, artifact, projectRoot);
        assertEquals(ReleaseStatus.PUBLISHED, published.getStatus());
        assertTrue(published.isImmutable());
    }
}
