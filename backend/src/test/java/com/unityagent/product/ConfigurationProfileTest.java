package com.unityagent.product;

import com.unityagent.memory.MemoryDatabase;
import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.SQLiteMemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import com.unityagent.product.model.ConfigEnvironment;
import com.unityagent.product.model.ConfigurationProfile;
import com.unityagent.product.service.ConfigurationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationProfileTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;
    private MemoryRepository repository;
    private ConfigurationManager configurationManager;

    @BeforeEach
    void setUp() {
        File dbFile = tempDir.resolve("config_test.db").toFile();
        db = new MemoryDatabase(dbFile.getAbsolutePath());
        db.initialize();
        repository = new SQLiteMemoryRepository(db);
        configurationManager = new ConfigurationManager(db);

        // SSOT project
        repository.upsertProject(new ProjectMemory(
                "proj-cfg-1", "Configured Game", "6000.4.7f1",
                "WINDOWS", "URP", "1.0.0", "fp-cfg-1"
        ));
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void testSaveAndRetrieveProfile() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("graphicsQuality", "ULTRA");
        settings.put("targetFrameRate", 60);
        settings.put("audioMasterVolume", 0.85);

        ConfigurationProfile profile = configurationManager.saveProfile(
                "cfg-dev", "proj-cfg-1", "Dev Profile", ConfigEnvironment.DEVELOPMENT, settings
        );
        assertNotNull(profile);
        assertEquals("cfg-dev", profile.getProfileId());

        Optional<ConfigurationProfile> loaded = configurationManager.getProfile("cfg-dev");
        assertTrue(loaded.isPresent());
        assertEquals("ULTRA", loaded.get().getSettings().get("graphicsQuality"));
        assertEquals(60, loaded.get().getSettings().get("targetFrameRate"));

        List<ConfigurationProfile> list = configurationManager.listProfiles("proj-cfg-1");
        assertEquals(1, list.size());
    }

    @Test
    void testZeroSecretRejectionOnSensitiveKey() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("api_key", "unallowed-api-key-12345");

        assertThrows(SecurityException.class, () -> {
            configurationManager.saveProfile("cfg-sec-1", "proj-cfg-1", "Bad Profile", ConfigEnvironment.RELEASE, settings);
        }, "Must reject settings with sensitive key 'api_key'");
    }

    @Test
    void testZeroSecretRejectionOnSensitivePatternValue() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("remoteServerUrl", "sk-123456789012345678901234567890");

        assertThrows(SecurityException.class, () -> {
            configurationManager.saveProfile("cfg-sec-2", "proj-cfg-1", "Bad Token", ConfigEnvironment.RELEASE, settings);
        }, "Must reject settings containing sensitive token pattern");
    }
}
