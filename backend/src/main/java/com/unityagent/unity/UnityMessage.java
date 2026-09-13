package com.unityagent.unity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Protocol v1.0 message exchanged between Java backend and Unity bridge.
 * Used for all WebSocket communication: handshakes, tool requests/responses,
 * events, errors, and heartbeats.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnityMessage {

    @JsonProperty("protocolVersion")
    private String protocolVersion = "1.0";

    @JsonProperty("type")
    private MessageType type;

    @JsonProperty("operationId")
    private String operationId;

    @JsonProperty("tool")
    private String tool;

    @JsonProperty("parameters")
    private Map<String, Object> parameters;

    @JsonProperty("data")
    private Map<String, Object> data;

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("projectId")
    private String projectId;

    @JsonProperty("errors")
    private List<ErrorDetail> errors;

    @JsonProperty("warnings")
    private List<String> warnings;

    public UnityMessage() {
    }

    // --- Static factory methods ---

    public static UnityMessage handshake(String operationId, String projectId, Map<String, ?> data) {
        UnityMessage msg = new UnityMessage();
        msg.type = MessageType.HANDSHAKE;
        msg.operationId = operationId != null ? operationId : UUID.randomUUID().toString();
        msg.projectId = projectId;
        if (data != null) {
            msg.data = new java.util.HashMap<>(data);
        }
        return msg;
    }

    public static final class Type {
        public static final MessageType HANDSHAKE = MessageType.HANDSHAKE;
        public static final MessageType HANDSHAKE_ACK = MessageType.HANDSHAKE_ACK;
        public static final MessageType TOOL_REQUEST = MessageType.TOOL_REQUEST;
        public static final MessageType TOOL_RESPONSE = MessageType.TOOL_RESPONSE;
        public static final MessageType EVENT = MessageType.EVENT;
        public static final MessageType ERROR = MessageType.ERROR;
        public static final MessageType PING = MessageType.PING;
        public static final MessageType PONG = MessageType.PONG;
    }

    public static UnityMessage handshakeAck(String correlatedOperationId) {
        return handshakeAck(correlatedOperationId, null);
    }

    public static UnityMessage handshakeAck(String correlatedOperationId, String projectId) {
        UnityMessage msg = new UnityMessage();
        msg.type = MessageType.HANDSHAKE_ACK;
        msg.operationId = correlatedOperationId;
        msg.projectId = projectId;
        msg.success = true;
        java.util.Map<String, Object> ackData = new java.util.HashMap<>();
        ackData.put("server", "autonomous-unity-agent");
        ackData.put("serverVersion", "1.0.0");
        ackData.put("protocolVersion", "1.0");
        if (projectId != null) {
            ackData.put("projectId", projectId);
        }
        ackData.put("capabilities", java.util.Map.of(
                "toolExecution", true,
                "perception", true,
                "dynamicPlanning", true,
                "compileRecovery", true,
                "runtimeValidation", true
        ));
        msg.data = ackData;
        return msg;
    }

    public static UnityMessage toolRequest(String tool, Map<String, Object> parameters) {
        return toolRequest(UUID.randomUUID().toString(), tool, parameters);
    }

    public static UnityMessage toolRequest(String operationId, String tool, Map<String, Object> parameters) {
        UnityMessage msg = new UnityMessage();
        msg.type = MessageType.TOOL_REQUEST;
        msg.operationId = operationId != null ? operationId : UUID.randomUUID().toString();
        msg.tool = tool;
        msg.parameters = parameters;
        return msg;
    }

    public static UnityMessage ping() {
        UnityMessage msg = new UnityMessage();
        msg.type = MessageType.PING;
        msg.operationId = UUID.randomUUID().toString();
        return msg;
    }

    public static UnityMessage toolResponse(String operationId, boolean success, Map<String, Object> data) {
        UnityMessage msg = new UnityMessage();
        msg.type = MessageType.TOOL_RESPONSE;
        msg.operationId = operationId;
        msg.success = success;
        msg.data = data;
        return msg;
    }

    public static UnityMessage error(String operationId, String code, String message) {
        UnityMessage msg = new UnityMessage();
        msg.type = MessageType.ERROR;
        msg.operationId = operationId;
        msg.success = false;
        msg.errors = new ArrayList<>();
        msg.errors.add(new ErrorDetail(code, message));
        return msg;
    }

    // --- Getters and Setters ---

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public List<ErrorDetail> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorDetail> errors) {
        this.errors = errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    // --- Message Types ---

    public enum MessageType {
        HANDSHAKE,
        HANDSHAKE_ACK,
        TOOL_REQUEST,
        TOOL_RESPONSE,
        EVENT,
        ERROR,
        PING,
        PONG
    }

    // --- Error Detail ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {

        @JsonProperty("code")
        private String code;

        @JsonProperty("message")
        private String message;

        public ErrorDetail() {
        }

        public ErrorDetail(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return code + ": " + message;
        }
    }

    @Override
    public String toString() {
        return "UnityMessage{" +
                "type=" + type +
                ", operationId='" + operationId + '\'' +
                ", tool='" + tool + '\'' +
                ", success=" + success +
                '}';
    }
}
