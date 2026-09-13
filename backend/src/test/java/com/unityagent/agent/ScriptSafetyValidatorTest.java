package com.unityagent.agent;

import com.unityagent.agent.security.ScriptSafetyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6 Script Safety & Sandboxing Validator Tests")
class ScriptSafetyValidatorTest {

    private ScriptSafetyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ScriptSafetyValidator();
    }

    @Test
    @DisplayName("Valid Assets C# paths are accepted and normalized")
    void testValidPaths() {
        ScriptSafetyValidator.ValidationResult r1 = validator.validatePath("Assets/Scripts/PlayerController.cs");
        assertTrue(r1.isValid());
        assertEquals("Assets/Scripts/PlayerController.cs", r1.getNormalizedPath());

        ScriptSafetyValidator.ValidationResult r2 = validator.validatePath("Assets\\Scripts\\Enemy.cs");
        assertTrue(r2.isValid());
        assertEquals("Assets/Scripts/Enemy.cs", r2.getNormalizedPath());

        ScriptSafetyValidator.ValidationResult r3 = validator.validatePath("Scripts/GameManager.cs");
        assertTrue(r3.isValid());
        assertEquals("Assets/Scripts/GameManager.cs", r3.getNormalizedPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../Outside.cs",
            "Assets/../Outside.cs",
            "Assets/..\\Escape.cs",
            "C:\\Windows\\System32\\malicious.cs",
            "D:/Projects/External.cs",
            "/etc/shadow.cs",
            "//network-share/script.cs",
            "Assets/Windows/System/hacker.cs",
            "Assets/Program Files/test.cs",
            "Assets/Scripts/Malicious.exe",
            "Assets/Scripts/Malicious.dll",
            "Assets/Scripts/Malicious.txt"
    })
    @DisplayName("Path traversal, absolute paths, and non-cs files are strictly rejected")
    void testInvalidPaths(String invalidPath) {
        ScriptSafetyValidator.ValidationResult result = validator.validatePath(invalidPath);
        assertFalse(result.isValid(), "Should reject: " + invalidPath);
        assertNotNull(result.getViolationMessage());
    }

    @Test
    @DisplayName("Safe Unity MonoBehaviours pass content validation")
    void testSafeUnityContent() {
        String code = """
                using UnityEngine;
                
                public class PlayerController : MonoBehaviour
                {
                    public float speed = 5.0f;
                    private Rigidbody rb;
                    
                    void Start()
                    {
                        rb = GetComponent<Rigidbody>();
                    }
                    
                    void Update()
                    {
                        float h = Input.GetAxis("Horizontal");
                        float v = Input.GetAxis("Vertical");
                        Vector3 movement = new Vector3(h, 0, v) * speed * Time.deltaTime;
                        transform.Translate(movement);
                    }
                }
                """;

        ScriptSafetyValidator.ValidationResult result = validator.validateContent(code);
        assertTrue(result.isValid(), "Safe Unity code should be valid");
        assertNull(result.getViolationMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "using System.Diagnostics; public class Hack { void Run() { Process.Start(\"cmd.exe\"); } }",
            "public class Hack { void Run() { var p = new ProcessStartInfo(\"calc.exe\"); } }",
            "using System.Runtime.InteropServices; public class Hack { [DllImport(\"user32.dll\")] static extern void Msg(); }",
            "public class Hack { extern void NativeExploit(); }",
            "using System.Reflection; public class Hack { void Run() { Assembly.Load(\"Malicious\"); } }",
            "using System.Reflection; public class Hack { void Run() { Assembly.LoadFrom(\"Malicious.dll\"); } }",
            "public class Hack { void Run() { System.Environment.Exit(1); } }",
            "public class Hack { void Run() { Environment.SetEnvironmentVariable(\"PATH\", \"C:\\\\\"); } }",
            "using Microsoft.Win32; public class Hack { void Run() { Registry.SetValue(\"key\", \"val\", null); } }",
            "using System.Net.Sockets; public class Hack { void Run() { var s = new Socket(SocketType.Stream, ProtocolType.Tcp); } }",
            "public class Hack { void Run() { var client = new TcpClient(\"127.0.0.1\", 8080); } }"
    })
    @DisplayName("Dangerous C# APIs, native interop, sockets, and processes are strictly rejected")
    void testDangerousContent(String dangerousCode) {
        ScriptSafetyValidator.ValidationResult result = validator.validateContent(dangerousCode);
        assertFalse(result.isValid(), "Should reject dangerous code: " + dangerousCode);
        assertNotNull(result.getViolationMessage());
        assertTrue(result.getViolationMessage().contains("prohibited API"));
    }

    @Test
    @DisplayName("Deterministic SHA-256 hash calculation with CRLF normalization")
    void testHashCalculation() {
        String unix = "using UnityEngine;\npublic class Test {}\n";
        String windows = "using UnityEngine;\r\npublic class Test {}\r\n";

        String hashUnix = ScriptSafetyValidator.computeHash(unix);
        String hashWindows = ScriptSafetyValidator.computeHash(windows);

        assertNotNull(hashUnix);
        assertEquals(64, hashUnix.length());
        assertEquals(hashUnix, hashWindows, "CRLF and LF must produce identical SHA-256 hashes");
    }

    @Test
    @DisplayName("Mixed-case and lower-case variants of dangerous APIs are strictly rejected")
    void testMixedCaseApiBypass() {
        assertFalse(validator.validateContent("using system.diagnostics;").isValid(), "system.diagnostics must be rejected");
        assertFalse(validator.validateContent("using System.Diagnostics;").isValid(), "System.Diagnostics must be rejected");
        assertFalse(validator.validateContent("using SYSTEM.DIAGNOSTICS;").isValid(), "SYSTEM.DIAGNOSTICS must be rejected");
        assertFalse(validator.validateContent("class Hack { [dllimport(\"user32.dll\")] static extern void M(); }").isValid(), "dllimport must be rejected");
        assertFalse(validator.validateContent("class Hack { [DllImport(\"user32.dll\")] static extern void M(); }").isValid(), "DllImport must be rejected");
    }

    @Test
    @DisplayName("Performance: validation of large scripts (>100KB) completes within 5 seconds")
    void testLargeScriptContentPerformance() {
        StringBuilder sb = new StringBuilder();
        sb.append("using UnityEngine;\n\npublic class LargePerformanceScript : MonoBehaviour {\n");
        for (int i = 0; i < 2500; i++) {
            sb.append("    public int field").append(i).append(" = ").append(i).append(";\n");
            sb.append("    public void Method").append(i).append("() { Debug.Log(\"Safe message ").append(i).append("\"); }\n");
        }
        sb.append("}\n");

        String content = sb.toString();
        assertTrue(content.length() >= 100 * 1024, "Content size must be at least 100 KB, actual: " + content.length());

        long start = System.currentTimeMillis();
        ScriptSafetyValidator.ValidationResult result = validator.validate("Assets/Scripts/LargePerformanceScript.cs", content);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result.isValid(), "Valid large script should pass safety validation");
        assertTrue(elapsed < 5000, "Validation should complete within 5 seconds, actual: " + elapsed + " ms");
    }
}
