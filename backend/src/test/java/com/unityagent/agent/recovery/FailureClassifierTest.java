package com.unityagent.agent.recovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FailureClassifierTest {

    private FailureClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new FailureClassifier();
    }

    @Test
    @DisplayName("Classifies CS1002 semicolon error as COMPILATION_SYNTAX and extracts script name")
    void testClassifiesSyntaxError() {
        String error = "Assets/Scripts/PlayerController.cs(24,15): error CS1002: ; expected";
        FailureContext ctx = classifier.classify("node_compile", "compile_project", error);

        assertNotNull(ctx);
        assertEquals(FailureType.COMPILATION_SYNTAX, ctx.getFailureType());
        assertEquals("CS1002", ctx.getErrorCode());
        assertEquals("PlayerController.cs", ctx.getTargetFileOrAsset());
    }

    @Test
    @DisplayName("Classifies CS0246 missing type as COMPILATION_TYPE and extracts missing symbol")
    void testClassifiesMissingTypeError() {
        String error = "Assets/Scripts/CombatManager.cs(10,5): error CS0246: The type or namespace name 'EnemyController' could not be found";
        FailureContext ctx = classifier.classify("node_compile", "compile_project", error);

        assertNotNull(ctx);
        assertEquals(FailureType.COMPILATION_TYPE, ctx.getFailureType());
        assertEquals("CS0246", ctx.getErrorCode());
        assertEquals("CombatManager.cs", ctx.getTargetFileOrAsset());
        assertEquals("EnemyController", ctx.getDiagnosticDetails().get("missingSymbol"));
    }

    @Test
    @DisplayName("Classifies CS1061 member missing as COMPILATION_MEMBER")
    void testClassifiesMemberMissingError() {
        String error = "Assets/Scripts/Player.cs(15,10): error CS1061: 'Rigidbody' does not contain a definition for 'speed'";
        FailureContext ctx = classifier.classify("node_compile", "compile_project", error);

        assertNotNull(ctx);
        assertEquals(FailureType.COMPILATION_MEMBER, ctx.getFailureType());
        assertEquals("CS1061", ctx.getErrorCode());
    }

    @Test
    @DisplayName("Classifies NullReferenceException as RUNTIME_NULL_REF")
    void testClassifiesNullReference() {
        String error = "NullReferenceException: Object reference not set to an instance of an object at PlayerController.Update()";
        FailureContext ctx = classifier.classify("node_test", "run_game_test", error);

        assertNotNull(ctx);
        assertEquals(FailureType.RUNTIME_NULL_REF, ctx.getFailureType());
    }

    @Test
    @DisplayName("Classifies missing component as MISSING_COMPONENT")
    void testClassifiesMissingComponent() {
        String error = "MissingComponentException: GameObject 'Player' does not have a 'Rigidbody' component attached.";
        FailureContext ctx = classifier.classify("node_action", "set_component_property", error);

        assertNotNull(ctx);
        assertEquals(FailureType.MISSING_COMPONENT, ctx.getFailureType());
    }

    @Test
    @DisplayName("Classifies destroyed or missing object as SCENE_DRIFT")
    void testClassifiesSceneDrift() {
        String error = "GameObject 'Platform_01' not found in active scene hierarchy.";
        FailureContext ctx = classifier.classify("node_action", "set_transform", error);

        assertNotNull(ctx);
        assertEquals(FailureType.SCENE_DRIFT, ctx.getFailureType());
    }

    @Test
    @DisplayName("Classifies zero movement during test as BEHAVIOR_TIMEOUT")
    void testClassifiesBehaviorTimeout() {
        String error = "Game test failed: distanceMoved = 0 after 3.0s of simulated forward input.";
        FailureContext ctx = classifier.classify("node_test", "run_game_test", error);

        assertNotNull(ctx);
        assertEquals(FailureType.BEHAVIOR_TIMEOUT, ctx.getFailureType());
    }
}
