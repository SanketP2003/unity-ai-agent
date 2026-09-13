using System;
using System.Reflection;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    public class AddComponentTool : IBridgeTool
    {
        public string ToolName => "add_component";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string compType = ToolParamHelper.ExtractString(request.parameters, "componentType");

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{target}'");
            }

            Type type = ResolveComponentType(compType);
            if (type == null)
            {
                return BridgeMessage.Error(request.operationId, "TYPE_NOT_FOUND", $"Could not resolve Component type '{compType}'");
            }

            if (!typeof(Component).IsAssignableFrom(type))
            {
                return BridgeMessage.Error(request.operationId, "NOT_A_COMPONENT", $"Type '{compType}' does not inherit from Component");
            }

            Component comp = Undo.AddComponent(go, type);
            if (comp == null)
            {
                return BridgeMessage.Error(request.operationId, "ADD_FAILED", $"Failed to add component '{compType}' to '{target}'");
            }

            var responseData = new ComponentResponseData
            {
                target = go.name,
                componentType = comp.GetType().Name,
                action = "added",
                details = $"Component '{comp.GetType().Name}' added to '{go.name}'"
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }

        public static Type ResolveComponentType(string typeName)
        {
            if (string.IsNullOrEmpty(typeName)) return null;

            if (typeName.EndsWith(".cs", StringComparison.OrdinalIgnoreCase))
            {
                typeName = typeName.Substring(0, typeName.Length - 3);
            }

            // Direct Type.GetType
            Type type = Type.GetType(typeName);
            if (type != null && typeof(Component).IsAssignableFrom(type)) return type;

            // Try UnityEngine namespace
            type = Type.GetType($"UnityEngine.{typeName}, UnityEngine.CoreModule") ??
                   Type.GetType($"UnityEngine.{typeName}, UnityEngine.PhysicsModule") ??
                   Type.GetType($"UnityEngine.{typeName}, UnityEngine.AudioModule") ??
                   Type.GetType($"UnityEngine.{typeName}, UnityEngine.AnimationModule") ??
                   Type.GetType($"UnityEngine.{typeName}, UnityEngine.UIModule") ??
                   Type.GetType($"UnityEngine.{typeName}, UnityEngine");
            if (type != null && typeof(Component).IsAssignableFrom(type)) return type;

            // Try Assembly-CSharp directly
            try
            {
                var asmCSharp = System.Reflection.Assembly.Load("Assembly-CSharp");
                if (asmCSharp != null)
                {
                    var t = asmCSharp.GetType(typeName, false, true);
                    if (t != null && typeof(Component).IsAssignableFrom(t)) return t;

                    Type[] types = null;
                    try { types = asmCSharp.GetTypes(); }
                    catch (System.Reflection.ReflectionTypeLoadException ex) { types = ex.Types; }
                    catch { }

                    if (types != null)
                    {
                        foreach (var cand in types)
                        {
                            if (cand != null && cand.Name.Equals(typeName, StringComparison.OrdinalIgnoreCase) &&
                                typeof(Component).IsAssignableFrom(cand))
                            {
                                return cand;
                            }
                        }
                    }
                }
            }
            catch { }

            // Scan all loaded assemblies safely
            foreach (var assembly in AppDomain.CurrentDomain.GetAssemblies())
            {
                Type[] types = null;
                try
                {
                    types = assembly.GetTypes();
                }
                catch (System.Reflection.ReflectionTypeLoadException ex)
                {
                    types = ex.Types;
                }
                catch
                {
                    continue;
                }

                if (types == null) continue;
                foreach (var t in types)
                {
                    if (t != null && t.Name.Equals(typeName, StringComparison.OrdinalIgnoreCase) &&
                        typeof(Component).IsAssignableFrom(t))
                    {
                        return t;
                    }
                }
            }

            return null;
        }
    }

    public class SetComponentPropertyTool : IBridgeTool
    {
        public string ToolName => "set_component_property";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string compType = ToolParamHelper.ExtractString(request.parameters, "componentType");
            string propertyName = ToolParamHelper.ExtractString(request.parameters, "property");

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{target}'");
            }

            Type type = AddComponentTool.ResolveComponentType(compType);
            if (type == null)
            {
                return BridgeMessage.Error(request.operationId, "TYPE_NOT_FOUND", $"Could not resolve Component type '{compType}'");
            }

            Component comp = go.GetComponent(type);
            if (comp == null)
            {
                return BridgeMessage.Error(request.operationId, "COMPONENT_NOT_FOUND", $"GameObject '{target}' does not have a '{compType}' component");
            }

            // 1. Try SerializedObject
            var serializedObject = new SerializedObject(comp);
            SerializedProperty prop = serializedObject.FindProperty(propertyName);

            if (prop != null)
            {
                Undo.RecordObject(comp, "Set property " + propertyName);
                SetSerializedPropertyValue(prop, request.parameters);
                serializedObject.ApplyModifiedProperties();

                var responseData = new ComponentResponseData
                {
                    target = go.name,
                    componentType = comp.GetType().Name,
                    action = "property_set",
                    details = $"Set '{propertyName}' on '{compType}' via SerializedProperty"
                };
                return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
            }

            // 2. Fallback to reflection
            PropertyInfo propInfo = type.GetProperty(propertyName, BindingFlags.Public | BindingFlags.Instance | BindingFlags.IgnoreCase);
            if (propInfo != null && propInfo.CanWrite)
            {
                Undo.RecordObject(comp, "Set property " + propertyName);
                object val = ConvertValue(propInfo.PropertyType, request.parameters);
                propInfo.SetValue(comp, val, null);

                var responseData = new ComponentResponseData
                {
                    target = go.name,
                    componentType = comp.GetType().Name,
                    action = "property_set",
                    details = $"Set '{propertyName}' on '{compType}' via PropertyInfo reflection"
                };
                return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
            }

            FieldInfo fieldInfo = type.GetField(propertyName, BindingFlags.Public | BindingFlags.Instance | BindingFlags.IgnoreCase);
            if (fieldInfo != null)
            {
                Undo.RecordObject(comp, "Set field " + propertyName);
                object val = ConvertValue(fieldInfo.FieldType, request.parameters);
                fieldInfo.SetValue(comp, val);

                var responseData = new ComponentResponseData
                {
                    target = go.name,
                    componentType = comp.GetType().Name,
                    action = "property_set",
                    details = $"Set '{propertyName}' on '{compType}' via FieldInfo reflection"
                };
                return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
            }

            return BridgeMessage.Error(request.operationId, "PROPERTY_NOT_FOUND",
                $"Property or field '{propertyName}' not found on component '{compType}'");
        }

        private void SetSerializedPropertyValue(SerializedProperty prop, string json)
        {
            switch (prop.propertyType)
            {
                case SerializedPropertyType.Boolean:
                    prop.boolValue = ToolParamHelper.ExtractBool(json, "value", prop.boolValue);
                    break;
                case SerializedPropertyType.Float:
                    prop.floatValue = ToolParamHelper.ExtractFloat(json, "value", prop.floatValue);
                    break;
                case SerializedPropertyType.Integer:
                    prop.intValue = (int)ToolParamHelper.ExtractFloat(json, "value", prop.intValue);
                    break;
                case SerializedPropertyType.String:
                    prop.stringValue = ToolParamHelper.ExtractString(json, "value", prop.stringValue);
                    break;
                case SerializedPropertyType.Color:
                    string hex = ToolParamHelper.ExtractString(json, "value", null);
                    if (!string.IsNullOrEmpty(hex) && ColorUtility.TryParseHtmlString(hex, out Color col))
                        prop.colorValue = col;
                    break;
                case SerializedPropertyType.Vector2:
                    if (json.Contains("\"value\":{"))
                    {
                        var vec2 = ToolParamHelper.Parse<Vector3Data>(ExtractObject(json, "value"));
                        prop.vector2Value = new Vector2(vec2.x, vec2.y);
                    }
                    break;
                case SerializedPropertyType.Vector3:
                    if (json.Contains("\"value\":{"))
                    {
                        var vec = ToolParamHelper.Parse<Vector3Data>(ExtractObject(json, "value"));
                        prop.vector3Value = vec.ToVector3();
                    }
                    break;
                case SerializedPropertyType.ObjectReference:
                    string objRefName = ToolParamHelper.ExtractString(json, "value", null);
                    if (!string.IsNullOrEmpty(objRefName))
                    {
                        GameObject refGo = ToolParamHelper.FindTarget(objRefName);
                        if (refGo != null)
                        {
                            prop.objectReferenceValue = refGo;
                        }
                    }
                    break;
            }
        }

        private object ConvertValue(Type targetType, string json)
        {
            if (targetType == typeof(bool)) return ToolParamHelper.ExtractBool(json, "value");
            if (targetType == typeof(float)) return ToolParamHelper.ExtractFloat(json, "value");
            if (targetType == typeof(int)) return (int)ToolParamHelper.ExtractFloat(json, "value");
            if (targetType == typeof(string)) return ToolParamHelper.ExtractString(json, "value");
            if (targetType == typeof(Vector2))
            {
                var vec = ToolParamHelper.Parse<Vector3Data>(ExtractObject(json, "value"));
                return new Vector2(vec.x, vec.y);
            }
            if (targetType == typeof(Vector3))
            {
                var vec = ToolParamHelper.Parse<Vector3Data>(ExtractObject(json, "value"));
                return vec.ToVector3();
            }
            if (targetType == typeof(Vector4))
            {
                var vec = ToolParamHelper.Parse<Vector3Data>(ExtractObject(json, "value"));
                return new Vector4(vec.x, vec.y, vec.z, 0);
            }
            if (targetType == typeof(Color))
            {
                string hex = ToolParamHelper.ExtractString(json, "value", null);
                if (!string.IsNullOrEmpty(hex) && ColorUtility.TryParseHtmlString(hex, out Color col))
                    return col;
            }
            if (targetType == typeof(Quaternion))
            {
                var vec = ToolParamHelper.Parse<Vector3Data>(ExtractObject(json, "value"));
                return Quaternion.Euler(vec.ToVector3());
            }
            if (targetType.IsEnum)
            {
                string enumStr = ToolParamHelper.ExtractString(json, "value", null);
                if (!string.IsNullOrEmpty(enumStr))
                {
                    try { return Enum.Parse(targetType, enumStr, true); } catch { /* Ignore */ }
                }
                int enumInt = (int)ToolParamHelper.ExtractFloat(json, "value", -1);
                if (enumInt >= 0) return Enum.ToObject(targetType, enumInt);
            }
            if (typeof(GameObject).IsAssignableFrom(targetType))
            {
                string refName = ToolParamHelper.ExtractString(json, "value", null);
                if (!string.IsNullOrEmpty(refName)) return ToolParamHelper.FindTarget(refName);
            }
            if (typeof(Component).IsAssignableFrom(targetType))
            {
                string refName = ToolParamHelper.ExtractString(json, "value", null);
                if (!string.IsNullOrEmpty(refName))
                {
                    GameObject refGo = ToolParamHelper.FindTarget(refName);
                    if (refGo != null) return refGo.GetComponent(targetType);
                }
            }
            return null;
        }

        private string ExtractObject(string json, string key)
        {
            string pattern = $"\"{key}\":";
            int start = json.IndexOf(pattern, StringComparison.Ordinal);
            if (start < 0) return null;
            start += pattern.Length;
            while (start < json.Length && char.IsWhiteSpace(json[start])) start++;
            if (start >= json.Length || json[start] != '{') return null;

            int depth = 0;
            int end = start;
            for (; end < json.Length; end++)
            {
                if (json[end] == '{') depth++;
                else if (json[end] == '}') { depth--; if (depth == 0) { end++; break; } }
            }
            return json.Substring(start, end - start);
        }
    }

    public class GetComponentTool : IBridgeTool
    {
        public string ToolName => "get_component";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string compType = ToolParamHelper.ExtractString(request.parameters, "componentType");

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{target}'");
            }

            Type type = AddComponentTool.ResolveComponentType(compType);
            if (type == null)
            {
                return BridgeMessage.Error(request.operationId, "TYPE_NOT_FOUND", $"Could not resolve Component type '{compType}'");
            }

            Component comp = go.GetComponent(type);
            if (comp == null)
            {
                return BridgeMessage.Error(request.operationId, "COMPONENT_NOT_FOUND", $"Component '{compType}' not found on '{target}'");
            }

            bool isBehaviour = comp is Behaviour b && b.enabled;
            string json = "{"
                + "\"target\":\"" + go.name + "\","
                + "\"componentType\":\"" + comp.GetType().Name + "\","
                + "\"fullType\":\"" + comp.GetType().FullName + "\","
                + "\"enabled\":" + (comp is Behaviour beh ? (beh.enabled ? "true" : "false") : "true") + ","
                + "\"instanceId\":" + comp.GetInstanceID()
                + "}";

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class GetComponentPropertiesTool : IBridgeTool
    {
        public string ToolName => "get_component_properties";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string compType = ToolParamHelper.ExtractString(request.parameters, "componentType");

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{target}'");
            }

            Type type = AddComponentTool.ResolveComponentType(compType);
            if (type == null)
            {
                return BridgeMessage.Error(request.operationId, "TYPE_NOT_FOUND", $"Could not resolve Component type '{compType}'");
            }

            Component comp = go.GetComponent(type);
            if (comp == null)
            {
                return BridgeMessage.Error(request.operationId, "COMPONENT_NOT_FOUND", $"Component '{compType}' not found on '{target}'");
            }

            var propList = new System.Collections.Generic.List<string>();
            var serializedObject = new SerializedObject(comp);
            SerializedProperty iter = serializedObject.GetIterator();
            int count = 0;
            if (iter.NextVisible(true))
            {
                do
                {
                    if (count++ > 50) break; // Limit payload
                    string valStr = iter.propertyType.ToString();
                    propList.Add("{\"" + iter.name + "\":\"" + valStr + "\"}");
                } while (iter.NextVisible(false));
            }

            string json = "{"
                + "\"target\":\"" + go.name + "\","
                + "\"componentType\":\"" + comp.GetType().Name + "\","
                + "\"properties\":[" + string.Join(",", propList) + "]"
                + "}";

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class RemoveComponentTool : IBridgeTool
    {
        public string ToolName => "remove_component";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string compType = ToolParamHelper.ExtractString(request.parameters, "componentType");

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Could not find GameObject '{target}'");
            }

            Type type = AddComponentTool.ResolveComponentType(compType);
            if (type == null)
            {
                return BridgeMessage.Error(request.operationId, "TYPE_NOT_FOUND", $"Could not resolve Component type '{compType}'");
            }

            Component comp = go.GetComponent(type);
            if (comp == null)
            {
                return BridgeMessage.Error(request.operationId, "COMPONENT_NOT_FOUND", $"GameObject '{target}' does not have a '{compType}' component");
            }

            string name = comp.GetType().Name;
            Undo.DestroyObjectImmediate(comp);

            var responseData = new ComponentResponseData
            {
                target = go.name,
                componentType = name,
                action = "removed",
                details = $"Component '{name}' removed from '{go.name}'"
            };

            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, JsonHelper.ToJson(responseData));
        }
    }
}
