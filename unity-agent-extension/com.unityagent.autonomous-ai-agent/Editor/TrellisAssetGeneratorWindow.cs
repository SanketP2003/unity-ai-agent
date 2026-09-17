using System;
using System.IO;
using System.Threading.Tasks;
using AutonomousUnityAgent.Editor.Tools;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor
{
    /// <summary>
    /// Interactive visual EditorWindow for generating high-detail 3D models with Microsoft TRELLIS / TRELLIS 2
    /// directly from within Unity.
    ///
    /// Access via: Window → Autonomous AI → Trellis 3D Generator
    /// </summary>
    public class TrellisAssetGeneratorWindow : EditorWindow
    {
        private string _prompt = "A high-detail futuristic battle drone with glowing engines";
        private Texture2D _referenceImage;
        private string _assetName = "BattleDrone";
        private Vector3 _position = Vector3.zero;
        private Vector3 _rotation = Vector3.zero;
        private Vector3 _scale = Vector3.one;
        private bool _addCollider = true;

        private bool _isGenerating = false;
        private string _statusMessage = "";
        private bool _isSuccess = true;
        private Vector2 _scrollPos;

        [MenuItem("Window/Autonomous AI/Trellis 3D Generator", false, 105)]
        public static void ShowWindow()
        {
            var window = GetWindow<TrellisAssetGeneratorWindow>("Trellis 3D");
            window.minSize = new Vector2(360, 480);
            window.Show();
        }

        private void OnGUI()
        {
            _scrollPos = EditorGUILayout.BeginScrollView(_scrollPos);

            EditorGUILayout.Space(10);
            EditorGUILayout.LabelField("TRELLIS 2 — 3D Asset Generator", EditorStyles.boldLabel);
            EditorGUILayout.HelpBox("Synthesize detailed textured 3D models using Microsoft TRELLIS 2 via Text-to-3D or Image-to-3D.", MessageType.Info);

            EditorGUILayout.Space(8);

            // --- Inputs Section ---
            EditorGUILayout.LabelField("Generation Inputs", EditorStyles.boldLabel);
            EditorGUILayout.BeginVertical(EditorStyles.helpBox);

            EditorGUILayout.LabelField("Text Prompt:", EditorStyles.miniBoldLabel);
            _prompt = EditorGUILayout.TextArea(_prompt, GUILayout.Height(50));

            EditorGUILayout.Space(5);
            EditorGUILayout.LabelField("Reference Image (Optional Image-to-3D):", EditorStyles.miniBoldLabel);
            _referenceImage = (Texture2D)EditorGUILayout.ObjectField(_referenceImage, typeof(Texture2D), false, GUILayout.Height(70));

            EditorGUILayout.Space(5);
            _assetName = EditorGUILayout.TextField("Asset / Object Name:", _assetName);

            EditorGUILayout.EndVertical();

            EditorGUILayout.Space(8);

            // --- Placement Section ---
            EditorGUILayout.LabelField("Scene Placement", EditorStyles.boldLabel);
            EditorGUILayout.BeginVertical(EditorStyles.helpBox);

            _position = EditorGUILayout.Vector3Field("Position:", _position);
            _rotation = EditorGUILayout.Vector3Field("Rotation:", _rotation);
            _scale = EditorGUILayout.Vector3Field("Scale:", _scale);
            _addCollider = EditorGUILayout.Toggle("Add Collider:", _addCollider);

            EditorGUILayout.EndVertical();

            EditorGUILayout.Space(12);

            // --- Action Button ---
            GUI.enabled = !_isGenerating && (!string.IsNullOrWhiteSpace(_prompt) || _referenceImage != null);
            if (GUILayout.Button(_isGenerating ? "Generating 3D Model (Please wait)..." : "Generate 3D Model with TRELLIS", GUILayout.Height(36)))
            {
                GenerateModel();
            }
            GUI.enabled = true;

            if (!string.IsNullOrEmpty(_statusMessage))
            {
                EditorGUILayout.Space(8);
                EditorGUILayout.HelpBox(_statusMessage, _isSuccess ? MessageType.Info : MessageType.Error);
            }

            EditorGUILayout.Space(15);
            EditorGUILayout.LabelField("Pipeline Info", EditorStyles.boldLabel);
            EditorGUILayout.BeginVertical(EditorStyles.helpBox);
            EditorGUILayout.LabelField("Target Format:", "GLB / OBJ (Auto-Converted)");
            EditorGUILayout.LabelField("Destination:", "Assets/Models/Generated/");
            EditorGUILayout.LabelField("Auto-Import:", "Enabled (Prefab & Scene Instance)");
            EditorGUILayout.EndVertical();

            EditorGUILayout.EndScrollView();
        }

        private async void GenerateModel()
        {
            _isGenerating = true;
            _statusMessage = "Generating 3D mesh with TRELLIS 2 diffusion...";
            _isSuccess = true;
            Repaint();

            string imagePath = "";
            if (_referenceImage != null)
            {
                imagePath = AssetDatabase.GetAssetPath(_referenceImage);
                if (!string.IsNullOrEmpty(imagePath))
                {
                    imagePath = Path.Combine(Directory.GetCurrentDirectory(), imagePath).Replace("\\", "/");
                }
            }

            string cleanName = string.IsNullOrWhiteSpace(_assetName) ? "TrellisModel" : _assetName.Trim();

            // Construct BridgeMessage tool request
            var request = new BridgeMessage
            {
                operationId = Guid.NewGuid().ToString(),
                type = AutonomousUnityAgent.Models.MessageType.TOOL_REQUEST,
                tool = "generate_trellis_mesh",
                parameters = "{" +
                    $"\"prompt\":\"{EscapeJson(_prompt)}\"," +
                    $"\"image_path\":\"{EscapeJson(imagePath)}\"," +
                    $"\"asset_name\":\"{EscapeJson(cleanName)}\"," +
                    $"\"position\":{{\"x\":{_position.x},\"y\":{_position.y},\"z\":{_position.z}}}," +
                    $"\"rotation\":{{\"x\":{_rotation.x},\"y\":{_rotation.y},\"z\":{_rotation.z}}}," +
                    $"\"scale\":{{\"x\":{_scale.x},\"y\":{_scale.y},\"z\":{_scale.z}}}," +
                    $"\"add_collider\":{_addCollider.ToString().ToLower()}" +
                    "}"
            };

            var tool = new GenerateTrellisMeshTool();
            BridgeMessage response = null;

            await Task.Run(() =>
            {
                response = tool.Execute(request);
            });

            _isGenerating = false;

            if (response != null && response.success)
            {
                _isSuccess = true;
                _statusMessage = $"Successfully generated and placed '{cleanName}' into active scene!";
            }
            else
            {
                _isSuccess = false;
                string err = response?.errors != null && response.errors.Count > 0 ? response.errors[0].message : "Unknown error";
                _statusMessage = $"Generation failed: {err}";
            }

            Repaint();
        }

        private string EscapeJson(string s)
        {
            if (string.IsNullOrEmpty(s)) return "";
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\n", "\\n").Replace("\r", "\\r");
        }
    }
}
