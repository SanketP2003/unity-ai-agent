using System;
using System.Reflection;
using AutonomousUnityAgent.Models;
using UnityEditor;
using UnityEngine;

namespace AutonomousUnityAgent.Editor.Tools
{
    internal static class UIHelper
    {
        public static Type ResolveType(string typeName)
        {
            return AddComponentTool.ResolveComponentType(typeName);
        }

        public static Component AddUIComponent(GameObject go, string typeName)
        {
            Type t = ResolveType(typeName);
            if (t == null) return null;
            var comp = go.GetComponent(t);
            if (comp == null) comp = go.AddComponent(t);
            return comp;
        }

        public static void SetProperty(object target, string name, object value)
        {
            if (target == null) return;
            var prop = target.GetType().GetProperty(name, BindingFlags.Public | BindingFlags.Instance | BindingFlags.IgnoreCase);
            if (prop != null && prop.CanWrite)
            {
                try
                {
                    if (prop.PropertyType.IsEnum && value is string s)
                    {
                        prop.SetValue(target, Enum.Parse(prop.PropertyType, s, true));
                    }
                    else if (prop.PropertyType == typeof(Color) && value is Color c)
                    {
                        prop.SetValue(target, c);
                    }
                    else if (prop.PropertyType == typeof(Vector2) && value is Vector2 v2)
                    {
                        prop.SetValue(target, v2);
                    }
                    else
                    {
                        prop.SetValue(target, Convert.ChangeType(value, prop.PropertyType));
                    }
                }
                catch { }
                return;
            }

            var field = target.GetType().GetField(name, BindingFlags.Public | BindingFlags.Instance | BindingFlags.IgnoreCase);
            if (field != null)
            {
                try
                {
                    if (field.FieldType.IsEnum && value is string s)
                    {
                        field.SetValue(target, Enum.Parse(field.FieldType, s, true));
                    }
                    else if (field.FieldType == typeof(Color) && value is Color c)
                    {
                        field.SetValue(target, c);
                    }
                    else if (field.FieldType == typeof(Vector2) && value is Vector2 v2)
                    {
                        field.SetValue(target, v2);
                    }
                    else
                    {
                        field.SetValue(target, Convert.ChangeType(value, field.FieldType));
                    }
                }
                catch { }
            }
        }
    }

    public class CreateCanvasTool : IBridgeTool
    {
        public string ToolName => "create_canvas";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "Canvas");
            GameObject canvasGo = ToolParamHelper.FindTarget(name);
            if (canvasGo == null)
            {
                canvasGo = new GameObject(name);
                var canvas = canvasGo.AddComponent<Canvas>();
                canvas.renderMode = RenderMode.ScreenSpaceOverlay;

                var scaler = UIHelper.AddUIComponent(canvasGo, "CanvasScaler");
                if (scaler != null)
                {
                    UIHelper.SetProperty(scaler, "uiScaleMode", "ScaleWithScreenSize");
                    UIHelper.SetProperty(scaler, "referenceResolution", new Vector2(1920, 1080));
                }

                UIHelper.AddUIComponent(canvasGo, "GraphicRaycaster");
                Undo.RegisterCreatedObjectUndo(canvasGo, "Create Canvas " + name);
            }

