package com.unityagent.studio;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.studio.model.StudioPermission;
import com.unityagent.studio.model.StudioRole;
import com.unityagent.studio.service.StudioSecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PermissionTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase memoryDb;
    private StudioSecurityService securityService;

    @BeforeEach
    void setUp() {
        memoryDb = new MemoryDatabase(tempDir.resolve("perm_test.db").toString());
        memoryDb.initialize();
        securityService = new StudioSecurityService(memoryDb);
    }

    @AfterEach
    void tearDown() {
        if (memoryDb != null) {
            memoryDb.shutdown();
        }
    }

    @Test
    @DisplayName("OWNER and ADMIN should have all permissions")
    void testOwnerAndAdminPermissions() {
        for (StudioPermission perm : StudioPermission.values()) {
            assertTrue(securityService.hasPermission(StudioRole.OWNER, perm));
            assertTrue(securityService.hasPermission(StudioRole.ADMIN, perm));
            assertDoesNotThrow(() -> securityService.checkPermission(StudioRole.OWNER, perm));
            assertDoesNotThrow(() -> securityService.checkPermission(StudioRole.ADMIN, perm));
        }
    }

    @Test
    @DisplayName("DEVELOPER should have build and run permissions but lack HIGH risk approval")
    void testDeveloperPermissions() {
        assertTrue(securityService.hasPermission(StudioRole.DEVELOPER, StudioPermission.RUN_TRIGGER));
        assertTrue(securityService.hasPermission(StudioRole.DEVELOPER, StudioPermission.BUILD_TRIGGER));
        assertTrue(securityService.hasPermission(StudioRole.DEVELOPER, StudioPermission.ROLLBACK_EXECUTE));
        assertTrue(securityService.hasPermission(StudioRole.DEVELOPER, StudioPermission.CHANGE_APPROVE_MED_LOW));

        // Must NOT have CHANGE_APPROVE_HIGH
        assertFalse(securityService.hasPermission(StudioRole.DEVELOPER, StudioPermission.CHANGE_APPROVE_HIGH));
        assertThrows(SecurityException.class, () ->
            securityService.checkPermission(StudioRole.DEVELOPER, StudioPermission.CHANGE_APPROVE_HIGH)
        );
    }

    @Test
    @DisplayName("REVIEWER should have approval permissions but lack execution/run permissions")
    void testReviewerPermissions() {
        assertTrue(securityService.hasPermission(StudioRole.REVIEWER, StudioPermission.CHANGE_APPROVE_HIGH));
        assertTrue(securityService.hasPermission(StudioRole.REVIEWER, StudioPermission.CHANGE_APPROVE_MED_LOW));
        assertTrue(securityService.hasPermission(StudioRole.REVIEWER, StudioPermission.AUDIT_VIEW));

        // Must NOT have RUN_TRIGGER or BUILD_TRIGGER
        assertFalse(securityService.hasPermission(StudioRole.REVIEWER, StudioPermission.RUN_TRIGGER));
        assertFalse(securityService.hasPermission(StudioRole.REVIEWER, StudioPermission.BUILD_TRIGGER));
        assertThrows(SecurityException.class, () ->
            securityService.checkPermission(StudioRole.REVIEWER, StudioPermission.RUN_TRIGGER)
        );
    }

    @Test
    @DisplayName("VIEWER should be strictly read-only")
    void testViewerPermissions() {
        assertTrue(securityService.hasPermission(StudioRole.VIEWER, StudioPermission.PROJECT_VIEW));
        assertTrue(securityService.hasPermission(StudioRole.VIEWER, StudioPermission.DIAGNOSTICS_VIEW));

        assertFalse(securityService.hasPermission(StudioRole.VIEWER, StudioPermission.RUN_TRIGGER));
        assertFalse(securityService.hasPermission(StudioRole.VIEWER, StudioPermission.BUILD_TRIGGER));
        assertFalse(securityService.hasPermission(StudioRole.VIEWER, StudioPermission.PROJECT_EDIT));
        assertFalse(securityService.hasPermission(StudioRole.VIEWER, StudioPermission.CHANGE_APPROVE_MED_LOW));
        assertFalse(securityService.hasPermission(StudioRole.VIEWER, StudioPermission.ROLLBACK_EXECUTE));

        assertThrows(SecurityException.class, () ->
            securityService.checkPermission(StudioRole.VIEWER, StudioPermission.BUILD_TRIGGER)
        );
    }
}
