package com.unityagent.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Service providing scoped project asset resolution and enforcing reuse-over-duplicate policy
 * within a specific Unity project.
 */
@Service
public class ProjectAssetResolutionService {

    private static final Logger log = LoggerFactory.getLogger(ProjectAssetResolutionService.class);

    /**
     * Finds an existing asset within the project's Assets/ directory matching the target name and extension.
     */
    public Optional<String> findExistingAsset(String projectPath, String extension, String targetName) {
        if (projectPath == null || targetName == null) return Optional.empty();

        Path assetsDir = Paths.get(projectPath, "Assets");
        if (!Files.exists(assetsDir) || !Files.isDirectory(assetsDir)) {
            return Optional.empty();
        }

        String searchName = targetName.toLowerCase(Locale.ROOT);
        String targetExt = extension.startsWith(".") ? extension.toLowerCase(Locale.ROOT) : "." + extension.toLowerCase(Locale.ROOT);

        try (Stream<Path> stream = Files.walk(assetsDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(targetExt))
                    .filter(p -> {
                        String fname = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return fname.equals(searchName + targetExt) || fname.contains(searchName);
                    })
                    .map(p -> assetsDir.relativize(p).toString().replace('\\', '/'))
                    .findFirst();
        } catch (IOException e) {
            log.warn("Error resolving entity in {}: {}", projectPath, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Determines whether an asset should be reused instead of created anew.
     */
    public boolean shouldReuse(String projectPath, String extension, String targetName) {
        return findExistingAsset(projectPath, extension, targetName).isPresent();
    }
}
