package com.unityagent.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Supply Chain Security & Hardening Tests")
class SupplyChainSecurityTest {

    // Regex for actual production-like API keys (excluding intentional mock test values)
    private static final Pattern REAL_API_KEY_PATTERN = Pattern.compile("(?i)(sk-[a-zA-Z0-9]{40,}|ghp_[a-zA-Z0-9]{36}|AKIA[0-9A-Z]{16})");

    @Test
    @DisplayName("Verify no real API keys or sensitive cloud tokens exist in backend sources")
    void testNoHardcodedSecretsInSource() throws IOException {
        Path srcDir = Paths.get("src", "main").toAbsolutePath().normalize();
        if (!Files.exists(srcDir)) {
            srcDir = Paths.get("backend", "src", "main").toAbsolutePath().normalize();
        }

        assertTrue(Files.exists(srcDir), "Source directory must exist: " + srcDir);

        List<String> findings = new ArrayList<>();

        Files.walkFileTree(srcDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (name.endsWith(".java") || name.endsWith(".yml") || name.endsWith(".properties") || name.endsWith(".xml")) {
                    String content = Files.readString(file);
                    Matcher matcher = REAL_API_KEY_PATTERN.matcher(content);
                    if (matcher.find()) {
                        findings.add(file.toString() + ": " + matcher.group());
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        assertTrue(findings.isEmpty(), "Found potential secret in sources: " + findings);
    }

    @Test
    @DisplayName("Verify .gitignore contains sensitive credential patterns and build outputs")
    void testGitignoreContainsSecurityPatterns() throws IOException {
        Path gitignore = Paths.get("..", ".gitignore").toAbsolutePath().normalize();
        if (!Files.exists(gitignore)) {
            gitignore = Paths.get(".gitignore").toAbsolutePath().normalize();
        }

        assertTrue(Files.exists(gitignore), ".gitignore must exist");
        String content = Files.readString(gitignore);

        assertTrue(content.contains(".env"), ".gitignore must block .env files");
        assertTrue(content.contains("*.log"), ".gitignore must block log files");
    }
}
