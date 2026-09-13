package com.unityagent.studio;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.studio.model.AuditEvent;
import com.unityagent.studio.service.StudioSecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditEventTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase memoryDb;
    private StudioSecurityService securityService;

    @BeforeEach
    void setUp() {
        memoryDb = new MemoryDatabase(tempDir.resolve("audit_test.db").toString());
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
    @DisplayName("Should record immutable audit events and query by project")
    void testRecordAndQueryAuditEvents() {
        AuditEvent e1 = securityService.logAuditEvent("lead_dev", "proj_audit", "run_1",
                "CHANGE_APPROVE", "cs_123", "SUCCESS", "Approved scene hierarchy modification");
        assertNotNull(e1);
        assertNotNull(e1.getEventId());

        AuditEvent e2 = securityService.logAuditEvent("agent_loop", "proj_audit", "run_1",
                "CHANGE_APPROVE", "cs_124", "DENIED", "Agent attempted to self-approve high risk change");
        assertNotNull(e2);

        // Another project
        securityService.logAuditEvent("lead_dev", "other_proj", "run_99",
                "PROJECT_CREATE", "other_proj", "SUCCESS", "Created new studio project");

        List<AuditEvent> projEvents = securityService.getAuditTrail("proj_audit", 10);
        assertEquals(2, projEvents.size());
        assertEquals("DENIED", projEvents.get(0).getResult());
        assertEquals("SUCCESS", projEvents.get(1).getResult());

        List<AuditEvent> allEvents = securityService.getAuditTrail(null, 10);
        assertEquals(3, allEvents.size());
    }
}
