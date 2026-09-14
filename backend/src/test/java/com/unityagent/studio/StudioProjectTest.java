package com.unityagent.studio;

import com.unityagent.agent.AutonomousRunController;
import com.unityagent.agent.AutonomousRunState;
import com.unityagent.agent.goal.GameGoal;
import com.unityagent.agent.plan.AgentPlan;
import com.unityagent.agent.provider.AIProvider;
import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.MemorySchema;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.studio.model.ProjectActivity;
import com.unityagent.studio.model.ProjectStatus;
import com.unityagent.studio.model.StudioProject;
import com.unityagent.studio.model.StudioProjectMetadata;
import com.unityagent.studio.service.StudioProjectService;
import com.unityagent.unity.UnityConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class StudioProjectTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private UnityConnection unityConnection;
    private AutonomousRunController runController;
    private AIProvider aiProvider;
    private StudioProjectService studioProjectService;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("studio_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();

        repository = new SQLiteMemoryRepository(db);
        unityConnection = Mockito.mock(UnityConnection.class);
        runController = Mockito.mock(AutonomousRunController.class);
        aiProvider = Mockito.mock(AIProvider.class);

        studioProjectService = new StudioProjectService(
                db, repository, unityConnection, runController, null, aiProvider
        );
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testSchemaV3TablesCreated() throws Exception {
        assertTrue(MemorySchema.CURRENT_VERSION >= 3);
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            // Verify Phase 11 tables exist
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='studio_project_metadata'")) {
                assertTrue(rs.next(), "studio_project_metadata table must exist in schema v3");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='change_sets'")) {
                assertTrue(rs.next(), "change_sets table must exist in schema v3");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='change_entries'")) {
                assertTrue(rs.next(), "change_entries table must exist in schema v3");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='build_records'")) {
                assertTrue(rs.next(), "build_records table must exist in schema v3");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='audit_events'")) {
                assertTrue(rs.next(), "audit_events table must exist in schema v3");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='studio_notifications'")) {
                assertTrue(rs.next(), "studio_notifications table must exist in schema v3");
            }
        }
    }

    @Test
    void testStudioProjectServiceProjectsAuthoritativeMemory() {
        // 1. Core authoritative project in database
        ProjectMemory pm = new ProjectMemory("proj_space", "Space Explorer", "6000.0.32f1", "Windows", "URP", "1.0.0", null);
        repository.upsertProject(pm);

        // 2. Mock Unity disconnected, AI provider configured
        when(unityConnection.isReady()).thenReturn(false);
        when(aiProvider.isConfigured()).thenReturn(true);

        List<StudioProject> list = studioProjectService.listProjects();
        assertEquals(1, list.size());

        StudioProject sp = list.get(0);
        assertEquals("proj_space", sp.getProjectId());
        assertEquals("Space Explorer", sp.getProjectName());
        assertEquals("6000.0.32f1", sp.getUnityVersion());
        assertEquals(ProjectStatus.DISCONNECTED, sp.getStatus());
        assertFalse(sp.isUnityConnected());
        assertTrue(sp.isProviderConfigured());
    }

    @Test
    void testStudioProjectReflectsActiveRunState() {
        String pid = "proj_rpg";
        ProjectMemory pm = new ProjectMemory(pid, "RPG Quest", "6000.0.32f1", "Windows", "URP", "1.0.0", null);
        repository.upsertProject(pm);

        when(unityConnection.isReady()).thenReturn(true);
        when(unityConnection.getProjectConnection(pid)).thenReturn(
                new UnityConnection.ProjectConnectionInfo(pid, "conn1", "1.0", "1.0.0", "6000.0.32f1", java.util.Map.of(), null));
        when(aiProvider.isConfigured()).thenReturn(true);

        // Mock active autonomous run
        GameGoal goal = new GameGoal("g1", "Build quest dialogue");
        AutonomousRunState activeRun = new AutonomousRunState("run_rpg_1", "s1", pid, goal, new AgentPlan());
        activeRun.setStatus(AutonomousRunState.RunStatus.RUNNING);
        when(runController.getActiveRunForProject(pid)).thenReturn(Optional.of(activeRun));

        StudioProject sp = studioProjectService.getProject(pid).orElseThrow();
        assertEquals(ProjectStatus.BUILDING, sp.getStatus());
        assertEquals("run_rpg_1", sp.getCurrentRunId());
        assertEquals("Build quest dialogue", sp.getCurrentGoal());
        assertTrue(sp.isUnityConnected());
    }

    @Test
    void testMetadataSavingAndRetrieval() {
        String pid = "proj_platformer";
        ProjectMemory pm = new ProjectMemory(pid, "Platformer 3D", "6000.0.32f1", "Windows", "URP", "1.0.0", null);
        repository.upsertProject(pm);

        StudioProjectMetadata meta = new StudioProjectMetadata();
        meta.setDescription("High speed platformer game");
        meta.setTags("platformer, 3d, speedrun");
        meta.setFavorite(true);
        meta.setTargetFps(120);
        meta.setActiveBuildProfile("Release");

        studioProjectService.saveMetadata(pid, meta);

        StudioProject sp = studioProjectService.getProject(pid).orElseThrow();
        assertNotNull(sp.getMetadata());
        assertEquals("High speed platformer game", sp.getMetadata().getDescription());
        assertTrue(sp.getMetadata().isFavorite());
        assertEquals(120, sp.getMetadata().getTargetFps());
        assertEquals("Release", sp.getMetadata().getActiveBuildProfile());
    }

    @Test
    void testProjectActivityRetrieval() throws Exception {
        String pid = "proj_act_test";
        ProjectMemory pm = new ProjectMemory(pid, "Activity Test", "6000.0.32f1", "Windows", "URP", "1.0.0", null);
        repository.upsertProject(pm);

        // Record audit event in DB
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO audit_events (event_id, timestamp, user_id, project_id, run_id, action, target, result, details) " +
                    "VALUES ('evt_1', datetime('now'), 'user_dev', 'proj_act_test', 'run_act_1', 'USER_STARTED_RUN', 'proj_act_test', 'SUCCESS', 'Goal: Build combat system')");
        }

        List<ProjectActivity> activities = studioProjectService.getProjectActivity(pid, 10);
        assertFalse(activities.isEmpty());
        assertEquals("USER_STARTED_RUN", activities.get(0).getType());
        assertEquals("SUCCESS", activities.get(0).getSeverity());
    }
}
