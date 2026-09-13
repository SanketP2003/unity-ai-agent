package com.unityagent.agent.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretScanningTest {

    private static final List<Pattern> LIVE_SECRET_PATTERNS = List.of(
            // OpenAI live key format
            Pattern.compile("sk-[a-zA-Z0-9]{20,}"),
            // NVIDIA NIM API key format
            Pattern.compile("nvapi-[a-zA-Z0-9_-]{20,}"),
            // GitHub personal access token
            Pattern.compile("ghp_[a-zA-Z0-9]{20,}"),
            // Hardcoded authorization bearer with concrete key
            Pattern.compile("Bearer\\s+[a-zA-Z0-9_\\-]{30,}")
    );

    @Test
    void testNoHardcodedSecretsInSourceCode() throws IOException {
        Path srcMain = Paths.get("src", "main");
        if (!Files.exists(srcMain)) {
            // Fallback for different working dir
            srcMain = Paths.get("autonomous-unity-agent", "backend", "src", "main");
        }
        assertTrue(Files.exists(srcMain), "src/main must exist");

        try (Stream<Path> paths = Files.walk(srcMain)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".properties") || p.toString().endsWith(".yml"))
                 .forEach(this::scanFileForSecrets);
        }
    }

    private void scanFileForSecrets(Path file) {
        try {
            String content = Files.readString(file);
            for (Pattern pattern : LIVE_SECRET_PATTERNS) {
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    org.junit.jupiter.api.Assertions.fail(
                            "Secret detected in " + file.toString() + " matching pattern " + pattern.pattern()
                    );
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed reading file during secret scan: " + file, e);
        }
    }
}
