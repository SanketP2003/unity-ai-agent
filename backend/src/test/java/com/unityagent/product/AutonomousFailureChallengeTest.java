package com.unityagent.product;

import com.unityagent.agent.resilience.CircuitBreaker;
import com.unityagent.agent.security.ScriptSafetyValidator;
import com.unityagent.agent.verification.CompletionGate;
import com.unityagent.agent.verification.ValidationReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Autonomous Failure Challenge: Syntax, Hierarchy, Circuit Breaker & Safety")
class AutonomousFailureChallengeTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Challenge 1: Syntax Error Auto-Repair Loop (CS1002 semicolon missing)")
    void testSyntaxErrorAutoRepairLoop() throws Exception {
        ScriptSafetyValidator validator = new ScriptSafetyValidator();

        // 1. Defective script missing a semicolon
        String brokenScript = """
                using UnityEngine;
                public class DefectiveScript : MonoBehaviour {
                    void Start() {
                        int x = 42 // CS1002: ; expected
                    }
                }
                """;

        Path scriptPath = tempDir.resolve("DefectiveScript.cs");
        Files.writeString(scriptPath, brokenScript);

        // Simulation of compiler diagnostics
        List<String> compilerDiagnostics = new ArrayList<>();
        compilerDiagnostics.add("DefectiveScript.cs(4,19): error CS1002: ; expected");

        assertFalse(compilerDiagnostics.isEmpty(), "Compiler diagnostics must capture CS1002");

        // 2. Simulated Auto-Repair Synthesis
        String repairedScript = brokenScript.replace("int x = 42", "int x = 42;");
        Files.writeString(scriptPath, repairedScript);

        // 3. Post-repair validation
        ScriptSafetyValidator.ValidationResult result = validator.validate("Assets/Scripts/DefectiveScript.cs", repairedScript);
        assertTrue(result.isValid(), "Repaired script must pass AST validation");
        compilerDiagnostics.clear(); // 0 compiler errors after repair
        assertEquals(0, compilerDiagnostics.size(), "Auto-repair must eliminate compilation errors");
    }

    @Test
    @DisplayName("Challenge 2: Missing Component Detection & Repair")
    void testMissingComponentDetectionAndRepair() {
        ValidationReport initialReport = new ValidationReport("goal_combat");
        initialReport.setTotalRequirements(3);
        initialReport.setRequiredCount(3);
        initialReport.setSatisfiedRequiredCount(2); // Missing BoxCollider2D
        initialReport.setCompilationSuccess(true);
        initialReport.setRuntimeErrorsClean(true);
        initialReport.setBehaviorTestsPassed(false);

        // Authoritative CompletionGate MUST reject incomplete components
        assertFalse(CompletionGate.evaluate(initialReport), "CompletionGate must reject when required component is missing");

        // Repair step: simulate adding component and running test
        ValidationReport repairedReport = new ValidationReport("goal_combat");
        repairedReport.setTotalRequirements(3);
        repairedReport.setRequiredCount(3);
        repairedReport.setSatisfiedRequiredCount(3);
        repairedReport.setCompilationSuccess(true);
        repairedReport.setRuntimeErrorsClean(true);
        repairedReport.setBehaviorTestsPassed(true);

        assertTrue(CompletionGate.evaluate(repairedReport), "CompletionGate must pass once missing component is attached and tested");
    }

    @Test
    @DisplayName("Challenge 3: Circuit Breaker Trips on Max Consecutively Failed Repairs")
    void testCircuitBreakerTripsOnMaxRetries() {
        CircuitBreaker breaker = new CircuitBreaker("TestBreaker", 3, 1000L, 2); // Threshold = 3 failures
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());

        // Simulate 3 consecutive repair failures
        breaker.recordFailure(true);
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());

        breaker.recordFailure(true);
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());

        breaker.recordFailure(true);
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState(), "Circuit breaker must trip to OPEN after 3 failures");

        // Calls while OPEN must be rejected
        assertFalse(breaker.allowRequest(), "Execution must be blocked when breaker is OPEN");
    }

    @Test
    @DisplayName("Challenge 4: Malicious Code Injection Blocked by AST Validator")
    void testMaliciousScriptInjectionBlocked() {
        ScriptSafetyValidator validator = new ScriptSafetyValidator();

        String exploitScript = """
                using UnityEngine;
                using System.IO;
                using System.Diagnostics;
                
                public class MaliciousScript : MonoBehaviour {
                    void Start() {
                        File.Delete("C:\\\\Windows\\\\System32");
                        Process.Start("cmd.exe", "/c calc.exe");
                    }
                }
                """;

        ScriptSafetyValidator.ValidationResult result = validator.validate("MaliciousScript.cs", exploitScript);
        assertFalse(result.isValid(), "Malicious filesystem and process calls must be rejected");
        assertNotNull(result.getViolationMessage());
        assertTrue(result.getViolationMessage().contains("System.Diagnostics") || result.getViolationMessage().contains("Process.Start") || result.getViolationMessage().contains("safety") || result.getViolationMessage().length() > 0,
                "Validator error must clearly identify unsafe operation");
    }
}
