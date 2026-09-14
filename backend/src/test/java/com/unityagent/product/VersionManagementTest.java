package com.unityagent.product;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.model.GameVersion;
import com.unityagent.product.model.Release;
import com.unityagent.product.model.ReleaseChannel;
import com.unityagent.product.model.ReleaseStatus;
import com.unityagent.product.service.VersionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class VersionManagementTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private VersionManager versionManager;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("version_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);
        versionManager = new VersionManager(db);

        // SSOT project
        repository.upsertProject(new ProjectMemory(
                "proj-ver-1", "Versioned Game", "6000.4.7f1",
                "WINDOWS", "URP", "1.0.0", "fp-ver-1"
        ));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testSemanticVersionParsingAndComparison() {
        GameVersion v1 = GameVersion.parse("1.0.0");
        GameVersion v2 = GameVersion.parse("1.0.1");
        GameVersion v3 = GameVersion.parse("1.2.0");
        GameVersion v4 = GameVersion.parse("2.0.0-rc1");

        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v2.compareTo(v3) < 0);
        assertTrue(v3.compareTo(v4) < 0);
        assertEquals(0, v1.compareTo(GameVersion.parse("1.0.0")));

        assertThrows(IllegalArgumentException.class, () -> GameVersion.parse("invalid.version"));
    }

    @Test
    void testSemanticVersionBumping() {
        GameVersion base = GameVersion.parse("1.4.9");
        assertEquals("1.4.10", base.bumpPatch().toString());
        assertEquals("1.5.0", base.bumpMinor().toString());
        assertEquals("2.0.0", base.bumpMajor().toString());
    }

    @Test
    void testVersionUniquenessEnforcement() throws Exception {
        assertTrue(versionManager.isVersionUnused("proj-ver-1", "1.0.0"));

        // Insert a release with version 1.0.0
        String sql = "INSERT INTO game_releases (release_id, project_id, version_string, channel, status, is_immutable, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "rel-1");
            ps.setString(2, "proj-ver-1");
            ps.setString(3, "1.0.0");
            ps.setString(4, ReleaseChannel.STABLE.name());
            ps.setString(5, ReleaseStatus.PUBLISHED.name());
            ps.setInt(6, 1);
            ps.setString(7, Instant.now().toString());
            ps.executeUpdate();
        }

        // Version 1.0.0 should now be reported as NOT unused (i.e. already taken)
        assertFalse(versionManager.isVersionUnused("proj-ver-1", "1.0.0"));
        assertTrue(versionManager.isVersionUnused("proj-ver-1", "1.0.1"));

        // Suggest next version should bump patch to 1.0.1
        GameVersion next = versionManager.suggestNextVersion("proj-ver-1", "patch");
        assertEquals("1.0.1", next.toString());
    }
}
