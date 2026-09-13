using System;
using System.IO;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEditor.Animations;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class CreateAnimatorControllerTool : IBridgeTool
    {
        public string ToolName => "create_animator_controller";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "CharacterAnimator");
            string path = ToolParamHelper.ExtractString(request.parameters, "path");

            if (string.IsNullOrEmpty(path))
            {
                path = $"Assets/Animations/{name}.controller";
            }
            if (!path.EndsWith(".controller", StringComparison.OrdinalIgnoreCase))
            {
                path += ".controller";
            }

            string dir = Path.GetDirectoryName(path);
            if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
            {
                Directory.CreateDirectory(dir);
                AssetDatabase.Refresh();
            }

            AnimatorController controller = AnimatorController.CreateAnimatorControllerAtPath(path);

            // Add standard default states if requested or by default
            var rootStateMachine = controller.layers[0].stateMachine;
            var idleState = rootStateMachine.AddState("Idle");
            rootStateMachine.defaultState = idleState;

            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Created AnimatorController at '{path}' with default 'Idle' state",
                target = path
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreateAnimationStateTool : IBridgeTool
    {
        public string ToolName => "create_animation_state";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string controllerPath = ToolParamHelper.ExtractString(request.parameters, "controllerPath");
            string stateName = ToolParamHelper.ExtractString(request.parameters, "stateName");

            if (string.IsNullOrEmpty(controllerPath) || string.IsNullOrEmpty(stateName))
            {
                return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameters 'controllerPath' and 'stateName' are required");
            }

            if (!controllerPath.StartsWith("Assets/")) controllerPath = "Assets/Animations/" + controllerPath;
            if (!controllerPath.EndsWith(".controller", StringComparison.OrdinalIgnoreCase)) controllerPath += ".controller";

            var controller = AssetDatabase.LoadAssetAtPath<AnimatorController>(controllerPath);
            if (controller == null)
            {
                return BridgeMessage.Error(request.operationId, "CONTROLLER_NOT_FOUND", $"AnimatorController not found at '{controllerPath}'");
            }

            var rootStateMachine = controller.layers[0].stateMachine;
            var state = rootStateMachine.AddState(stateName);

            AssetDatabase.SaveAssets();

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Added state '{stateName}' to '{controller.name}'",
                target = stateName
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class SetAnimationParameterTool : IBridgeTool
    {
        public string ToolName => "set_animation_parameter";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string controllerPath = ToolParamHelper.ExtractString(request.parameters, "controllerPath");
            string paramName = ToolParamHelper.ExtractString(request.parameters, "name");
            string typeStr = ToolParamHelper.ExtractString(request.parameters, "type", "Float");

            if (string.IsNullOrEmpty(controllerPath) || string.IsNullOrEmpty(paramName))
            {
                return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameters 'controllerPath' and 'name' are required");
            }

            if (!controllerPath.StartsWith("Assets/")) controllerPath = "Assets/Animations/" + controllerPath;
            if (!controllerPath.EndsWith(".controller", StringComparison.OrdinalIgnoreCase)) controllerPath += ".controller";

            var controller = AssetDatabase.LoadAssetAtPath<AnimatorController>(controllerPath);
            if (controller == null)
            {
                return BridgeMessage.Error(request.operationId, "CONTROLLER_NOT_FOUND", $"AnimatorController not found at '{controllerPath}'");
            }

            AnimatorControllerParameterType paramType = AnimatorControllerParameterType.Float;
            switch (typeStr.ToLowerInvariant())
            {
                case "int": paramType = AnimatorControllerParameterType.Int; break;
                case "bool": paramType = AnimatorControllerParameterType.Bool; break;
                case "trigger": paramType = AnimatorControllerParameterType.Trigger; break;
                default: paramType = AnimatorControllerParameterType.Float; break;
            }

            controller.AddParameter(paramName, paramType);
            AssetDatabase.SaveAssets();

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Added parameter '{paramName}' ({paramType}) to '{controller.name}'",
                target = paramName
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreateAnimationTransitionTool : IBridgeTool
    {
        public string ToolName => "create_animation_transition";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string controllerPath = ToolParamHelper.ExtractString(request.parameters, "controllerPath");
            string srcStateName = ToolParamHelper.ExtractString(request.parameters, "sourceState");
            string dstStateName = ToolParamHelper.ExtractString(request.parameters, "destState");

            if (string.IsNullOrEmpty(controllerPath))
            {
                return BridgeMessage.Error(request.operationId, "MISSING_PARAM", "Parameter 'controllerPath' is required");
            }

            if (!controllerPath.StartsWith("Assets/")) controllerPath = "Assets/Animations/" + controllerPath;
            if (!controllerPath.EndsWith(".controller", StringComparison.OrdinalIgnoreCase)) controllerPath += ".controller";

            var controller = AssetDatabase.LoadAssetAtPath<AnimatorController>(controllerPath);
            if (controller == null)
            {
                return BridgeMessage.Error(request.operationId, "CONTROLLER_NOT_FOUND", $"AnimatorController not found at '{controllerPath}'");
            }

            var sm = controller.layers[0].stateMachine;
            AnimatorState src = null;
            AnimatorState dst = null;

            foreach (var childState in sm.states)
            {
                if (childState.state.name.Equals(srcStateName, StringComparison.OrdinalIgnoreCase)) src = childState.state;
                if (childState.state.name.Equals(dstStateName, StringComparison.OrdinalIgnoreCase)) dst = childState.state;
            }

            if (src == null || dst == null)
            {
                return BridgeMessage.Error(request.operationId, "STATE_NOT_FOUND", $"Could not locate source '{srcStateName}' or dest '{dstStateName}' in controller");
            }

            var transition = src.AddTransition(dst);
            bool hasExitTime = ToolParamHelper.ExtractBool(request.parameters, "hasExitTime", false);
            transition.hasExitTime = hasExitTime;

            string condParam = ToolParamHelper.ExtractString(request.parameters, "conditionParam");
            if (!string.IsNullOrEmpty(condParam))
            {
                transition.AddCondition(AnimatorConditionMode.Greater, 0.1f, condParam);
            }

            AssetDatabase.SaveAssets();

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Created transition from '{src.name}' to '{dst.name}' in '{controller.name}'",
                target = src.name + "->" + dst.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class AssignAnimatorControllerTool : IBridgeTool
    {
        public string ToolName => "assign_animator_controller";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string controllerPath = ToolParamHelper.ExtractString(request.parameters, "controllerPath");

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Target '{target}' not found");

            if (!controllerPath.StartsWith("Assets/")) controllerPath = "Assets/Animations/" + controllerPath;
            if (!controllerPath.EndsWith(".controller", StringComparison.OrdinalIgnoreCase)) controllerPath += ".controller";

            var controller = AssetDatabase.LoadAssetAtPath<RuntimeAnimatorController>(controllerPath);
            if (controller == null)
            {
                return BridgeMessage.Error(request.operationId, "CONTROLLER_NOT_FOUND", $"Controller not found at '{controllerPath}'");
            }

            var anim = go.GetComponent<Animator>();
            if (anim == null) anim = go.AddComponent<Animator>();
            Undo.RecordObject(anim, "Assign AnimatorController " + go.name);
            anim.runtimeAnimatorController = controller;

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Assigned controller '{controller.name}' to Animator on '{go.name}'",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class GetAnimatorInfoTool : IBridgeTool
    {
        public string ToolName => "get_animator_info";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Target '{target}' not found");

            var anim = go.GetComponent<Animator>();
            if (anim == null) return BridgeMessage.Error(request.operationId, "NO_ANIMATOR", $"'{target}' does not have Animator component");

            string ctrlName = anim.runtimeAnimatorController != null ? anim.runtimeAnimatorController.name : "null";

            string json = "{"
                + "\"target\":\"" + go.name + "\","
                + "\"hasController\":" + (anim.runtimeAnimatorController != null ? "true" : "false") + ","
                + "\"controllerName\":\"" + ctrlName + "\""
                + "}";

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
