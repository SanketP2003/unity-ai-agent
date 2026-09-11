using System;
using System.Collections.Concurrent;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using UnityEngine;

namespace AutonomousUnityAgent.Editor
{
    /// <summary>
    /// Low-level WebSocket client using System.Net.WebSockets.ClientWebSocket.
    /// Handles connection, disconnection, send/receive with thread-safe queuing,
    /// and graceful shutdown.
    ///
    /// All received messages are queued and must be dequeued on the main thread
    /// via TryDequeueMessage(). Unity API calls must NOT happen on the receive thread.
    /// </summary>
    public class WebSocketClient : IDisposable
    {
        private ClientWebSocket _webSocket;
        private CancellationTokenSource _cts;
        private readonly ConcurrentQueue<string> _incomingMessages = new ConcurrentQueue<string>();
        private readonly ConcurrentQueue<string> _outgoingMessages = new ConcurrentQueue<string>();
        private volatile bool _isConnected;
        private volatile bool _isConnecting;
        private Task _receiveTask;
        private Task _sendTask;

        private const int ReceiveBufferSize = 8192;
        private const int MaxMessageSize = 1024 * 1024; // 1MB max message

        public bool IsConnected => _isConnected;
        public bool IsConnecting => _isConnecting;

        /// <summary>
        /// Connect to the WebSocket server.
        /// </summary>
        public async Task ConnectAsync(string url)
        {
            if (_isConnected || _isConnecting)
            {
                Debug.LogWarning("[WebSocketClient] Already connected or connecting.");
                return;
            }

            _isConnecting = true;

            try
            {
                Dispose(); // Clean up any previous connection

                _webSocket = new ClientWebSocket();
                _cts = new CancellationTokenSource();

                Debug.Log($"[WebSocketClient] Connecting to {url}...");
                await _webSocket.ConnectAsync(new Uri(url), _cts.Token);

                _isConnected = true;
                _isConnecting = false;
                Debug.Log("[WebSocketClient] Connected.");

                // Start receive and send loops
                _receiveTask = Task.Run(() => ReceiveLoop(_cts.Token));
                _sendTask = Task.Run(() => SendLoop(_cts.Token));
            }
            catch (Exception ex)
            {
                _isConnecting = false;
                _isConnected = false;
                Debug.LogError($"[WebSocketClient] Connection failed: {ex.Message}");
                throw;
            }
        }

        /// <summary>
        /// Disconnect from the WebSocket server.
        /// </summary>
        public async Task DisconnectAsync()
        {
            if (!_isConnected && !_isConnecting) return;

            Debug.Log("[WebSocketClient] Disconnecting...");

            try
            {
                _cts?.Cancel();

                if (_webSocket?.State == WebSocketState.Open)
                {
                    using var closeCts = new CancellationTokenSource(TimeSpan.FromSeconds(3));
                    await _webSocket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Client closing",
                        closeCts.Token);
                }
            }
            catch (Exception ex)
            {
                Debug.LogWarning($"[WebSocketClient] Error during disconnect: {ex.Message}");
            }
            finally
            {
                _isConnected = false;
                _isConnecting = false;
                Debug.Log("[WebSocketClient] Disconnected.");
            }
        }

        /// <summary>
        /// Queue a message for sending. Thread-safe.
        /// </summary>
        public void Send(string message)
        {
            if (!_isConnected)
            {
                Debug.LogWarning("[WebSocketClient] Cannot send: not connected.");
                return;
            }
            _outgoingMessages.Enqueue(message);
        }

        /// <summary>
        /// Try to dequeue a received message. Call from the main thread (EditorApplication.update).
        /// </summary>
        public bool TryDequeueMessage(out string message)
        {
            return _incomingMessages.TryDequeue(out message);
        }

        /// <summary>
        /// Number of messages waiting to be processed.
        /// </summary>
        public int PendingIncomingCount => _incomingMessages.Count;

        // --- Background loops ---

        private async Task ReceiveLoop(CancellationToken ct)
        {
            var buffer = new byte[ReceiveBufferSize];
            var messageBuffer = new StringBuilder();

            try
            {
                while (!ct.IsCancellationRequested && _webSocket?.State == WebSocketState.Open)
                {
                    var segment = new ArraySegment<byte>(buffer);
                    WebSocketReceiveResult result;

                    try
                    {
                        result = await _webSocket.ReceiveAsync(segment, ct);
                    }
                    catch (OperationCanceledException)
                    {
                        break;
                    }

                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        Debug.Log("[WebSocketClient] Server initiated close.");
                        _isConnected = false;
                        break;
                    }

                    if (result.MessageType == WebSocketMessageType.Text)
                    {
                        messageBuffer.Append(Encoding.UTF8.GetString(buffer, 0, result.Count));

                        if (result.EndOfMessage)
                        {
                            string message = messageBuffer.ToString();
                            messageBuffer.Clear();

                            if (message.Length <= MaxMessageSize)
                            {
                                _incomingMessages.Enqueue(message);
                            }
                            else
                            {
                                Debug.LogWarning($"[WebSocketClient] Dropped oversized message ({message.Length} bytes)");
                            }
                        }
                    }
                }
            }
            catch (WebSocketException ex)
            {
                Debug.LogError($"[WebSocketClient] Receive error: {ex.Message}");
            }
            catch (OperationCanceledException)
            {
                // Expected during shutdown
            }
            catch (Exception ex)
            {
                Debug.LogError($"[WebSocketClient] Unexpected receive error: {ex.Message}");
            }
            finally
            {
                _isConnected = false;
            }
        }

        private async Task SendLoop(CancellationToken ct)
        {
            try
            {
                while (!ct.IsCancellationRequested && _webSocket?.State == WebSocketState.Open)
                {
                    if (_outgoingMessages.TryDequeue(out string message))
                    {
                        var bytes = Encoding.UTF8.GetBytes(message);
                        var segment = new ArraySegment<byte>(bytes);

                        try
                        {
                            await _webSocket.SendAsync(segment, WebSocketMessageType.Text,
                                true, ct);
                        }
                        catch (OperationCanceledException)
                        {
                            break;
                        }
                    }
                    else
                    {
                        // No messages to send, wait briefly
                        await Task.Delay(10, ct);
                    }
                }
            }
            catch (OperationCanceledException)
            {
                // Expected during shutdown
            }
            catch (Exception ex)
            {
                Debug.LogError($"[WebSocketClient] Send error: {ex.Message}");
                _isConnected = false;
            }
        }

        // --- IDisposable ---

        public void Dispose()
        {
            _cts?.Cancel();
            _cts?.Dispose();
            _cts = null;

            _webSocket?.Dispose();
            _webSocket = null;

            _isConnected = false;
            _isConnecting = false;

            // Clear queues
            while (_incomingMessages.TryDequeue(out _)) { }
            while (_outgoingMessages.TryDequeue(out _)) { }
        }
    }
}
