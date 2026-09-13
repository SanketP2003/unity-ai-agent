using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;

namespace AutonomousUnityAgent.Editor.Security
{
    /// <summary>
    /// Security validator for agent-generated scripts and filesystem paths.
    /// Strictly limits operations within Assets/ and prevents invocation of prohibited dangerous APIs.
    /// </summary>
    public static class ScriptSecurityHelper
    {
        private static readonly Regex[] ProhibitedPatterns = new[]
        {
            new Regex(@"\bSystem\.Diagnostics\b", RegexOptions.IgnoreCase),
            new Regex(@"\bProcessStartInfo\b"),
            new Regex(@"\bProcess\.Start\b"),
            new Regex(@"\bcmd\.exe\b", RegexOptions.IgnoreCase),
            new Regex(@"\bpowershell(\.exe)?\b", RegexOptions.IgnoreCase),
            new Regex(@"/bin/(sh|bash)"),
            new Regex(@"\[\s*DllImport\b"),
            new Regex(@"\bextern\s+.*\("),
            new Regex(@"\bAssembly\.Load(From|File)?\b"),
            new Regex(@"\bAppDomain\.CurrentDomain\.Load\b"),
            new Regex(@"\bSystem\.Environment\b"),
            new Regex(@"\bEnvironment\.(Exit|FailFast|SetEnvironmentVariable|GetEnvironmentVariable)\b"),
            new Regex(@"\bMicrosoft\.Win32\b"),
            new Regex(@"\bRegistry(Key)?\b"),
            new Regex(@"\bSystem\.Net\.Sockets\b"),
            new Regex(@"\b(Socket|TcpClient|TcpListener|UdpClient)\b")
        };

        public static string ValidatePath(string rawPath, out string normalizedPath)
        {
            normalizedPath = null;
            if (string.IsNullOrWhiteSpace(rawPath))
                return "Script path cannot be null or empty.";

            string norm = rawPath.Trim().Replace('\\', '/');

            if (norm.Contains("..") || norm.Contains("/../") || norm.StartsWith("../"))
                return $"Path traversal sequence '..' is prohibited: {rawPath}";

            if (Regex.IsMatch(norm, @"^[a-zA-Z]:"))
                return $"Absolute drive paths are prohibited: {rawPath}";

            if (norm.StartsWith("/") || norm.StartsWith("//"))
                return $"Absolute root paths are prohibited: {rawPath}";

            string lower = norm.ToLowerInvariant();
            if (lower.Contains("windows") || lower.Contains("program files"))
                return $"Access to system directories is prohibited: {rawPath}";

            if (!norm.StartsWith("Assets/") && norm != "Assets")
            {
                if (norm.StartsWith("Scripts/"))
                    norm = "Assets/" + norm;
                else
                    return $"Script path must be within 'Assets/': {rawPath}";
            }

            if (!norm.ToLowerInvariant().EndsWith(".cs"))
                return $"Script file must have a '.cs' extension: {rawPath}";

            normalizedPath = norm;
            return null;
        }

        public static string ValidateContent(string content)
        {
            if (string.IsNullOrWhiteSpace(content))
                return "Script content cannot be empty.";

            foreach (var pattern in ProhibitedPatterns)
            {
                if (pattern.IsMatch(content))
                {
                    return $"Generated script contains prohibited API or construct matching pattern: {pattern}";
                }
            }

            return null;
        }

        public static string ComputeHash(string content)
        {
            if (content == null) return "";
            using (var sha256 = SHA256.Create())
            {
                // Normalize CRLF to LF for deterministic hashes across platforms
                string normalized = content.Replace("\r\n", "\n");
                byte[] bytes = Encoding.UTF8.GetBytes(normalized);
                byte[] hash = sha256.ComputeHash(bytes);
                var sb = new StringBuilder();
                for (int i = 0; i < hash.Length; i++)
                {
                    sb.Append(hash[i].ToString("x2"));
                }
                return sb.ToString();
            }
        }
    }
}
