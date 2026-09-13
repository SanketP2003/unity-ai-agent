using AutonomousUnityAgent.Models;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    /// <summary>
    /// Phase 4 acceptance test tool.
    /// Creates a test cube named "AIAgent_TestCube" at position (0,0,0).
    /// Idempotent: destroys any existing AIAgent_TestCube before creating a new one.
    /// </summary>
    public class CreateTestCubeTool : IBridgeTool
    {
        public string ToolName => "create_test_cube";

        private const string TestCubeName = "AIAgent_TestCube";

        public BridgeMessage Execute(BridgeMessage request)
        {
            // Idempotent: remove existing test cube if present
            GameObject existing = GameObject.Find(TestCubeName);
            if (existing != null)
            {
                Debug.Log($"[CreateTestCubeTool] Destroying existing {TestCubeName}");
                Object.DestroyImmediate(existing);
            }

            // Create the test cube
            GameObject cube = GameObject.CreatePrimitive(PrimitiveType.Cube);
            cube.name = TestCubeName;
            cube.transform.position = Vector3.zero;

            Debug.Log($"[CreateTestCubeTool] Created {TestCubeName} at (0,0,0)");

            // Build response data
            string responseData = JsonHelper.ToJson(new ToolResponseData
            {
                @object = TestCubeName,
                position = new PositionData { x = 0, y = 0, z = 0 }
            });

            return BridgeMessage.ToolResponse(
                request.operationId,
                ToolName,
                success: true,
                data: responseData
            );
        }
    }
}
