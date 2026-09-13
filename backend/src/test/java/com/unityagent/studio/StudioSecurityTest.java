package com.unityagent.studio;

import com.unityagent.studio.service.StudioDiagnosticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StudioSecurityTest {

    private final StudioDiagnosticsService diagnosticsService = new StudioDiagnosticsService();

    @Test
    @DisplayName("Should redact multiple sensitive credentials in complex text")
    void testMultipleCredentialsRedaction() {
        String input = "Bearer eyJhbGciOiJIUzI1NiJ9.testToken with API key sk-ant-api03-1234567890abcdef-12345 and password='super_secret_db_pass'";
        String scrubbed = diagnosticsService.scrubSecrets(input);

        assertFalse(scrubbed.contains("eyJhbGciOiJIUzI1NiJ9.testToken"));
        assertTrue(scrubbed.contains("Bearer [REDACTED_TOKEN]"));
        assertFalse(scrubbed.contains("1234567890abcdef"));
        assertTrue(scrubbed.contains("sk-[REDACTED_KEY]"));
        assertFalse(scrubbed.contains("super_secret_db_pass"));
        assertTrue(scrubbed.contains("password=[REDACTED_SECRET]"));
    }

    @Test
    @DisplayName("Should handle null and empty input gracefully without errors")
    void testNullAndEmptyHandling() {
        assertNull(diagnosticsService.scrubSecrets(null));
        assertEquals("", diagnosticsService.scrubSecrets(""));
        assertEquals("   ", diagnosticsService.scrubSecrets("   "));
    }

    @Test
    @DisplayName("Should preserve normal non-sensitive messages without alteration")
    void testPreserveNormalMessages() {
        String normal = "GameObject 'Player' created at position (0, 1, 0) with Rigidbody";
        assertEquals(normal, diagnosticsService.scrubSecrets(normal));
    }
}
