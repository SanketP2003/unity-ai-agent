package com.unityagent.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Detects local Unity Editor installations, Unity versions, and hub locations across Windows, macOS, and Linux.
 */
@Service
public class InstallationDetector {

    private static final Logger log = LoggerFactory.getLogger(InstallationDetector.class);

    public record UnityInstallation(
            String version,
            Path editorPath,
            boolean isHubManaged
    ) {}

    public List<UnityInstallation> detectInstallations() {
        List<UnityInstallation> found = new ArrayList<>();

        String os = System.getProperty("os.name", "").toLowerCase();
        List<Path> searchRoots = new ArrayList<>();

        if (os.contains("win")) {
            searchRoots.add(Paths.get("C:/Program Files/Unity/Hub/Editor"));
            searchRoots.add(Paths.get("C:/Program Files/Unity"));
        } else if (os.contains("mac") || os.contains("darwin")) {
            searchRoots.add(Paths.get("/Applications/Unity/Hub/Editor"));
            searchRoots.add(Paths.get("/Applications/Unity"));
        } else {
            // Linux
            searchRoots.add(Paths.get("/opt/unity/editors"));
            searchRoots.add(Paths.get(System.getProperty("user.home"), "Unity/Hub/Editor"));
        }

        for (Path root : searchRoots) {
            if (Files.exists(root) && Files.isDirectory(root)) {
                try (var stream = Files.list(root)) {
                    for (Path child : (Iterable<Path>) stream::iterator) {
                        if (Files.isDirectory(child)) {
                            Path editorExe = os.contains("win")
                                    ? child.resolve("Editor/Unity.exe")
                                    : child.resolve("Editor/Unity");

                            if (Files.exists(editorExe)) {
                                found.add(new UnityInstallation(child.getFileName().toString(), editorExe, true));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed scanning Unity root {}: {}", root, e.getMessage());
                }
            }
        }

        return found;
    }

    public Optional<UnityInstallation> findPreferredInstallation(String targetVersion) {
        List<UnityInstallation> installations = detectInstallations();
        if (installations.isEmpty()) return Optional.empty();

        if (targetVersion != null && !targetVersion.isBlank()) {
            Optional<UnityInstallation> match = installations.stream()
                    .filter(i -> i.version().contains(targetVersion))
                    .findFirst();
            if (match.isPresent()) return match;
        }

        return Optional.of(installations.get(0));
    }
}
