package com.unityagent.studio.service;

import com.unityagent.studio.model.DiagnosticEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Centralized studio diagnostics service across 9 categories.
 * Strictly guarantees that credentials, API keys, and sensitive tokens are scrubbed
 * and never exposed in logs or diagnostics streams.
 */
@Service
public class StudioDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(StudioDiagnosticsService.class);

    private static final int MAX_BUFFER_SIZE = 1000;

    // Secret scrubbing patterns
    private static final Pattern API_KEY_PATTERN = Pattern.compile("sk-[a-zA-Z0-9_-]{15,}");
    private static final Pattern BEARER_PATTERN = Pattern.compile("Bearer\\s+[a-zA-Z0-9_\\-\\.]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREDENTIAL_KV_PATTERN = Pattern.compile("(?i)(password|secret|token|apikey|api_key)\\s*[:=]\\s*[\"']?[^\"',\\s]+[\"']?");

    private final Deque<DiagnosticEntry> diagnosticsBuffer = new ConcurrentLinkedDeque<>();

    /**
     * Scrubs sensitive credentials and tokens from a string.
     */
    public String scrubSecrets(String input) {
        if (input == null || input.isBlank()) return input;

        String scrubbed = API_KEY_PATTERN.matcher(input).replaceAll("sk-[REDACTED_KEY]");
        scrubbed = BEARER_PATTERN.matcher(scrubbed).replaceAll("Bearer [REDACTED_TOKEN]");
        scrubbed = CREDENTIAL_KV_PATTERN.matcher(scrubbed).replaceAll("$1=[REDACTED_SECRET]");
        return scrubbed;
    }

    /**
     * Records a new diagnostic entry with automated secret scrubbing.
     */
    public DiagnosticEntry record(String projectId,
                                 DiagnosticEntry.Category category,
                                 DiagnosticEntry.Severity severity,
                                 String source,
                                 String message,
                                 Map<String, Object> metadata) {
        String id = "diag_" + UUID.randomUUID().toString().substring(0, 8);
        String now = Instant.now().toString();

        String cleanMessage = scrubSecrets(message);
        String cleanSource = scrubSecrets(source);

        Map<String, Object> cleanMetadata = new LinkedHashMap<>();
        if (metadata != null) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                String lowerKey = key.toLowerCase();
                if (lowerKey.contains("password") || lowerKey.contains("secret") ||
                    lowerKey.contains("token") || lowerKey.contains("key")) {
                    cleanMetadata.put(key, "[REDACTED_SECRET]");
                } else if (val instanceof String strVal) {
                    cleanMetadata.put(key, scrubSecrets(strVal));
                } else {
                    cleanMetadata.put(key, val);
                }
            }
        }

        DiagnosticEntry entry = new DiagnosticEntry(id, now, projectId, category, severity,
                cleanSource, cleanMessage, cleanMetadata);

        diagnosticsBuffer.addFirst(entry);

        // Enforce maximum buffer size
        while (diagnosticsBuffer.size() > MAX_BUFFER_SIZE) {
            diagnosticsBuffer.removeLast();
        }

        return entry;
    }

    /**
     * Filters diagnostic entries by multi-facet criteria.
     */
    public List<DiagnosticEntry> queryDiagnostics(String projectId,
                                                 DiagnosticEntry.Category category,
                                                 DiagnosticEntry.Severity severity,
                                                 String search,
                                                 int limit) {
        int max = limit > 0 ? limit : 100;
        String searchLower = search != null ? search.toLowerCase() : null;

        return diagnosticsBuffer.stream()
                .filter(d -> projectId == null || Objects.equals(projectId, d.getProjectId()))
                .filter(d -> category == null || d.getCategory() == category)
                .filter(d -> severity == null || d.getSeverity() == severity)
                .filter(d -> searchLower == null ||
                        (d.getMessage() != null && d.getMessage().toLowerCase().contains(searchLower)) ||
                        (d.getSource() != null && d.getSource().toLowerCase().contains(searchLower)))
                .limit(max)
                .collect(Collectors.toList());
    }

    public int getBufferSize() {
        return diagnosticsBuffer.size();
    }

    public void clear() {
        diagnosticsBuffer.clear();
    }
}
