package com.unityagent.memory.service;

import com.unityagent.memory.MemoryRepository;
import com.unityagent.memory.model.ProjectMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing project identity and metadata in persistent memory.
 * Called on Unity handshake to register/restore projects.
 */
@Service
public class ProjectMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ProjectMemoryService.class);
    private final MemoryRepository repository;

    public ProjectMemoryService(MemoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Register or update a project on handshake.
     */
    public ProjectMemory registerProject(String projectId, String unityVersion,
                                          String projectName, String extensionVersion,
                                          Map<String, Object> capabilities) {
        Optional<ProjectMemory> existing = repository.findProject(projectId);

        ProjectMemory project;
        if (existing.isPresent()) {
            project = existing.get();
            project.setUnityVersion(unityVersion);
            if (projectName != null) project.setProjectName(projectName);
            project.setExtensionVersion(extensionVersion);
            project.setLastConnectedAt(Instant.now());
            log.info("Restored project memory: projectId={}, name={}", projectId, project.getProjectName());
        } else {
            project = new ProjectMemory(projectId, projectName, unityVersion,
                    null, null, extensionVersion, null);
            log.info("Created new project memory: projectId={}, name={}", projectId, projectName);
        }

        repository.upsertProject(project);
        return project;
    }

    /**
     * Update the last agent run for a project.
     */
    public void updateLastAgentRun(String projectId, String agentRunId) {
        repository.findProject(projectId).ifPresent(p -> {
            p.setLastAgentRunId(agentRunId);
            p.setLastConnectedAt(Instant.now());
            repository.upsertProject(p);
        });
    }

    public Optional<ProjectMemory> getProject(String projectId) {
        return repository.findProject(projectId);
    }

    public List<ProjectMemory> getAllProjects() {
        return repository.findAllProjects();
    }

    public void deleteProjectMemory(String projectId) {
        repository.deleteAllProjectData(projectId);
        log.info("Deleted all memory for project {}", projectId);
    }
}
