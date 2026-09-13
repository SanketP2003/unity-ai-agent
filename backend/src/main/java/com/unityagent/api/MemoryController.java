package com.unityagent.api;

import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.model.*;
import com.unityagent.memory.service.MemoryContext;
import com.unityagent.memory.service.MemoryRetriever;
import com.unityagent.memory.service.ProjectMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for inspecting, previewing, and managing persistent multi-project agent memory.
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    private final ProjectMemoryService projectMemoryService;
    private final MemoryRepository memoryRepository;
    private final MemoryRetriever memoryRetriever;

    public MemoryController(ProjectMemoryService projectMemoryService,
                            MemoryRepository memoryRepository,
                            MemoryRetriever memoryRetriever) {
        this.projectMemoryService = projectMemoryService;
        this.memoryRepository = memoryRepository;
        this.memoryRetriever = memoryRetriever;
    }

    /**
     * GET /api/memory/projects — list all known projects in memory.
     */
    @GetMapping("/projects")
    public ResponseEntity<List<ProjectMemory>> listProjects() {
        return ResponseEntity.ok(projectMemoryService.getAllProjects());
    }

    /**
     * GET /api/memory/projects/{projectId} — get project details.
     */
    @GetMapping("/projects/{projectId}")
    public ResponseEntity<?> getProject(@PathVariable String projectId) {
        return projectMemoryService.getProject(projectId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "NOT_FOUND",
                        "message", "Project '" + projectId + "' not found in memory"
                )));
    }

    /**
     * GET /api/memory/projects/{projectId}/summary — get categorized facts (ARCHITECTURE, VALIDATION, FAILURE).
     */
    @GetMapping("/projects/{projectId}/summary")
    public ResponseEntity<Map<String, Object>> getProjectSummary(@PathVariable String projectId) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("projectId", projectId);

        List<MemoryEntry> arch = memoryRepository.findMemoryEntries(projectId, "ARCHITECTURE");
        List<MemoryEntry> validation = memoryRepository.findMemoryEntries(projectId, "VALIDATION");
        List<MemoryEntry> failures = memoryRepository.findMemoryEntries(projectId, "FAILURE");

        resp.put("architectureEntries", arch);
        resp.put("validationEntries", validation);
        resp.put("failureEntries", failures);
        resp.put("totalEntries", arch.size() + validation.size() + failures.size());

        memoryRepository.findLatestArchitecture(projectId).ifPresent(snap -> {
            resp.put("latestArchitectureSnapshot", snap);
        });

        return ResponseEntity.ok(resp);
    }

    /**
     * GET /api/memory/projects/{projectId}/context — preview the bounded LLM prompt context for a given goal.
     */
    @GetMapping("/projects/{projectId}/context")
    public ResponseEntity<Map<String, Object>> previewContext(
            @PathVariable String projectId,
            @RequestParam(name = "goal", required = false, defaultValue = "") String goal) {

        MemoryContext ctx = memoryRetriever.getRelevantContext(projectId, goal);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("projectId", projectId);
        resp.put("goal", goal);

        if (ctx != null && ctx.hasContent()) {
            resp.put("hasContent", true);
            resp.put("formattedContext", ctx.format());
            resp.put("estimatedTokens", ctx.estimateTokens());
            resp.put("scriptSummariesCount", ctx.getScriptSummaries().size());
            resp.put("recentHistoryCount", ctx.getRecentHistory().size());
            resp.put("knownIssuesCount", ctx.getKnownIssues().size());
        } else {
            resp.put("hasContent", false);
            resp.put("formattedContext", "");
            resp.put("estimatedTokens", 0);
        }

        return ResponseEntity.ok(resp);
    }

    /**
     * GET /api/memory/projects/{projectId}/conversations — get past conversation records.
     */
    @GetMapping("/projects/{projectId}/conversations")
    public ResponseEntity<List<ConversationRecord>> getConversations(
            @PathVariable String projectId,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        return ResponseEntity.ok(memoryRepository.findConversationsByProject(projectId, limit));
    }

    /**
     * GET /api/memory/projects/{projectId}/scripts — get tracked scripts and hashes.
     */
    @GetMapping("/projects/{projectId}/scripts")
    public ResponseEntity<List<ScriptMemory>> getScripts(@PathVariable String projectId) {
        return ResponseEntity.ok(memoryRepository.findScripts(projectId));
    }

    /**
     * GET /api/memory/projects/{projectId}/preferences — get preferences.
     */
    @GetMapping("/projects/{projectId}/preferences")
    public ResponseEntity<List<UserPreference>> getPreferences(@PathVariable String projectId) {
        return ResponseEntity.ok(memoryRepository.findPreferences(projectId));
    }

    /**
     * POST /api/memory/projects/{projectId}/preferences — set a preference.
     */
    @PostMapping("/projects/{projectId}/preferences")
    public ResponseEntity<?> setPreference(
            @PathVariable String projectId,
            @RequestBody Map<String, String> body) {
        String key = body.get("key");
        String value = body.get("value");
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_KEY", "message", "Key is required"));
        }
        memoryRepository.upsertPreference(projectId, key, value);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "projectId", projectId, "key", key, "value", value != null ? value : ""));
    }

    /**
     * DELETE /api/memory/projects/{projectId} — clear memory for project.
     */
    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<Map<String, Object>> deleteProjectMemory(@PathVariable String projectId) {
        projectMemoryService.deleteProjectMemory(projectId);
        return ResponseEntity.ok(Map.of(
                "status", "DELETED",
                "projectId", projectId,
                "message", "All memory data for project '" + projectId + "' has been cleared."
        ));
    }
}
