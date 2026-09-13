package com.unityagent.studio;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.studio.model.*;
import com.unityagent.studio.service.ChangeReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChangeReviewTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase memoryDb;
    private ChangeReviewService changeReviewService;

    @BeforeEach
    void setUp() throws Exception {
        memoryDb = new MemoryDatabase(tempDir.resolve("change_review_test.db").toString());
        memoryDb.initialize();

        // Seed test projects to satisfy foreign key constraints
        try (java.sql.Connection conn = memoryDb.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO projects (project_id, project_name, created_at) VALUES (?, ?, datetime('now'))")) {
            stmt.setString(1, "proj_alpha");
            stmt.setString(2, "Project Alpha");
            stmt.executeUpdate();

            stmt.setString(1, "proj_beta");
            stmt.setString(2, "Project Beta");
            stmt.executeUpdate();
        }

        changeReviewService = new ChangeReviewService(memoryDb);
    }

    @AfterEach
    void tearDown() {
        if (memoryDb != null) {
            memoryDb.shutdown();
        }
    }

    @Test
    @DisplayName("Should accurately evaluate risk levels based on change type and path")
    void testRiskEvaluation() {
        // High risk cases
        assertEquals(RiskLevel.HIGH, changeReviewService.evaluateRisk(ChangeType.FILE_DELETE, "Assets/Scripts/Player.cs"));
        assertEquals(RiskLevel.HIGH, changeReviewService.evaluateRisk(ChangeType.SCENE_OBJECT_DELETE, "Player"));
        assertEquals(RiskLevel.HIGH, changeReviewService.evaluateRisk(ChangeType.FILE_MODIFY, "Assets/Scenes/Main.unity"));
        assertEquals(RiskLevel.HIGH, changeReviewService.evaluateRisk(ChangeType.FILE_MODIFY, "ProjectSettings/TagManager.asset"));
        assertEquals(RiskLevel.HIGH, changeReviewService.evaluateRisk(ChangeType.FILE_CREATE, "Assets/Plugins/NativePlugin.dll"));
        assertEquals(RiskLevel.HIGH, changeReviewService.evaluateRisk(ChangeType.FILE_MODIFY, "Packages/manifest.json"));

        // Medium risk cases
        assertEquals(RiskLevel.MEDIUM, changeReviewService.evaluateRisk(ChangeType.FILE_MODIFY, "Assets/Scripts/PlayerController.cs"));
        assertEquals(RiskLevel.MEDIUM, changeReviewService.evaluateRisk(ChangeType.COMPONENT_MODIFY, "Player"));

        // Low risk cases
        assertEquals(RiskLevel.LOW, changeReviewService.evaluateRisk(ChangeType.FILE_CREATE, "Assets/Scripts/NewScript.cs"));
        assertEquals(RiskLevel.LOW, changeReviewService.evaluateRisk(ChangeType.SCENE_OBJECT_CREATE, "Enemy"));
        assertEquals(RiskLevel.LOW, changeReviewService.evaluateRisk(ChangeType.ASSET_IMPORT, "Assets/Textures/Ground.png"));
    }

    @Test
    @DisplayName("Should create change set, add entries, and aggregate overall risk")
    void testCreateChangeSetAndEntries() {
        ChangeSet cs = changeReviewService.createChangeSet("proj_alpha", "run_101", "node_1", "Add Player Controller");
        assertNotNull(cs);
        assertEquals("proj_alpha", cs.getProjectId());
        assertEquals("run_101", cs.getAgentRunId());
        assertEquals(ApprovalState.PENDING, cs.getStatus());
        assertEquals(RiskLevel.LOW, cs.getRiskLevel());

        // Add a medium risk entry
        ChangeEntry e1 = changeReviewService.addEntry(cs.getChangeSetId(), ChangeType.FILE_MODIFY, "Assets/Scripts/GameManager.cs",
                "hash1", "hash2", "diff content", "Update score handling");
        assertNotNull(e1);
        assertEquals(RiskLevel.MEDIUM, e1.getRiskLevel());
        assertEquals(RiskLevel.MEDIUM, cs.getRiskLevel());
        assertFalse(cs.isRequiresHumanApproval());

        // Add a high risk entry
        ChangeEntry e2 = changeReviewService.addEntry(cs.getChangeSetId(), ChangeType.FILE_DELETE, "Assets/OldScript.cs",
                "hash3", null, null, "Delete deprecated script");
        assertEquals(RiskLevel.HIGH, e2.getRiskLevel());
        assertEquals(RiskLevel.HIGH, cs.getRiskLevel());
        assertTrue(cs.isRequiresHumanApproval());
    }

    @Test
    @DisplayName("Human approval boundary: LLMs cannot self-approve HIGH risk change sets")
    void testHumanApprovalBoundaryRejectsLlm() {
        ChangeSet cs = changeReviewService.createChangeSet("proj_alpha", "run_101", "node_2", "Modify Main Scene");
        changeReviewService.addEntry(cs.getChangeSetId(), ChangeType.FILE_MODIFY, "Assets/Scenes/Main.unity",
                "hashA", "hashB", "diff", "Edit main scene hierarchy");

        assertEquals(RiskLevel.HIGH, cs.getRiskLevel());

        // Attempt approval by LLM or Agent -> Must throw SecurityException
        assertThrows(SecurityException.class, () ->
            changeReviewService.approveChangeSet(cs.getChangeSetId(), "AGENT_AUTONOMOUS", "agent_loop")
        );

        assertThrows(SecurityException.class, () ->
            changeReviewService.approveChangeSet(cs.getChangeSetId(), "LLM", "gpt_4o")
        );

        assertThrows(SecurityException.class, () ->
            changeReviewService.approveChangeSet(cs.getChangeSetId(), null, "system")
        );

        // Status must remain PENDING
        assertEquals(ApprovalState.PENDING, cs.getStatus());
    }

    @Test
    @DisplayName("Human approval boundary: Authorized human can approve HIGH risk change set")
    void testHumanApprovalSucceeds() {
        ChangeSet cs = changeReviewService.createChangeSet("proj_alpha", "run_101", "node_3", "Delete Old Assets");
        changeReviewService.addEntry(cs.getChangeSetId(), ChangeType.FILE_DELETE, "Assets/OldFile.cs",
                "hash1", null, null, "Cleanup");

        ChangeSet approved = changeReviewService.approveChangeSet(cs.getChangeSetId(), "DEVELOPER", "lead_dev");
        assertEquals(ApprovalState.APPROVED, approved.getStatus());
        assertEquals("lead_dev", approved.getReviewedBy());
        assertNotNull(approved.getReviewedAt());

        // Check entries are also marked approved
        for (ChangeEntry entry : approved.getEntries()) {
            assertEquals(ApprovalState.APPROVED, entry.getApprovalState());
        }
    }

    @Test
    @DisplayName("Should handle rejection workflow with rationale")
    void testRejectChangeSet() {
        ChangeSet cs = changeReviewService.createChangeSet("proj_alpha", "run_101", "node_4", "Risky Shader Change");
        changeReviewService.addEntry(cs.getChangeSetId(), ChangeType.FILE_MODIFY, "Assets/Shaders/Custom.shader",
                "h1", "h2", "diff", "Shader update");

        ChangeSet rejected = changeReviewService.rejectChangeSet(cs.getChangeSetId(), "tech_artist", "Causes performance regression on mobile");
        assertEquals(ApprovalState.REJECTED, rejected.getStatus());
        assertEquals("tech_artist", rejected.getReviewedBy());
        assertEquals("Causes performance regression on mobile", rejected.getRejectionReason());
        assertNotNull(rejected.getReviewedAt());
    }

    @Test
    @DisplayName("Should query pending and project change sets from persistence")
    void testPersistenceAndQuery() {
        changeReviewService.createChangeSet("proj_beta", "run_201", "n1", "Pending Change 1");
        ChangeSet cs2 = changeReviewService.createChangeSet("proj_beta", "run_201", "n2", "Approved Change 2");
        changeReviewService.approveChangeSet(cs2.getChangeSetId(), "ADMIN", "admin_user");

        List<ChangeSet> all = changeReviewService.getChangeSetsForProject("proj_beta");
        assertEquals(2, all.size());

        List<ChangeSet> pending = changeReviewService.getPendingChangeSets("proj_beta");
        assertEquals(1, pending.size());
        assertEquals("Pending Change 1", pending.get(0).getSummary());

        List<ChangeSet> runSets = changeReviewService.getChangeSetsForRun("run_201");
        assertEquals(2, runSets.size());
    }
}