            // Ensure EventSystem exists
            Type esType = UIHelper.ResolveType("EventSystem");
            if (esType != null && UnityEngine.Object.FindObjectOfType(esType) == null)
            {
                var eventSystemGo = new GameObject("EventSystem");
                eventSystemGo.AddComponent(esType);
                UIHelper.AddUIComponent(eventSystemGo, "StandaloneInputModule");
                Undo.RegisterCreatedObjectUndo(eventSystemGo, "Create EventSystem");
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Canvas '{canvasGo.name}' created/configured with EventSystem",
                target = canvasGo.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreatePanelTool : IBridgeTool
    {
        public string ToolName => "create_panel";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "Panel");
            string parent = ToolParamHelper.ExtractString(request.parameters, "parent", "Canvas");

            GameObject parentGo = ToolParamHelper.FindTarget(parent);
            if (parentGo == null)
            {
                var canvasTool = new CreateCanvasTool();
                canvasTool.Execute(request);
                parentGo = ToolParamHelper.FindTarget("Canvas");
            }

            var panelGo = new GameObject(name);
            panelGo.transform.SetParent(parentGo.transform, false);
            var rect = panelGo.AddComponent<RectTransform>();
            var img = UIHelper.AddUIComponent(panelGo, "Image");

            string colorStr = ToolParamHelper.ExtractString(request.parameters, "color", "#000000AA");
            if (ColorUtility.TryParseHtmlString(colorStr, out Color col) && img != null)
            {
                UIHelper.SetProperty(img, "color", col);
            }

            rect.anchorMin = Vector2.zero;
            rect.anchorMax = Vector2.one;
            rect.offsetMin = Vector2.zero;
            rect.offsetMax = Vector2.zero;

            Undo.RegisterCreatedObjectUndo(panelGo, "Create Panel " + name);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Created Panel '{name}' under '{parentGo.name}'",
                target = name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreateTextTool : IBridgeTool
    {
        public string ToolName => "create_text";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "Text");
            string textVal = ToolParamHelper.ExtractString(request.parameters, "text", "New Text");
            string parent = ToolParamHelper.ExtractString(request.parameters, "parent", "Canvas");

            GameObject parentGo = ToolParamHelper.FindTarget(parent);
            if (parentGo == null)
            {
                var canvasTool = new CreateCanvasTool();
                canvasTool.Execute(request);
                parentGo = ToolParamHelper.FindTarget("Canvas");
            }

            var textGo = new GameObject(name);
            textGo.transform.SetParent(parentGo.transform, false);
            var rect = textGo.AddComponent<RectTransform>();

            Component txt = UIHelper.AddUIComponent(textGo, "Text") ?? UIHelper.AddUIComponent(textGo, "TextMeshProUGUI");
            if (txt != null)
            {
                UIHelper.SetProperty(txt, "text", textVal);

                Font f = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf") ?? Resources.GetBuiltinResource<Font>("Arial.ttf");
                if (f == null) f = Font.CreateDynamicFontFromOSFont("Arial", 16);
                if (f != null) UIHelper.SetProperty(txt, "font", f);

                float fontSize = ToolParamHelper.ExtractFloat(request.parameters, "fontSize", 24f);
                UIHelper.SetProperty(txt, "fontSize", (int)fontSize);

                string colorStr = ToolParamHelper.ExtractString(request.parameters, "color", "#FFFFFF");
                if (ColorUtility.TryParseHtmlString(colorStr, out Color col))
                {
                    UIHelper.SetProperty(txt, "color", col);
                }
            }

            rect.sizeDelta = new Vector2(300, 60);
            float px = ToolParamHelper.ExtractFloat(request.parameters, "x", 0f);
            float py = ToolParamHelper.ExtractFloat(request.parameters, "y", 0f);
            rect.anchoredPosition = new Vector2(px, py);

            Undo.RegisterCreatedObjectUndo(textGo, "Create Text " + name);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Created Text '{name}' with content '{textVal}'",
                target = name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreateImageTool : IBridgeTool
    {
        public string ToolName => "create_image";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "Image");
            string parent = ToolParamHelper.ExtractString(request.parameters, "parent", "Canvas");

            GameObject parentGo = ToolParamHelper.FindTarget(parent);
            if (parentGo == null)
            {
                var canvasTool = new CreateCanvasTool();
                canvasTool.Execute(request);
                parentGo = ToolParamHelper.FindTarget("Canvas");
            }

            var imgGo = new GameObject(name);
            imgGo.transform.SetParent(parentGo.transform, false);
            var rect = imgGo.AddComponent<RectTransform>();
            var img = UIHelper.AddUIComponent(imgGo, "Image");

            string colorStr = ToolParamHelper.ExtractString(request.parameters, "color", "#FFFFFF");
            if (ColorUtility.TryParseHtmlString(colorStr, out Color col) && img != null)
            {
                UIHelper.SetProperty(img, "color", col);
            }

            float w = ToolParamHelper.ExtractFloat(request.parameters, "width", 100f);
            float h = ToolParamHelper.ExtractFloat(request.parameters, "height", 100f);
            rect.sizeDelta = new Vector2(w, h);

            float px = ToolParamHelper.ExtractFloat(request.parameters, "x", 0f);
            float py = ToolParamHelper.ExtractFloat(request.parameters, "y", 0f);
            rect.anchoredPosition = new Vector2(px, py);

            Undo.RegisterCreatedObjectUndo(imgGo, "Create Image " + name);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Created Image '{name}' under '{parentGo.name}'",
                target = name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreateButtonTool : IBridgeTool
    {
        public string ToolName => "create_button";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "Button");
            string label = ToolParamHelper.ExtractString(request.parameters, "text", "Button");
            string parent = ToolParamHelper.ExtractString(request.parameters, "parent", "Canvas");

            GameObject parentGo = ToolParamHelper.FindTarget(parent);
            if (parentGo == null)
            {
                var canvasTool = new CreateCanvasTool();
                canvasTool.Execute(request);
                parentGo = ToolParamHelper.FindTarget("Canvas");
            }

            var btnGo = new GameObject(name);
            btnGo.transform.SetParent(parentGo.transform, false);
            var rect = btnGo.AddComponent<RectTransform>();
            var img = UIHelper.AddUIComponent(btnGo, "Image");
            if (img != null) UIHelper.SetProperty(img, "color", new Color(0.2f, 0.4f, 0.8f, 1f));

            UIHelper.AddUIComponent(btnGo, "Button");

            rect.sizeDelta = new Vector2(160, 45);
            float px = ToolParamHelper.ExtractFloat(request.parameters, "x", 0f);
            float py = ToolParamHelper.ExtractFloat(request.parameters, "y", 0f);
            rect.anchoredPosition = new Vector2(px, py);

            // Child text
            var labelGo = new GameObject("Text");
            labelGo.transform.SetParent(btnGo.transform, false);
            var labelRect = labelGo.AddComponent<RectTransform>();
            labelRect.anchorMin = Vector2.zero;
            labelRect.anchorMax = Vector2.one;
            labelRect.offsetMin = Vector2.zero;
            labelRect.offsetMax = Vector2.zero;

            Component txt = UIHelper.AddUIComponent(labelGo, "Text") ?? UIHelper.AddUIComponent(labelGo, "TextMeshProUGUI");
            if (txt != null)
            {
                UIHelper.SetProperty(txt, "text", label);
                UIHelper.SetProperty(txt, "alignment", "MiddleCenter");
                UIHelper.SetProperty(txt, "fontSize", 18);
                UIHelper.SetProperty(txt, "color", Color.white);
                Font f = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf") ?? Resources.GetBuiltinResource<Font>("Arial.ttf");
                if (f != null) UIHelper.SetProperty(txt, "font", f);
            }

            Undo.RegisterCreatedObjectUndo(btnGo, "Create Button " + name);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Created Button '{name}' with label '{label}'",
                target = name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreateSliderTool : IBridgeTool
    {
        public string ToolName => "create_slider";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "Slider");
            string parent = ToolParamHelper.ExtractString(request.parameters, "parent", "Canvas");

            GameObject parentGo = ToolParamHelper.FindTarget(parent);
            if (parentGo == null)
            {
                var canvasTool = new CreateCanvasTool();
                canvasTool.Execute(request);
                parentGo = ToolParamHelper.FindTarget("Canvas");
            }

            var sliderGo = new GameObject(name);
            sliderGo.transform.SetParent(parentGo.transform, false);
            var rect = sliderGo.AddComponent<RectTransform>();
            rect.sizeDelta = new Vector2(200, 30);
            var slider = UIHelper.AddUIComponent(sliderGo, "Slider");

            float min = ToolParamHelper.ExtractFloat(request.parameters, "minValue", 0f);
            float max = ToolParamHelper.ExtractFloat(request.parameters, "maxValue", 100f);
            float val = ToolParamHelper.ExtractFloat(request.parameters, "value", 50f);

            if (slider != null)
            {
                UIHelper.SetProperty(slider, "minValue", min);
                UIHelper.SetProperty(slider, "maxValue", max);
                UIHelper.SetProperty(slider, "value", val);
            }

            Undo.RegisterCreatedObjectUndo(sliderGo, "Create Slider " + name);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Created Slider '{name}' (min={min}, max={max})",
                target = name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreateProgressBarTool : IBridgeTool
    {
        public string ToolName => "create_progress_bar";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string name = ToolParamHelper.ExtractString(request.parameters, "name", "HealthBar");
            string parent = ToolParamHelper.ExtractString(request.parameters, "parent", "Canvas");

            GameObject parentGo = ToolParamHelper.FindTarget(parent);
            if (parentGo == null)
            {
                var canvasTool = new CreateCanvasTool();
                canvasTool.Execute(request);
                parentGo = ToolParamHelper.FindTarget("Canvas");
            }

            var rootGo = new GameObject(name);
            rootGo.transform.SetParent(parentGo.transform, false);
            var rootRect = rootGo.AddComponent<RectTransform>();
            float w = ToolParamHelper.ExtractFloat(request.parameters, "width", 250f);
            float h = ToolParamHelper.ExtractFloat(request.parameters, "height", 30f);
            rootRect.sizeDelta = new Vector2(w, h);

            // Background
            var bgGo = new GameObject("Background");
            bgGo.transform.SetParent(rootGo.transform, false);
            var bgRect = bgGo.AddComponent<RectTransform>();
            bgRect.anchorMin = Vector2.zero;
            bgRect.anchorMax = Vector2.one;
            bgRect.offsetMin = Vector2.zero;
            bgRect.offsetMax = Vector2.zero;
            var bgImg = UIHelper.AddUIComponent(bgGo, "Image");
            if (bgImg != null) UIHelper.SetProperty(bgImg, "color", new Color(0.2f, 0.2f, 0.2f, 0.8f));

            // Fill area
            var fillGo = new GameObject("Fill");
            fillGo.transform.SetParent(rootGo.transform, false);
            var fillRect = fillGo.AddComponent<RectTransform>();
            fillRect.anchorMin = Vector2.zero;
            fillRect.anchorMax = new Vector2(1f, 1f);
            fillRect.offsetMin = Vector2.zero;
            fillRect.offsetMax = Vector2.zero;
            var fillImg = UIHelper.AddUIComponent(fillGo, "Image");
            string colStr = ToolParamHelper.ExtractString(request.parameters, "color", "#00FF44");
            if (fillImg != null)
            {
                if (ColorUtility.TryParseHtmlString(colStr, out Color c)) UIHelper.SetProperty(fillImg, "color", c);
                else UIHelper.SetProperty(fillImg, "color", Color.green);
            }

            Undo.RegisterCreatedObjectUndo(rootGo, "Create ProgressBar " + name);

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Created Progress Bar '{name}' (width={w}, height={h})",
                target = name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class CreateUIElementTool : IBridgeTool
    {
        public string ToolName => "create_ui_element";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string elementType = ToolParamHelper.ExtractString(request.parameters, "type", "text").ToLowerInvariant();
            switch (elementType)
            {
                case "panel": return new CreatePanelTool().Execute(request);
                case "text": return new CreateTextTool().Execute(request);
                case "image": return new CreateImageTool().Execute(request);
                case "button": return new CreateButtonTool().Execute(request);
                case "slider": return new CreateSliderTool().Execute(request);
                case "progressbar":
                case "healthbar": return new CreateProgressBarTool().Execute(request);
                default: return new CreateTextTool().Execute(request);
            }
        }
    }

    public class SetUIPropertyTool : IBridgeTool
    {
        public string ToolName => "set_ui_property";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null)
            {
                return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"UI element '{target}' not found");
            }

