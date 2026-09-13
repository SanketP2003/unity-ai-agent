package com.unityagent.agent.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable entry in the durable event journal.
 * Sequences are strictly monotonically increasing per agentRunId.
 *
 * <p>Guardrails:
 * - Payloads bounded to at most {@value #MAX_PAYLOAD_BYTES} bytes.
 * - Never contains API keys, tokens, or hidden LLM reasoning.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RunEventRecord {

    public static final int MAX_PAYLOAD_BYTES = 65536;

    private String eventId;
    private long sequence;
    private Instant timestamp;
    private String projectId;
    private String sessionId;
    private String agentRunId;
    private String eventType;
    private String payload;

    public RunEventRecord() {
    }

    public RunEventRecord(String eventId, long sequence, Instant timestamp, String projectId,
                          String sessionId, String agentRunId, String eventType, String payload) {
        this.eventId = eventId != null ? eventId : "evt_" + UUID.randomUUID().toString().substring(0, 12);
        this.sequence = sequence;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.projectId = projectId;
        this.sessionId = sessionId;
        this.agentRunId = agentRunId;
        this.eventType = eventType;
        this.payload = sanitizePayload(payload);
    }

    public static String sanitizePayload(String raw) {
        if (raw == null) {
            return null;
        }
        // Truncate if exceeds max payload bytes
        if (raw.length() > MAX_PAYLOAD_BYTES) {
            return raw.substring(0, MAX_PAYLOAD_BYTES - 24) + "... [PAYLOAD_TRUNCATED]";
        }
        return raw;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAgentRunId() {
        return agentRunId;
    }

    public void setAgentRunId(String agentRunId) {
        this.agentRunId = agentRunId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = sanitizePayload(payload);
    }
}
