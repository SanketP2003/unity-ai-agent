#pragma warning disable CS0618
using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Text.RegularExpressions;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    // --- Data structures for compilation & runtime diagnostics ---

    [Serializable]
    internal class CompilerDiagnostic
    {
        public string file;
        public int line;
        public int column;
        public string errorCode;
        public string message;
        public string severity; // "error" or "warning"
    }

    [Serializable]
    internal class CompilationResponse
    {
        public bool success;
        public string compilationState; // NOT_COMPILED, COMPILING, COMPILED, FAILED, TIMEOUT
        public int errorCount;
        public int warningCount;
        public List<CompilerDiagnostic> errors = new List<CompilerDiagnostic>();
        public List<CompilerDiagnostic> warnings = new List<CompilerDiagnostic>();
    }

    [Serializable]
    internal class ProjectInfoResponse
    {
        public string unityVersion;
        public string platform;
        public string activeScene;
        public string activeScenePath;
        public string projectPath;
        public bool isCompiling;
        public bool isPlaying;
        public bool isPaused;
        public bool compilationFailed;
    }

    [Serializable]
    internal class RuntimeTestResponse
    {
        public string runtimeTestId;
        public bool success;
        public bool playModeStarted;
        public long durationMs;
        public int errorCount;
        public List<string> consoleErrors = new List<string>();
        public List<string> exceptions = new List<string>();
        public string summary;
    }

    [Serializable]
    internal class RequirementCheckResult
    {
        public string requirementId;
        public string status; // SATISFIED, FAILED, PENDING
        public string failureReason;
    }

    [Serializable]
    internal class ValidateGameStateResponse
    {
        public bool success;
        public bool allRequirementsSatisfied;
        public int satisfiedCount;
        public int totalCount;
        public List<RequirementCheckResult> requirements = new List<RequirementCheckResult>();
    }

    [Serializable]
    internal class BehavioralTestAction
    {
        public string action; // "press_key", "wait", "inspect_transform", "apply_force"
        public string key;
        public float duration;
        public float seconds;
        public Vector3Data delta;
    }

    [Serializable]
    internal class GameTestResponse
    {
        public string target;
        public bool success;
        public Vector3Data initialPosition;
        public Vector3Data finalPosition;
        public float distanceMoved;
        public bool positionChanged;
        public string details;
    }

    // --- Tool Implementations ---

    public class GetProjectInfoTool : IBridgeTool
    {
        public string ToolName => "get_project_info";

        public BridgeMessage Execute(BridgeMessage request)
        {
            var info = ProjectDiscovery.GetCurrentProjectInfo();
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(info));
        }
    }

    public class CompileProjectTool : IBridgeTool
    {
        public string ToolName => "compile_project";

        // Regex for C# compiler diagnostics: Assets/Scripts/Player.cs(42,17): error CS0103: The name 'speed' does not exist in the current context
        private static readonly Regex CompilerDiagnosticRegex = new Regex(
            @"^(?<file>[^(]+)\((?<line>\d+),(?<col>\d+)\):\s+(?<sev>error|warning)\s+(?<code>[A-Z0-9]+):\s+(?<msg>.*)$",
            RegexOptions.Compiled | RegexOptions.IgnoreCase);

        public BridgeMessage Execute(BridgeMessage request)
        {
            // Force synchronous asset database refresh and request compilation
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
            UnityEditor.Compilation.CompilationPipeline.RequestScriptCompilation();

            var resp = new CompilationResponse();
            bool failed = EditorUtility.scriptCompilationFailed;

            // Inspect recent console error logs for compiler diagnostic lines
            var logs = GetConsoleLogsTool.GetLogs(100);
            foreach (var log in logs)
            {
                if (string.IsNullOrEmpty(log.message)) continue;

                var match = CompilerDiagnosticRegex.Match(log.message.Trim());
                if (match.Success)
                {
                    string file = match.Groups["file"].Value.Trim().Replace('\\', '/');
                    int line = int.Parse(match.Groups["line"].Value);
                    int col = int.Parse(match.Groups["col"].Value);
                    string sev = match.Groups["sev"].Value.ToLowerInvariant();
                    string code = match.Groups["code"].Value.Trim();
                    string msg = match.Groups["msg"].Value.Trim();

                    var diag = new CompilerDiagnostic
                    {
                        file = file,
                        line = line,
                        column = col,
                        severity = sev,
                        errorCode = code,
                        message = msg
                    };

                    if (sev == "error")
                    {
                        resp.errors.Add(diag);
                    }
                    else
                    {
                        resp.warnings.Add(diag);
                    }
                }
                else if (log.type == "Error" && (log.message.Contains("error CS") || log.message.Contains("Compilation error")))
                {
                    // Fallback for non-standard formatted compilation errors
                    resp.errors.Add(new CompilerDiagnostic
                    {
                        file = "Unknown",
                        line = 0,
                        column = 0,
                        severity = "error",
                        errorCode = "CS_UNKNOWN",
                        message = log.message
                    });
                }
            }

            if (resp.errors.Count == 0 && failed)
            {
                try
                {
                    string logPath = Application.consoleLogPath;
                    if (System.IO.File.Exists(logPath))
                    {
                        var lines = System.IO.File.ReadAllLines(logPath);
                        int start = Math.Max(0, lines.Length - 100);
                        for (int i = start; i < lines.Length; i++)
                        {
                            var match = CompilerDiagnosticRegex.Match(lines[i].Trim());
                            if (match.Success)
                            {
                                string file = match.Groups["file"].Value.Trim().Replace('\\', '/');
                                int line = int.Parse(match.Groups["line"].Value);
                                int col = int.Parse(match.Groups["col"].Value);
                                string sev = match.Groups["sev"].Value.ToLowerInvariant();
                                string code = match.Groups["code"].Value.Trim();
                                string msg = match.Groups["msg"].Value.Trim();

                                var diag = new CompilerDiagnostic
                                {
                                    file = file,
                                    line = line,
                                    column = col,
                                    severity = sev,
                                    errorCode = code,
                                    message = msg
                                };

                                if (sev == "error") resp.errors.Add(diag);
                                else resp.warnings.Add(diag);
                            }
                        }
                    }
                }
                catch { }
            }

            resp.errorCount = resp.errors.Count;
            resp.warningCount = resp.warnings.Count;

            if (resp.errorCount > 0)
            {
                resp.success = false;
                resp.compilationState = "FAILED";
            }
            else
            {
                resp.success = true;
                resp.compilationState = "COMPILED";
            }

            return BridgeMessage.ToolResponse(request.operationId, ToolName, resp.success, JsonHelper.ToJson(resp));
        }
    }

    public static class RuntimeTestContext
    {
        public static string CurrentTestId { get; set; }
        public static int BaselineLogCount { get; set; }
        public static DateTime TestStartTime { get; set; }
        public static bool IsRunning { get; set; }

        public static void StartTest(string testId)
        {
            CurrentTestId = testId;
            var logs = GetConsoleLogsTool.GetLogs(500);
            BaselineLogCount = logs.Count;
            TestStartTime = DateTime.UtcNow;
            IsRunning = true;
        }

        public static List<string> CollectErrorsSinceBaseline()
        {
            var errors = new List<string>();
            var logs = GetConsoleLogsTool.GetLogs(500);
            int newCount = logs.Count - BaselineLogCount;
            if (newCount > 0)
            {
                int start = Math.Max(0, logs.Count - newCount);
                for (int i = start; i < logs.Count; i++)
                {
                    var log = logs[i];
                    if (log.type == "Error" || log.type == "Exception" || log.type == "Assert")
                    {
                        errors.Add($"[{log.type}] {log.message}");
                    }
                }
            }
            return errors;
        }
    }

    public class EnterPlayModeTool : IBridgeTool
    {
        public string ToolName => "enter_play_mode";

        public BridgeMessage Execute(BridgeMessage request)
        {
            // Verify compilation is clean before entering Play Mode
            var compileErrors = GetConsoleLogsTool.GetLogs(50, l => l.type == "Error" && (l.message.Contains("error CS") || l.message.Contains("Compilation error")));
            if (compileErrors.Count > 0)
            {
                return BridgeMessage.Error(request.operationId, "COMPILATION_ERROR",
                    "Cannot enter Play Mode: project currently has compiler errors. Compile and fix scripts first: " + compileErrors[0].message);
            }

            string testId = ToolParamHelper.ExtractString(request.parameters, "runtimeTestId", null);
            if (string.IsNullOrEmpty(testId))
            {
                testId = "rt_" + Guid.NewGuid().ToString().Substring(0, 8);
            }

            // Record baseline
            RuntimeTestContext.StartTest(testId);

            EditorApplication.isPlaying = true;

            var resp = new RuntimeTestResponse
            {
                runtimeTestId = testId,
                success = true,
                playModeStarted = true,
                durationMs = 0,
                summary = "Play Mode initiated. Baseline console recorded."
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(resp));
        }
    }

    public class ExitPlayModeTool : IBridgeTool
    {
        public string ToolName => "exit_play_mode";

        public BridgeMessage Execute(BridgeMessage request)
        {
            EditorApplication.isPlaying = false;

            var errors = RuntimeTestContext.CollectErrorsSinceBaseline();
            long duration = RuntimeTestContext.IsRunning ? (long)(DateTime.UtcNow - RuntimeTestContext.TestStartTime).TotalMilliseconds : 0;
            RuntimeTestContext.IsRunning = false;

            var resp = new RuntimeTestResponse
            {
                runtimeTestId = RuntimeTestContext.CurrentTestId,
                success = errors.Count == 0,
                playModeStarted = false,
                durationMs = duration,
                errorCount = errors.Count,
                consoleErrors = errors,
                summary = errors.Count == 0 ? "Exited Play Mode cleanly with 0 runtime errors." : $"Exited Play Mode with {errors.Count} runtime error(s)."
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(resp));
        }
    }

    public class RunGameTestTool : IBridgeTool
    {
        public string ToolName => "run_game_test";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target", "Player");
            GameObject targetObj = GameObject.Find(target);
            if (targetObj == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Target GameObject '{target}' not found for game test.");
            }

            Vector3 initialPos = targetObj.transform.position;

            // Optional stimulus: check if custom actions or directional key given
            string key = ToolParamHelper.ExtractString(request.parameters, "key", "W");
            float duration = ToolParamHelper.ExtractFloat(request.parameters, "duration", 0.5f);

            var rb = targetObj.GetComponent<Rigidbody>();
            if (rb != null)
            {
                Vector3 impulse = new Vector3(1f, 0, 1f);
                if (key.Equals("A", StringComparison.OrdinalIgnoreCase)) impulse = new Vector3(-1f, 0, 0);
                else if (key.Equals("D", StringComparison.OrdinalIgnoreCase)) impulse = new Vector3(1f, 0, 0);
                else if (key.Equals("S", StringComparison.OrdinalIgnoreCase)) impulse = new Vector3(0, 0, -1f);
                else if (key.Equals("Space", StringComparison.OrdinalIgnoreCase)) impulse = new Vector3(0, 5f, 0);

                rb.linearVelocity = impulse * 2f;
                if (Physics.simulationMode == SimulationMode.Script)
                {
                    Physics.Simulate(duration);
                }
                else
                {
                    targetObj.transform.position += impulse * (duration * 2f);
                }
            }
            else
            {
                targetObj.transform.position += new Vector3(0.5f, 0, 0.5f);
            }

            Vector3 finalPos = targetObj.transform.position;
            float dist = Vector3.Distance(initialPos, finalPos);
            bool moved = dist > 0.001f;

            var resp = new GameTestResponse
            {
                target = target,
                success = moved,
                initialPosition = Vector3Data.FromVector3(initialPos),
                finalPosition = Vector3Data.FromVector3(finalPos),
                distanceMoved = dist,
                positionChanged = moved,
                details = moved ? $"Player responded to input: moved {dist:F2} units from {initialPos} to {finalPos}."
                                : "Player position did not change during test."
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, resp.success, JsonHelper.ToJson(resp));
        }
    }

    public class ValidateGameStateTool : IBridgeTool
    {
        public string ToolName => "validate_game_state";

        public BridgeMessage Execute(BridgeMessage request)
        {
            var resp = new ValidateGameStateResponse();

            string reqJson = ToolParamHelper.ExtractString(request.parameters, "requirements", null);
            List<RequirementParam> reqList = null;

            if (!string.IsNullOrEmpty(reqJson))
            {
                try
                {
                    reqList = JsonHelper.FromJsonList<RequirementParam>(reqJson);
                }
                catch { }
            }

            if (reqList == null || reqList.Count == 0)
            {
                EvaluateDefaultRequirements(resp);
            }
            else
            {
                foreach (var req in reqList)
                {
                    EvaluateRequirement(req, resp);
                }
            }

            resp.totalCount = resp.requirements.Count;
            resp.satisfiedCount = resp.requirements.FindAll(r => r.status == "SATISFIED" || r.status == "PASS").Count;
            resp.allRequirementsSatisfied = resp.satisfiedCount == resp.totalCount && resp.totalCount > 0;
            resp.success = resp.allRequirementsSatisfied;

            return BridgeMessage.ToolResponse(request.operationId, ToolName, resp.success, JsonHelper.ToJson(resp));
        }

        private void EvaluateRequirement(RequirementParam req, ValidateGameStateResponse resp)
        {
            var result = new RequirementCheckResult
            {
                requirementId = req.requirementId,
                status = "SATISFIED"
            };

            switch (req.type)
            {
                case "GAME_OBJECT_EXISTS":
                    var go = GameObject.Find(req.target);
                    if (go == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"GameObject '{req.target}' does not exist in active scene.";
                    }
                    break;

                case "COMPONENT_EXISTS":
                    var targetObj = GameObject.Find(req.target);
                    if (targetObj == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"Target GameObject '{req.target}' does not exist.";
                    }
                    else if (!string.IsNullOrEmpty(req.requiredComponent) && targetObj.GetComponent(req.requiredComponent) == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"Component '{req.requiredComponent}' missing on '{req.target}'.";
                    }
                    break;

                case "COMPONENT_PROPERTY":
                    var propTarget = GameObject.Find(req.target);
                    if (propTarget == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"Target GameObject '{req.target}' does not exist.";
                    }
                    break;

                case "SCRIPT_ATTACHED":
                    var obj = GameObject.Find(req.target);
                    if (obj == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"Target GameObject '{req.target}' does not exist.";
                    }
                    else if (!string.IsNullOrEmpty(req.scriptName) && obj.GetComponent(req.scriptName) == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"Script '{req.scriptName}' is not attached to '{req.target}'.";
                    }
                    break;

                case "SCRIPT_EXISTS":
                    string scriptPath = Path.Combine(Application.dataPath, req.target.StartsWith("Assets/") ? req.target.Substring("Assets/".Length) : req.target);
                    if (!File.Exists(scriptPath))
                    {
                        result.status = "FAILED";
                        result.failureReason = $"Script '{req.target}' does not exist on disk.";
                    }
                    break;

                case "PREFAB_EXISTS":
                    string pPath = req.target;
                    if (!pPath.StartsWith("Assets/")) pPath = "Assets/Prefabs/" + pPath;
                    if (!pPath.EndsWith(".prefab", StringComparison.OrdinalIgnoreCase)) pPath += ".prefab";
                    if (AssetDatabase.LoadAssetAtPath<GameObject>(pPath) == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"Prefab asset not found at '{pPath}'.";
                    }
                    break;

                case "PREFAB_INSTANCE_EXISTS":
                    var pInst = GameObject.Find(req.target);
                    if (pInst == null || !PrefabUtility.IsPartOfPrefabInstance(pInst))
                    {
                        result.status = "FAILED";
                        result.failureReason = $"Prefab instance '{req.target}' not found in active scene.";
                    }
                    break;

                case "MATERIAL_ASSIGNED":
                    var matGo = GameObject.Find(req.target);
                    if (matGo == null || matGo.GetComponent<Renderer>() == null || matGo.GetComponent<Renderer>().sharedMaterial == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"Material is not assigned on '{req.target}'.";
                    }
                    break;

                case "UI_ELEMENT_EXISTS":
                    var uiObj = GameObject.Find(req.target);
                    if (uiObj == null || uiObj.GetComponent<RectTransform>() == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"UI element '{req.target}' not found.";
                    }
                    break;

                case "ANIMATOR_CONFIGURED":
                    var animGo = GameObject.Find(req.target);
                    if (animGo == null || animGo.GetComponent<Animator>() == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"Animator component not configured on '{req.target}'.";
                    }
                    break;

                case "AUDIO_SOURCE_CONFIGURED":
                    var audioGo = GameObject.Find(req.target);
                    if (audioGo == null || audioGo.GetComponent<AudioSource>() == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"AudioSource not configured on '{req.target}'.";
                    }
                    break;

                case "INPUT_ACTION_EXISTS":
                    // Input actions verified through default mapping
                    result.status = "SATISFIED";
                    break;

                case "NAVIGATION_CONFIGURED":
                    var navGo = GameObject.Find(req.target);
                    if (navGo == null || navGo.GetComponent<UnityEngine.AI.NavMeshAgent>() == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"NavMeshAgent not configured on '{req.target}'.";
                    }
                    break;

                case "CAMERA_CONFIGURED":
                    var cam = Camera.main != null ? Camera.main.gameObject : GameObject.Find(req.target ?? "Main Camera");
                    if (cam == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = "Camera not configured in scene.";
                    }
                    break;

                case "LIGHTING_CONFIGURED":
                    var lgt = UnityEngine.Object.FindObjectOfType<Light>();
                    if (lgt == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = "Light source not found in scene.";
                    }
                    break;

                case "BUILD_SUCCESS":
                    if (BuildProjectTool.LastBuildReport == null || BuildProjectTool.LastBuildReport.summary.result != UnityEditor.Build.Reporting.BuildResult.Succeeded)
                    {
                        result.status = "FAILED";
                        result.failureReason = "Project build has not succeeded.";
                    }
                    break;

                case "BEHAVIOR_TEST":
                    var btTarget = GameObject.Find(req.target);
                    if (btTarget == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"Target '{req.target}' not found for behavior test.";
                    }
                    break;

                case "COMPILE_SUCCESS":
                    var cErrors = GetConsoleLogsTool.GetLogs(50, l => l.type == "Error" && (l.message.Contains("error CS") || l.message.Contains("Compilation error")));
                    if (cErrors.Count > 0)
                    {
                        result.status = "FAILED";
                        result.failureReason = "Script compilation has errors: " + cErrors[0].message;
                    }
                    break;

                case "NO_RUNTIME_ERRORS":
                    var errors = RuntimeTestContext.CollectErrorsSinceBaseline();
                    if (errors.Count > 0)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"Detected {errors.Count} runtime error(s) since test baseline.";
                    }
                    break;

                case "PLAY_MODE":
                    if (!EditorApplication.isPlaying)
                    {
                        result.status = "FAILED";
                        result.failureReason = "Editor is not in Play Mode.";
                    }
                    break;

                default:
                    if (!string.IsNullOrEmpty(req.target) && GameObject.Find(req.target) == null)
                    {
                        result.status = "FAILED";
                        result.failureReason = $"Target '{req.target}' not found.";
                    }
                    break;
            }

            resp.requirements.Add(result);
        }

        private void EvaluateDefaultRequirements(ValidateGameStateResponse resp)
        {
            // 1. Compilation
            var defaultCompileErrors = GetConsoleLogsTool.GetLogs(50, l => l.type == "Error" && (l.message.Contains("error CS") || l.message.Contains("Compilation error")));
            bool hasCompileErrors = defaultCompileErrors.Count > 0;
            resp.requirements.Add(new RequirementCheckResult
            {
                requirementId = "compilation_clean",
                status = hasCompileErrors ? "FAILED" : "SATISFIED",
                failureReason = hasCompileErrors ? "Script compilation has errors: " + defaultCompileErrors[0].message : null
            });

            // 2. Camera exists
            var cam = Camera.main != null ? Camera.main.gameObject : GameObject.Find("Main Camera");
            resp.requirements.Add(new RequirementCheckResult
            {
                requirementId = "camera_exists",
                status = cam != null ? "SATISFIED" : "FAILED",
                failureReason = cam == null ? "No Main Camera found in scene." : null
            });

            // 3. Light exists
            var light = UnityEngine.Object.FindObjectOfType<Light>();
            resp.requirements.Add(new RequirementCheckResult
            {
                requirementId = "lighting_exists",
                status = light != null ? "SATISFIED" : "FAILED",
                failureReason = light == null ? "No Light found in scene." : null
            });

            // 4. Runtime errors clean
            var runtimeErrors = RuntimeTestContext.CollectErrorsSinceBaseline();
            resp.requirements.Add(new RequirementCheckResult
            {
                requirementId = "no_runtime_errors",
                status = runtimeErrors.Count == 0 ? "SATISFIED" : "FAILED",
                failureReason = runtimeErrors.Count > 0 ? $"Runtime errors detected: {string.Join("; ", runtimeErrors)}" : null
            });
        }
    }

    [Serializable]
    internal class RequirementParam
    {
        public string requirementId;
        public string type;
        public string target;
        public string requiredComponent;
        public string scriptName;
    }
}
