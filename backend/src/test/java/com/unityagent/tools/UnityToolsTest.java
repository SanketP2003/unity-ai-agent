package com.unityagent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UnityToolsTest {

    private UnityTools.CreatePrimitive createPrimitive;
    private UnityTools.CreateEmptyGameObject createEmpty;
    private UnityTools.SetTransform setTransform;
    private UnityTools.AddComponent addComponent;
    private UnityTools.SetComponentProperty setComponentProp;
    private UnityTools.SetMaterialColor setMaterialColor;
    private UnityTools.CreateLight createLight;
    private UnityTools.SetPlayMode setPlayMode;
    private UnityTools.GetConsoleLogs getConsoleLogs;

    @BeforeEach
    void setUp() {
        createPrimitive = new UnityTools.CreatePrimitive();
        createEmpty = new UnityTools.CreateEmptyGameObject();
        setTransform = new UnityTools.SetTransform();
        addComponent = new UnityTools.AddComponent();
        setComponentProp = new UnityTools.SetComponentProperty();
        setMaterialColor = new UnityTools.SetMaterialColor();
        createLight = new UnityTools.CreateLight();
        setPlayMode = new UnityTools.SetPlayMode();
        getConsoleLogs = new UnityTools.GetConsoleLogs();
    }

    @Test
    void testCreatePrimitiveValidation() {
        // Missing type
        assertNotNull(createPrimitive.validate(Map.of()));
        // Invalid type
        assertNotNull(createPrimitive.validate(Map.of("type", "InvalidShape")));

        // Valid Cube with transform
        Map<String, Object> validParams = Map.of(
                "type", "Cube",
                "name", "MyCube",
                "position", Map.of("x", 0.0, "y", 1.0, "z", 0.0),
                "scale", Map.of("x", 2.0, "y", 2.0, "z", 2.0)
        );
        assertNull(createPrimitive.validate(validParams));

        // Invalid position field
        Map<String, Object> invalidPos = Map.of(
                "type", "Sphere",
                "position", "not-a-vector"
        );
        assertNotNull(createPrimitive.validate(invalidPos));
    }

    @Test
    void testCreateEmptyGameObjectValidation() {
        assertNotNull(createEmpty.validate(Map.of()));
        assertNotNull(createEmpty.validate(Map.of("name", " ")));
        assertNull(createEmpty.validate(Map.of("name", "PlayerRoot")));
    }

    @Test
    void testSetTransformValidation() {
        // Missing target
        assertNotNull(setTransform.validate(Map.of("position", Map.of("x", 1, "y", 2, "z", 3))));

        // Missing all transform fields
        assertNotNull(setTransform.validate(Map.of("target", "Player")));

        // Valid
        assertNull(setTransform.validate(Map.of(
                "target", "Player",
                "position", Map.of("x", 1.0, "y", 2.0, "z", 3.0),
                "space", "World"
        )));

        // Invalid space
        assertNotNull(setTransform.validate(Map.of(
                "target", "Player",
                "position", Map.of("x", 1.0, "y", 2.0, "z", 3.0),
                "space", "InvalidSpace"
        )));
    }

    @Test
    void testAddComponentValidation() {
        assertNotNull(addComponent.validate(Map.of("target", "Cube")));
        assertNotNull(addComponent.validate(Map.of("componentType", "Rigidbody")));
        assertNull(addComponent.validate(Map.of("target", "Cube", "componentType", "Rigidbody")));
    }

    @Test
    void testSetComponentPropertyValidation() {
        assertNotNull(setComponentProp.validate(Map.of("target", "Cube", "componentType", "Rigidbody")));
        assertNull(setComponentProp.validate(Map.of(
                "target", "Cube",
                "componentType", "Rigidbody",
                "property", "isKinematic",
                "value", true
        )));
    }

    @Test
    void testSetMaterialColorValidation() {
        // Missing color
        assertNotNull(setMaterialColor.validate(Map.of("target", "Cube")));

        // Valid hex color
        assertNull(setMaterialColor.validate(Map.of("target", "Cube", "color", "#FF0000")));
        assertNull(setMaterialColor.validate(Map.of("target", "Cube", "color", "#00FF00AA")));

        // Invalid hex
        assertNotNull(setMaterialColor.validate(Map.of("target", "Cube", "color", "red")));

        // Valid RGBA map
        assertNull(setMaterialColor.validate(Map.of(
                "target", "Cube",
                "color", Map.of("r", 1.0, "g", 0.0, "b", 0.0)
        )));
    }

    @Test
    void testCreateLightValidation() {
        assertNotNull(createLight.validate(Map.of()));
        assertNotNull(createLight.validate(Map.of("lightType", "Laser")));
        assertNull(createLight.validate(Map.of(
                "lightType", "Directional",
                "intensity", 1.5,
                "color", "#FFFFEE"
        )));
    }

    @Test
    void testSetPlayModeValidation() {
        assertNotNull(setPlayMode.validate(Map.of()));
        assertNotNull(setPlayMode.validate(Map.of("play", "yes")));
        assertNull(setPlayMode.validate(Map.of("play", true)));
    }

    @Test
    void testGetConsoleLogsValidation() {
        assertNull(getConsoleLogs.validate(Map.of()));
        assertNull(getConsoleLogs.validate(Map.of("count", 10, "logType", "Error")));
        assertNotNull(getConsoleLogs.validate(Map.of("logType", "Notice")));
    }
}
