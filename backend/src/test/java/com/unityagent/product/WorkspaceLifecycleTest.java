package com.unityagent.product;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.model.ProjectLifecycleState;
import com.unityagent.product.model.Workspace;
import com.unityagent.product.model.WorkspaceProject;
import com.unityagent.product.service.WorkspaceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceLifecycleTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private WorkspaceManager workspaceManager;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("workspace_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);
        workspaceManager = new WorkspaceManager(db);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testWorkspaceCreationAndListing() {
        Workspace ws = workspaceManager.createWorkspace("ws-core", "Core Studio", tempDir.toString());
        assertNotNull(ws);
        assertEquals("ws-core", ws.getWorkspaceId());
        assertEquals("Core Studio", ws.getName());

        List<Workspace> list = workspaceManager.listWorkspaces();
        assertEquals(1, list.size());
        assertEquals("ws-core", list.get(0).getWorkspaceId());
    }

    @Test
    void testProjectSSOTAndWorkspaceLinking() {
        // 1. Existing projects table remains the authoritative SSOT
        ProjectMemory pm = new ProjectMemory(
                "proj-rpg-1", "Realm of Legends", "2022.3.20f1",
                "WINDOWS", "URP", "1.0.0", "fp-123"
        );
        repository.upsertProject(pm);
        Optional<ProjectMemory> saved = repository.findProject("proj-rpg-1");
        assertTrue(saved.isPresent(), "Project must exist in SSOT projects table");

        // 2. Link to workspace via workspace_projects
        workspaceManager.createWorkspace("ws-1", "Studio A", tempDir.toString());
        WorkspaceProject wp = workspaceManager.addProjectToWorkspace("ws-1", "proj-rpg-1");
        assertNotNull(wp);
        assertEquals("ws-1", wp.getWorkspaceId());
        assertEquals("proj-rpg-1", wp.getProjectId());
        assertEquals(ProjectLifecycleState.CREATED, wp.getLifecycleState());

        List<WorkspaceProject> projects = workspaceManager.listWorkspaceProjects("ws-1");
        assertEquals(1, projects.size());
        assertEquals("proj-rpg-1", projects.get(0).getProjectId());
    }

    @Test
    void testLifecycleTransitions() {
        repository.upsertProject(new ProjectMemory("proj-2", "Space Runner", "2022.3", "WINDOWS", "URP", "1.0.0", "fp-2"));
        workspaceManager.createWorkspace("ws-1", "Studio A", tempDir.toString());
        workspaceManager.addProjectToWorkspace("ws-1", "proj-2");

        // Open -> ACTIVE
        workspaceManager.openProject("proj-2");
        assertEquals(ProjectLifecycleState.ACTIVE, workspaceManager.getProjectState("proj-2").orElseThrow());

        // Close -> READY
        workspaceManager.closeProject("proj-2");
        assertEquals(ProjectLifecycleState.READY, workspaceManager.getProjectState("proj-2").orElseThrow());

        // Archive -> ARCHIVED
        workspaceManager.archiveProject("proj-2");
        assertEquals(ProjectLifecycleState.ARCHIVED, workspaceManager.getProjectState("proj-2").orElseThrow());

        // Restore -> READY
        workspaceManager.restoreProject("proj-2");
        assertEquals(ProjectLifecycleState.READY, workspaceManager.getProjectState("proj-2").orElseThrow());
    }

    @Test
    void testProjectDeletionRequiresExplicitConfirm() {
        repository.upsertProject(new ProjectMemory("proj-del", "Delete Candidate", "2022.3", "WINDOWS", "URP", "1.0.0", "fp-del"));
        workspaceManager.createWorkspace("ws-1", "Studio A", tempDir.toString());
        workspaceManager.addProjectToWorkspace("ws-1", "proj-del");

        // Without confirmation token "DELETE", deletion must fail
        assertThrows(IllegalArgumentException.class, () -> {
            workspaceManager.deleteProject("proj-del", "wrong-token");
        });

        // SSOT project must remain untouched
        assertTrue(repository.findProject("proj-del").isPresent());

        // With explicit confirmation
        workspaceManager.deleteProject("proj-del", "DELETE");
        assertEquals(ProjectLifecycleState.DELETED, workspaceManager.getProjectState("proj-del").orElseThrow());
    }
}
