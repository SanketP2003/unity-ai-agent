package com.unityagent.studio;

import com.unityagent.studio.model.DiagnosticEntry;
import com.unityagent.studio.service.StudioDiagnosticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsTest {

    private StudioDiagnosticsService diagnosticsService;

    @BeforeEach
    void setUp() {
        diagnosticsService = new StudioDiagnosticsService();
    }

    @Test
    @DisplayName("Should scrub sensitive credentials, API keys, and Bearer tokens from messages and metadata")
    void testSecretScrubbing() {
        String sensitiveMsg = "OpenAI call failed with key sk-1234567890abcdef12345 and auth Bearer my_secret_token_12345";
        DiagnosticEntry entry = diagnosticsService.record("proj_1",
                DiagnosticEntry.Category.PROVIDER,
                DiagnosticEntry.Severity.ERROR,
                "OpenAIProvider",
                sensitiveMsg,
                Map.of("password", "supersecret123", "apiKey", "sk-abcdef1234567890987654"));

        assertNotNull(entry);
        assertFalse(entry.getMessage().contains("sk-1234567890abcdef12345"));
        assertTrue(entry.getMessage().contains("sk-[REDACTED_KEY]"));
        assertFalse(entry.getMessage().contains("my_secret_token_12345"));
        assertTrue(entry.getMessage().contains("Bearer [REDACTED_TOKEN]"));

        // Check metadata scrubbing
        assertEquals("[REDACTED_SECRET]", entry.getMetadata().get("password"));
        assertEquals("[REDACTED_SECRET]", entry.getMetadata().get("apiKey"));
    }

    @Test
    @DisplayName("Should record diagnostic entries across all 9 studio categories")
    void testNineCategoriesRecording() {
        for (DiagnosticEntry.Category category : DiagnosticEntry.Category.values()) {
            diagnosticsService.record("proj_cat",
                    category,
                    DiagnosticEntry.Severity.INFO,
                    "test_source",
                    "Category test: " + category.name(),
                    Map.of("cat", category.name()));
        }

        List<DiagnosticEntry> all = diagnosticsService.queryDiagnostics("proj_cat", null, null, null, 50);
        assertEquals(9, all.size());
    }

    @Test
    @DisplayName("Should filter diagnostics by multi-facet criteria")
    void testQueryFiltering() {
        diagnosticsService.record("p1", DiagnosticEntry.Category.COMPILATION, DiagnosticEntry.Severity.ERROR, "Compiler", "CS0103 error", null);
        diagnosticsService.record("p1", DiagnosticEntry.Category.RUNTIME, DiagnosticEntry.Severity.WARN, "Console", "Frame drop warning", null);
        diagnosticsService.record("p2", DiagnosticEntry.Category.COMPILATION, DiagnosticEntry.Severity.INFO, "Compiler", "Compile clean", null);

        // Filter by project
        List<DiagnosticEntry> p1Entries = diagnosticsService.queryDiagnostics("p1", null, null, null, 10);
        assertEquals(2, p1Entries.size());

        // Filter by category
        List<DiagnosticEntry> compEntries = diagnosticsService.queryDiagnostics(null, DiagnosticEntry.Category.COMPILATION, null, null, 10);
        assertEquals(2, compEntries.size());

        // Filter by severity
        List<DiagnosticEntry> errEntries = diagnosticsService.queryDiagnostics(null, null, DiagnosticEntry.Severity.ERROR, null, 10);
        assertEquals(1, errEntries.size());
        assertEquals("CS0103 error", errEntries.get(0).getMessage());

        // Filter by search text
        List<DiagnosticEntry> searched = diagnosticsService.queryDiagnostics(null, null, null, "frame", 10);
        assertEquals(1, searched.size());
        assertEquals("Frame drop warning", searched.get(0).getMessage());
    }
}
