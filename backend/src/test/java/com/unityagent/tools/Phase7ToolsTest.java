package com.unityagent.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Phase7ToolsTest {

    @Test
    void testSceneTools() {
        var openScene = new Phase7Tools.OpenScene();
        assertEquals("open_scene", openScene.name());
        assertEquals("Scene", openScene.definition().getDomain());
        assertNotNull(openScene.validate(Map.of()));
        assertNull(openScene.validate(Map.of("scenePath", "Assets/Scenes/Main.unity")));
        assertNull(openScene.validate(Map.of("sceneName", "Main")));

        var sceneInfo = new Phase7Tools.GetSceneInfo();
        assertEquals("get_scene_info", sceneInfo.name());
        assertEquals(ToolPermission.READ_ONLY, sceneInfo.permission());
        assertNull(sceneInfo.validate(Map.of()));
    }

    @Test
    void testGameObjectTools() {
        var createGo = new Phase7Tools.CreateGameObject();
        assertEquals("create_gameobject", createGo.name());
        assertEquals("GameObject", createGo.definition().getDomain());
        assertNotNull(createGo.validate(Map.of()));
        assertNotNull(createGo.validate(Map.of("name", " ")));
        assertNull(createGo.validate(Map.of("name", "Hero", "primitiveType", "Capsule")));

        var duplicateGo = new Phase7Tools.DuplicateGameObject();
        assertNotNull(duplicateGo.validate(Map.of()));
        assertNull(duplicateGo.validate(Map.of("target", "Hero")));

        var renameGo = new Phase7Tools.RenameGameObject();
        assertNotNull(renameGo.validate(Map.of("target", "Hero")));
        assertNull(renameGo.validate(Map.of("target", "Hero", "newName", "SuperHero")));

        var moveGo = new Phase7Tools.MoveGameObject();
        assertNotNull(moveGo.validate(Map.of()));
        assertNull(moveGo.validate(Map.of("target", "Hero", "dx", 5.0)));

        var findGos = new Phase7Tools.FindGameObjects();
        assertEquals(ToolPermission.READ_ONLY, findGos.permission());
        assertNull(findGos.validate(Map.of("tag", "Enemy")));
    }

    @Test
    void testComponentTools() {
        var getComp = new Phase7Tools.GetComponent();
        assertNotNull(getComp.validate(Map.of("target", "Hero")));
        assertNull(getComp.validate(Map.of("target", "Hero", "componentType", "Rigidbody")));

        var getProps = new Phase7Tools.GetComponentProperties();
        assertNotNull(getProps.validate(Map.of("componentType", "Rigidbody")));
        assertNull(getProps.validate(Map.of("target", "Hero", "componentType", "Rigidbody")));
    }

    @Test
    void testPrefabTools() {
        var createPrefab = new Phase7Tools.CreatePrefab();
        assertNotNull(createPrefab.validate(Map.of("target", "Hero")));
        assertNull(createPrefab.validate(Map.of("target", "Hero", "assetPath", "Assets/Prefabs/Hero.prefab")));

        var instantiatePrefab = new Phase7Tools.InstantiatePrefab();
        assertNotNull(instantiatePrefab.validate(Map.of()));
        assertNull(instantiatePrefab.validate(Map.of("prefabAssetPath", "Assets/Prefabs/Hero.prefab")));
    }

    @Test
    void testMaterialAndVisualTools() {
        var setMatProp = new Phase7Tools.SetMaterialProperty();
        assertNotNull(setMatProp.validate(Map.of("materialAssetPath", "Assets/Mat.mat")));
        assertNull(setMatProp.validate(Map.of(
                "materialAssetPath", "Assets/Mat.mat",
                "propertyName", "_Color",
                "value", "#FF0000"
        )));

        var assignMat = new Phase7Tools.AssignMaterial();
        assertNotNull(assignMat.validate(Map.of("target", "Hero")));
        assertNull(assignMat.validate(Map.of("target", "Hero", "materialAssetPath", "Assets/Mat.mat")));
    }

    @Test
    void testLightingAndCameraTools() {
        var configLight = new Phase7Tools.ConfigureLight();
        assertNotNull(configLight.validate(Map.of()));
        assertNull(configLight.validate(Map.of("target", "DirectionalLight", "intensity", 1.5)));

        var configCam = new Phase7Tools.ConfigureCamera();
        assertNotNull(configCam.validate(Map.of()));
        assertNull(configCam.validate(Map.of("target", "MainCamera", "fieldOfView", 60.0)));

        var followTarget = new Phase7Tools.FollowTarget();
        assertNotNull(followTarget.validate(Map.of("cameraTarget", "MainCamera")));
        assertNull(followTarget.validate(Map.of("cameraTarget", "MainCamera", "followTarget", "Player")));
    }

    @Test
    void testUITools() {
        var createCanvas = new Phase7Tools.CreateCanvas();
        assertEquals("UI", createCanvas.definition().getDomain());
        assertNull(createCanvas.validate(Map.of("name", "HUDCanvas")));

        var createText = new Phase7Tools.CreateText();
        assertNull(createText.validate(Map.of("name", "ScoreText", "text", "Score: 0")));

        var createBtn = new Phase7Tools.CreateButton();
        assertNull(createBtn.validate(Map.of("name", "StartBtn", "buttonText", "Play")));

        var bindEvent = new Phase7Tools.BindUIEvent();
        assertNotNull(bindEvent.validate(Map.of("target", "StartBtn")));
        assertNull(bindEvent.validate(Map.of(
                "target", "StartBtn",
                "targetObject", "GameManager",
                "targetComponent", "GameController",
                "targetMethod", "StartGame"
        )));
    }

    @Test
    void testPhysicsTools() {
        var configRb = new Phase7Tools.ConfigureRigidbody();
        assertEquals("Physics", configRb.definition().getDomain());
        assertNotNull(configRb.validate(Map.of()));
        assertNull(configRb.validate(Map.of("target", "Player", "mass", 2.0, "useGravity", true)));

        var configCollider = new Phase7Tools.ConfigureCollider();
        assertNotNull(configCollider.validate(Map.of()));
        assertNull(configCollider.validate(Map.of("target", "Player", "colliderType", "Box", "isTrigger", false)));

        var setGravity = new Phase7Tools.SetGravity();
        assertNotNull(setGravity.validate(Map.of()));
        assertNull(setGravity.validate(Map.of("gravity", Map.of("x", 0, "y", -9.81, "z", 0))));
    }

    @Test
    void testNavigationTools() {
        var configNav = new Phase7Tools.ConfigureNavigation();
        assertEquals("Navigation", configNav.definition().getDomain());
        assertNotNull(configNav.validate(Map.of()));
        assertNull(configNav.validate(Map.of("target", "Floor", "isNavigationStatic", true)));

        var createAgent = new Phase7Tools.CreateNavMeshAgent();
        assertNotNull(createAgent.validate(Map.of()));
        assertNull(createAgent.validate(Map.of("target", "Enemy", "speed", 3.5)));
    }

    @Test
    void testAnimationTools() {
        var createAnimController = new Phase7Tools.CreateAnimatorController();
        assertEquals("Animation", createAnimController.definition().getDomain());
        assertNotNull(createAnimController.validate(Map.of()));
        assertNull(createAnimController.validate(Map.of("assetPath", "Assets/Anim/Player.controller")));

        var createState = new Phase7Tools.CreateAnimationState();
        assertNotNull(createState.validate(Map.of("controllerPath", "Assets/Anim/Player.controller")));
        assertNull(createState.validate(Map.of("controllerPath", "Assets/Anim/Player.controller", "stateName", "Run")));
    }

    @Test
    void testAudioTools() {
        var createAudio = new Phase7Tools.CreateAudioSource();
        assertEquals("Audio", createAudio.definition().getDomain());
        assertNotNull(createAudio.validate(Map.of()));
        assertNull(createAudio.validate(Map.of("target", "BGMPlayer", "loop", true, "volume", 0.8)));

        var assignClip = new Phase7Tools.AssignAudioClip();
        assertNotNull(assignClip.validate(Map.of("target", "BGMPlayer")));
        assertNull(assignClip.validate(Map.of("target", "BGMPlayer", "audioClipPath", "Assets/Audio/Music.mp3")));
    }

    @Test
    void testInputTools() {
        var createInput = new Phase7Tools.CreateInputAction();
        assertEquals("Input", createInput.definition().getDomain());
        assertNotNull(createInput.validate(Map.of("assetPath", "Assets/Input/Game.inputactions")));
        assertNull(createInput.validate(Map.of(
                "assetPath", "Assets/Input/Game.inputactions",
                "actionName", "Jump",
                "actionType", "Button"
        )));
    }

    @Test
    void testAssetAndProjectTools() {
        var deleteAsset = new Phase7Tools.DeleteAsset();
        assertTrue(deleteAsset.isDestructive());
        assertEquals(ToolPermission.DESTRUCTIVE, deleteAsset.permission());
        assertNotNull(deleteAsset.validate(Map.of()));
        assertNull(deleteAsset.validate(Map.of("assetPath", "Assets/Old.mat")));

        var setBuild = new Phase7Tools.SetBuildSetting();
        assertEquals(ToolPermission.PROJECT_WRITE, setBuild.permission());
        assertNotNull(setBuild.validate(Map.of("settingName", "productName")));
        assertNull(setBuild.validate(Map.of("settingName", "productName", "value", "AwesomeGame")));

        var createTag = new Phase7Tools.CreateTag();
        assertNotNull(createTag.validate(Map.of()));
        assertNull(createTag.validate(Map.of("tagName", "Enemy")));

        var buildProj = new Phase7Tools.BuildProject();
        assertEquals(ToolPermission.BUILD, buildProj.permission());
        assertNotNull(buildProj.validate(Map.of()));
        assertNull(buildProj.validate(Map.of("outputPath", "Builds/Game.exe")));
    }

    @Test
    void testToolRegistryWithPhase7Tools() {
        ToolRegistry registry = new ToolRegistry(List.of(
                new Phase7Tools.OpenScene(),
                new Phase7Tools.CreateGameObject(),
                new Phase7Tools.CreateCanvas(),
                new Phase7Tools.ConfigureRigidbody(),
                new Phase7Tools.CreateNavMeshAgent(),
                new Phase7Tools.BuildProject(),
                new Phase7Tools.DeleteAsset()
        ));

        assertEquals(7, registry.size());
        assertTrue(registry.hasTool("open_scene"));
        assertTrue(registry.hasTool("create_gameobject"));
        assertTrue(registry.hasTool("create_canvas"));
        assertTrue(registry.hasTool("configure_rigidbody"));
        assertTrue(registry.hasTool("create_navmesh_agent"));
        assertTrue(registry.hasTool("build_project"));
        assertTrue(registry.hasTool("delete_asset"));

        List<Map<String, Object>> openAiDefs = registry.getOpenAIToolDefinitions();
        assertEquals(7, openAiDefs.size());
    }
}
