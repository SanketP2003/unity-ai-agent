package com.unityagent.unity;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UnityMessage serialization, deserialization, and factory methods.
 */
class UnityMessageTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        // Match the Spring Jackson configuration
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void testSerializeToolRequest() throws Exception {
        UnityMessage msg = UnityMessage.toolRequest("create_test_cube", Map.of());
        String json = mapper.writeValueAsString(msg);

        assertTrue(json.contains("\"protocolVersion\":\"1.0\""));
        assertTrue(json.contains("\"type\":\"TOOL_REQUEST\""));
        assertTrue(json.contains("\"tool\":\"create_test_cube\""));
        assertNotNull(msg.getOperationId());
        assertFalse(msg.getOperationId().isBlank());
    }

    @Test
    void testSerializeHandshakeAck() throws Exception {
        UnityMessage msg = UnityMessage.handshakeAck("test-op-id");
        String json = mapper.writeValueAsString(msg);

        assertTrue(json.contains("\"type\":\"HANDSHAKE_ACK\""));
        assertTrue(json.contains("\"operationId\":\"test-op-id\""));
        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("\"serverVersion\""));
    }

    @Test
    void testSerializePing() throws Exception {
        UnityMessage msg = UnityMessage.ping();
        String json = mapper.writeValueAsString(msg);

        assertTrue(json.contains("\"type\":\"PING\""));
        assertNotNull(msg.getOperationId());
    }

    @Test
    void testSerializeError() throws Exception {
        UnityMessage msg = UnityMessage.error("op-123", "TEST_ERROR", "Something failed");
        String json = mapper.writeValueAsString(msg);

        assertTrue(json.contains("\"type\":\"ERROR\""));
        assertTrue(json.contains("\"success\":false"));
        assertTrue(json.contains("\"code\":\"TEST_ERROR\""));
        assertTrue(json.contains("\"message\":\"Something failed\""));
    }

    @Test
    void testDeserializeHandshake() throws Exception {
        String json = """
                {
                    "protocolVersion": "1.0",
                    "type": "HANDSHAKE",
                    "operationId": "uuid-123",
                    "data": {
                        "client": "unity",
                        "bridgeVersion": "0.1.0",
                        "unityVersion": "6000.4.7f1"
                    }
                }
                """;
        UnityMessage msg = mapper.readValue(json, UnityMessage.class);

        assertEquals("1.0", msg.getProtocolVersion());
        assertEquals(UnityMessage.MessageType.HANDSHAKE, msg.getType());
        assertEquals("uuid-123", msg.getOperationId());
        assertEquals("unity", msg.getData().get("client"));
        assertEquals("6000.4.7f1", msg.getData().get("unityVersion"));
    }

    @Test
    void testDeserializeToolResponse() throws Exception {
        String json = """
                {
                    "protocolVersion": "1.0",
                    "type": "TOOL_RESPONSE",
                    "operationId": "op-456",
                    "tool": "create_test_cube",
                    "success": true,
                    "data": {
                        "object": "AIAgent_TestCube",
                        "position": { "x": 0, "y": 0, "z": 0 }
                    },
                    "errors": [],
                    "warnings": []
                }
                """;
        UnityMessage msg = mapper.readValue(json, UnityMessage.class);

        assertEquals(UnityMessage.MessageType.TOOL_RESPONSE, msg.getType());
        assertEquals("create_test_cube", msg.getTool());
        assertTrue(msg.getSuccess());
        assertEquals("AIAgent_TestCube", msg.getData().get("object"));
    }

    @Test
    void testDeserializeFailedToolResponse() throws Exception {
        String json = """
                {
                    "protocolVersion": "1.0",
                    "type": "TOOL_RESPONSE",
                    "operationId": "op-789",
                    "tool": "create_test_cube",
                    "success": false,
                    "errors": [
                        {
                            "code": "UNITY_OPERATION_FAILED",
                            "message": "Failed to create GameObject."
                        }
                    ]
                }
                """;
        UnityMessage msg = mapper.readValue(json, UnityMessage.class);

        assertFalse(msg.getSuccess());
        assertNotNull(msg.getErrors());
        assertEquals(1, msg.getErrors().size());
        assertEquals("UNITY_OPERATION_FAILED", msg.getErrors().get(0).getCode());
    }

    @Test
    void testDeserializeMissingFields() throws Exception {
        String json = """
                {
                    "type": "PONG",
                    "operationId": "op-minimal"
                }
                """;
        UnityMessage msg = mapper.readValue(json, UnityMessage.class);

        assertEquals(UnityMessage.MessageType.PONG, msg.getType());
        assertEquals("op-minimal", msg.getOperationId());
        assertNull(msg.getTool());
        assertNull(msg.getData());
        assertNull(msg.getSuccess());
    }

    @Test
    void testDeserializeUnknownFieldsIgnored() throws Exception {
        String json = """
                {
                    "type": "PING",
                    "operationId": "op-extra",
                    "unexpectedField": "should be ignored"
                }
                """;
        // With FAIL_ON_UNKNOWN_PROPERTIES=false, this should not throw
        UnityMessage msg = mapper.readValue(json, UnityMessage.class);
        assertEquals(UnityMessage.MessageType.PING, msg.getType());
    }

    @Test
    void testNullFieldsNotSerialized() throws Exception {
        UnityMessage msg = UnityMessage.ping();
        String json = mapper.writeValueAsString(msg);

        // PING should not include tool, parameters, data, errors, etc.
        assertFalse(json.contains("\"tool\""));
        assertFalse(json.contains("\"parameters\""));
        assertFalse(json.contains("\"errors\""));
        assertFalse(json.contains("\"warnings\""));
    }

    @Test
    void testRoundTripToolRequest() throws Exception {
        UnityMessage original = UnityMessage.toolRequest("create_test_cube", Map.of("key", "value"));
        String json = mapper.writeValueAsString(original);
        UnityMessage restored = mapper.readValue(json, UnityMessage.class);

        assertEquals(original.getType(), restored.getType());
        assertEquals(original.getOperationId(), restored.getOperationId());
        assertEquals(original.getTool(), restored.getTool());
        assertEquals(original.getProtocolVersion(), restored.getProtocolVersion());
        assertEquals("value", restored.getParameters().get("key"));
    }
}