            var rect = go.GetComponent<RectTransform>();
            if (rect == null)
            {
                return BridgeMessage.Error(request.operationId, "NO_RECT_TRANSFORM", $"'{target}' does not have a RectTransform");
            }

            Undo.RecordObject(rect, "Set UI Property " + go.name);

            float px = ToolParamHelper.ExtractFloat(request.parameters, "x", -9999f);
            float py = ToolParamHelper.ExtractFloat(request.parameters, "y", -9999f);
            if (px != -9999f || py != -9999f)
            {
                Vector2 cur = rect.anchoredPosition;
                rect.anchoredPosition = new Vector2(px != -9999f ? px : cur.x, py != -9999f ? py : cur.y);
            }

            float w = ToolParamHelper.ExtractFloat(request.parameters, "width", -1f);
            float h = ToolParamHelper.ExtractFloat(request.parameters, "height", -1f);
            if (w >= 0f || h >= 0f)
            {
                Vector2 curSize = rect.sizeDelta;
                rect.sizeDelta = new Vector2(w >= 0f ? w : curSize.x, h >= 0f ? h : curSize.y);
            }

            string textStr = ToolParamHelper.ExtractString(request.parameters, "text");
            if (!string.IsNullOrEmpty(textStr))
            {
                Component txt = go.GetComponent(UIHelper.ResolveType("Text")) ?? go.GetComponent(UIHelper.ResolveType("TextMeshProUGUI"));
                if (txt != null)
                {
                    Undo.RecordObject(txt, "Set UI Text");
                    UIHelper.SetProperty(txt, "text", textStr);
                }
            }

