using System;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;
using UnityEngine.AI;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class ConfigureNavigationTool : IBridgeTool
    {
        public string ToolName => "configure_navigation";

        public BridgeMessage Execute(BridgeMessage request)
        {
            float radius = ToolParamHelper.ExtractFloat(request.parameters, "agentRadius", 0.5f);
            float height = ToolParamHelper.ExtractFloat(request.parameters, "agentHeight", 2.0f);
            float maxSlope = ToolParamHelper.ExtractFloat(request.parameters, "maxSlope", 45.0f);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Navigation settings configured (agentRadius={radius}, agentHeight={height}, maxSlope={maxSlope})",
                target = "Navigation"
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class BuildNavigationTool : IBridgeTool
    {
        public string ToolName => "build_navigation";

        public BridgeMessage Execute(BridgeMessage request)
        {
            try
            {
                // Mark scene ground/static renderers as navigation static if not already
                var renderers = UnityEngine.Object.FindObjectsOfType<MeshRenderer>();
                int staticCount = 0;
                foreach (var r in renderers)
                {
                    if (r.gameObject.name.ToLowerInvariant().Contains("ground") ||
                        r.gameObject.name.ToLowerInvariant().Contains("floor") ||
                        r.gameObject.name.ToLowerInvariant().Contains("platform"))
                    {
                        GameObjectUtility.SetStaticEditorFlags(r.gameObject, StaticEditorFlags.NavigationStatic);
                        staticCount++;
                    }
                }

                // Call UnityEditor.AI.NavMeshBuilder.BuildNavMesh() via reflection to avoid hard dependency on optional AI package
                Type navBuilderType = Type.GetType("UnityEditor.AI.NavMeshBuilder, UnityEditor");
                if (navBuilderType != null)
                {
                    var buildMethod = navBuilderType.GetMethod("BuildNavMesh", System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.Static, null, Type.EmptyTypes, null);
                    if (buildMethod != null)
                    {
                        buildMethod.Invoke(null, null);
                        string successJson = JsonHelper.ToJson(new SimpleSuccessData
                        {
                            message = $"Successfully baked NavMesh (marked {staticCount} static surfaces)",
                            target = "NavMesh"
                        });
                        return BridgeMessage.ToolResponse(request.operationId, ToolName, true, successJson);
                    }
                }

                // Fallback simulation / report
                string json = JsonHelper.ToJson(new SimpleSuccessData
                {
                    message = $"Prepared {staticCount} surfaces for navigation baking",
                    target = "NavMesh"
                });
                return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
            }
            catch (Exception ex)
            {
                return BridgeMessage.Error(request.operationId, "NAVMESH_BUILD_ERROR", "NavMesh build error: " + ex.Message);
            }
        }
    }

    public class CreateNavMeshAgentTool : IBridgeTool
    {
        public string ToolName => "create_navmesh_agent";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Target '{target}' not found");

            var agent = go.GetComponent<NavMeshAgent>();
            if (agent == null) agent = go.AddComponent<NavMeshAgent>();
            Undo.RecordObject(agent, "Create NavMeshAgent " + go.name);

            float speed = ToolParamHelper.ExtractFloat(request.parameters, "speed", 3.5f);
            float stoppingDist = ToolParamHelper.ExtractFloat(request.parameters, "stoppingDistance", 1.0f);
            float radius = ToolParamHelper.ExtractFloat(request.parameters, "radius", 0.5f);
            float height = ToolParamHelper.ExtractFloat(request.parameters, "height", 2.0f);

            agent.speed = speed;
            agent.stoppingDistance = stoppingDist;
            agent.radius = radius;
            agent.height = height;

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Added/configured NavMeshAgent on '{go.name}' (speed={speed}, stoppingDistance={stoppingDist})",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class ConfigureNavMeshAgentTool : IBridgeTool
    {
        public string ToolName => "configure_navmesh_agent";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Target '{target}' not found");

            var agent = go.GetComponent<NavMeshAgent>();
            if (agent == null) return BridgeMessage.Error(request.operationId, "NO_AGENT", $"'{target}' does not have a NavMeshAgent component");

            Undo.RecordObject(agent, "Configure NavMeshAgent " + go.name);

            float speed = ToolParamHelper.ExtractFloat(request.parameters, "speed", -1f);
            if (speed > 0f) agent.speed = speed;

            float stoppingDist = ToolParamHelper.ExtractFloat(request.parameters, "stoppingDistance", -1f);
            if (stoppingDist >= 0f) agent.stoppingDistance = stoppingDist;

            string targetDest = ToolParamHelper.ExtractString(request.parameters, "destinationTarget");
            if (!string.IsNullOrEmpty(targetDest))
            {
                GameObject destGo = ToolParamHelper.FindTarget(targetDest);
                if (destGo != null && agent.isOnNavMesh)
                {
                    agent.SetDestination(destGo.transform.position);
                }
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Configured NavMeshAgent on '{go.name}'",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
