using System;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class ConfigureRigidbodyTool : IBridgeTool
    {
        public string ToolName => "configure_rigidbody";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"GameObject '{target}' not found");

            var rb = go.GetComponent<Rigidbody>();
            if (rb == null) rb = go.AddComponent<Rigidbody>();
            Undo.RecordObject(rb, "Configure Rigidbody " + go.name);

            float mass = ToolParamHelper.ExtractFloat(request.parameters, "mass", -1f);
            if (mass > 0f) rb.mass = mass;

            float drag = ToolParamHelper.ExtractFloat(request.parameters, "drag", -1f);
            if (drag >= 0f) rb.linearDamping = drag;

            float angularDrag = ToolParamHelper.ExtractFloat(request.parameters, "angularDrag", -1f);
            if (angularDrag >= 0f) rb.angularDamping = angularDrag;

            if (request.parameters.Contains("\"useGravity\""))
            {
                rb.useGravity = ToolParamHelper.ExtractBool(request.parameters, "useGravity", true);
            }

            if (request.parameters.Contains("\"isKinematic\""))
            {
                rb.isKinematic = ToolParamHelper.ExtractBool(request.parameters, "isKinematic", false);
            }

            // Constraints
            RigidbodyConstraints constraints = rb.constraints;
            if (ToolParamHelper.ExtractBool(request.parameters, "freezeRotationX", false)) constraints |= RigidbodyConstraints.FreezeRotationX;
            if (ToolParamHelper.ExtractBool(request.parameters, "freezeRotationY", false)) constraints |= RigidbodyConstraints.FreezeRotationY;
            if (ToolParamHelper.ExtractBool(request.parameters, "freezeRotationZ", false)) constraints |= RigidbodyConstraints.FreezeRotationZ;
            if (ToolParamHelper.ExtractBool(request.parameters, "freezePositionX", false)) constraints |= RigidbodyConstraints.FreezePositionX;
            if (ToolParamHelper.ExtractBool(request.parameters, "freezePositionY", false)) constraints |= RigidbodyConstraints.FreezePositionY;
            if (ToolParamHelper.ExtractBool(request.parameters, "freezePositionZ", false)) constraints |= RigidbodyConstraints.FreezePositionZ;
            rb.constraints = constraints;

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Configured Rigidbody on '{go.name}' (mass={rb.mass}, gravity={rb.useGravity}, kinematic={rb.isKinematic})",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class ConfigureColliderTool : IBridgeTool
    {
        public string ToolName => "configure_collider";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"GameObject '{target}' not found");

            string colType = ToolParamHelper.ExtractString(request.parameters, "colliderType", "box").ToLowerInvariant();
            Collider col = go.GetComponent<Collider>();

            if (col == null)
            {
                switch (colType)
                {
                    case "sphere": col = go.AddComponent<SphereCollider>(); break;
                    case "capsule": col = go.AddComponent<CapsuleCollider>(); break;
                    case "mesh": col = go.AddComponent<MeshCollider>(); break;
                    default: col = go.AddComponent<BoxCollider>(); break;
                }
            }

            Undo.RecordObject(col, "Configure Collider " + go.name);

            if (request.parameters.Contains("\"isTrigger\""))
            {
                col.isTrigger = ToolParamHelper.ExtractBool(request.parameters, "isTrigger", false);
            }

            if (col is BoxCollider box)
            {
                float sx = ToolParamHelper.ExtractFloat(request.parameters, "sizeX", -1f);
                float sy = ToolParamHelper.ExtractFloat(request.parameters, "sizeY", -1f);
                float sz = ToolParamHelper.ExtractFloat(request.parameters, "sizeZ", -1f);
                if (sx > 0f || sy > 0f || sz > 0f)
                {
                    box.size = new Vector3(sx > 0f ? sx : box.size.x, sy > 0f ? sy : box.size.y, sz > 0f ? sz : box.size.z);
                }
            }
            else if (col is SphereCollider sphere)
            {
                float radius = ToolParamHelper.ExtractFloat(request.parameters, "radius", -1f);
                if (radius > 0f) sphere.radius = radius;
            }
            else if (col is CapsuleCollider capsule)
            {
                float radius = ToolParamHelper.ExtractFloat(request.parameters, "radius", -1f);
                float height = ToolParamHelper.ExtractFloat(request.parameters, "height", -1f);
                if (radius > 0f) capsule.radius = radius;
                if (height > 0f) capsule.height = height;
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Configured {col.GetType().Name} on '{go.name}' (isTrigger={col.isTrigger})",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreatePhysicsMaterialTool : IBridgeTool
    {
        public string ToolName => "create_physics_material";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "BouncyMaterial");
            float dynamicFriction = ToolParamHelper.ExtractFloat(request.parameters, "dynamicFriction", 0.6f);
            float staticFriction = ToolParamHelper.ExtractFloat(request.parameters, "staticFriction", 0.6f);
            float bounciness = ToolParamHelper.ExtractFloat(request.parameters, "bounciness", 0.8f);

            var mat = new PhysicsMaterial(name)
            {
                dynamicFriction = dynamicFriction,
                staticFriction = staticFriction,
                bounciness = bounciness,
                bounceCombine = PhysicsMaterialCombine.Maximum,
                frictionCombine = PhysicsMaterialCombine.Average
            };

            if (!AssetDatabase.IsValidFolder("Assets/Materials"))
            {
                AssetDatabase.CreateFolder("Assets", "Materials");
            }

            string path = $"Assets/Materials/{name}.physicMaterial";
            AssetDatabase.CreateAsset(mat, path);
            AssetDatabase.SaveAssets();

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Created PhysicMaterial at '{path}' (bounciness={bounciness})",
                target = path
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class ConfigureJointTool : IBridgeTool
    {
        public string ToolName => "configure_joint";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string connectedBodyName = ToolParamHelper.ExtractString(request.parameters, "connectedBody");
            string jointType = ToolParamHelper.ExtractString(request.parameters, "jointType", "Fixed").ToLowerInvariant();

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"GameObject '{target}' not found");

            Joint joint = go.GetComponent<Joint>();
            if (joint == null)
            {
                switch (jointType)
                {
                    case "hinge": joint = go.AddComponent<HingeJoint>(); break;
                    case "spring": joint = go.AddComponent<SpringJoint>(); break;
                    default: joint = go.AddComponent<FixedJoint>(); break;
                }
            }

            if (!string.IsNullOrEmpty(connectedBodyName))
            {
                GameObject connGo = ToolParamHelper.FindTarget(connectedBodyName);
                if (connGo != null)
                {
                    joint.connectedBody = connGo.GetComponent<Rigidbody>();
                }
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Configured {joint.GetType().Name} on '{go.name}'",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class SetGravityTool : IBridgeTool
    {
        public string ToolName => "set_gravity";

        public BridgeMessage Execute(BridgeMessage request)
        {
            float gx = ToolParamHelper.ExtractFloat(request.parameters, "x", 0f);
            float gy = ToolParamHelper.ExtractFloat(request.parameters, "y", -9.81f);
            float gz = ToolParamHelper.ExtractFloat(request.parameters, "z", 0f);

            Physics.gravity = new Vector3(gx, gy, gz);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Set Physics.gravity to ({gx}, {gy}, {gz})",
                target = "Physics.gravity"
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