            string colStr = ToolParamHelper.ExtractString(request.parameters, "color");
            if (!string.IsNullOrEmpty(colStr) && ColorUtility.TryParseHtmlString(colStr, out Color col))
            {
                Component img = go.GetComponent(UIHelper.ResolveType("Graphic")) ?? go.GetComponent(UIHelper.ResolveType("Image"));
                if (img != null)
                {
                    Undo.RecordObject(img, "Set UI Color");
                    UIHelper.SetProperty(img, "color", col);
                }
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Updated UI properties for '{go.name}'",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class SetUILayoutTool : IBridgeTool
    {
        public string ToolName => "set_ui_layout";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string layoutType = ToolParamHelper.ExtractString(request.parameters, "layoutType", "Vertical");
            float spacing = ToolParamHelper.ExtractFloat(request.parameters, "spacing", 10f);

            GameObject go = ToolParamHelper.FindTarget(target);
            if (go == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"UI target '{target}' not found");

            string groupName = layoutType.Equals("Horizontal", StringComparison.OrdinalIgnoreCase)
                ? "HorizontalLayoutGroup"
                : layoutType.Equals("Grid", StringComparison.OrdinalIgnoreCase)
                    ? "GridLayoutGroup"
                    : "VerticalLayoutGroup";

            Component group = UIHelper.AddUIComponent(go, groupName);
            if (group != null)
            {
                UIHelper.SetProperty(group, "spacing", spacing);
                UIHelper.SetProperty(group, "childAlignment", "MiddleCenter");
            }

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"Configured {layoutType}LayoutGroup on '{go.name}'",
                target = go.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }

    public class BindUIEventTool : IBridgeTool
    {
        public string ToolName => "bind_ui_event";

        public BridgeMessage Execute(BridgeMessage request)
        {
            string target = ToolParamHelper.ExtractString(request.parameters, "target");
            string listenerTarget = ToolParamHelper.ExtractString(request.parameters, "listener");
            string methodName = ToolParamHelper.ExtractString(request.parameters, "method");

            GameObject btnGo = ToolParamHelper.FindTarget(target);
            if (btnGo == null) return BridgeMessage.Error(request.operationId, "TARGET_NOT_FOUND", $"Button '{target}' not found");

            string json = JsonHelper.ToJson(new SimpleSuccessData
            {
                message = $"UI Event bound for '{btnGo.name}' -> listener '{listenerTarget}', method '{methodName}'",
                target = btnGo.name
            });
            return BridgeMessage.ToolResponse(request.operationId, ToolName, true, json);
        }
    }
}
