package com.unityagent.agent.identity;

import java.util.Objects;

/**
 * Abstraction separating the stable AI-facing object identifier from the
 * transient Unity engine/editor InstanceID.
 *
 * <p><b>CRITICAL ARCHITECTURAL NOTE:</b>
 * Unity's native {@code InstanceID} (from {@code GameObject.GetInstanceID()}) is
 * transient and session-scoped in memory. It changes when Unity restarts, when scenes
 * are reloaded, or when objects are recreated.
 *
 * <p>This abstraction ensures that the AgentLoop, ProjectMemory, and reasoning layers
 * depend solely on the logical {@code objectId} (e.g. {@code "obj_123"}), allowing
 * a fully persistent GUID system or asset identifier to be introduced in future phases
 * without refactoring the agent reasoning architecture.
 */
public class UnityObjectIdentity {

    private final String objectId;
    private final String name;
    private final String scene;
    private final Integer transientInstanceId;

    public UnityObjectIdentity(String objectId, String name, String scene, Integer transientInstanceId) {
        this.objectId = Objects.requireNonNull(objectId, "objectId cannot be null");
        this.name = name != null ? name : "";
        this.scene = scene != null ? scene : "";
        this.transientInstanceId = transientInstanceId;
    }

    public static UnityObjectIdentity of(String objectId, String name, String scene) {
        Integer parsedInstanceId = parseTransientInstanceId(objectId);
        return new UnityObjectIdentity(objectId, name, scene, parsedInstanceId);
    }

    public static UnityObjectIdentity fromTransientInstanceId(int instanceId, String name, String scene) {
        return new UnityObjectIdentity("obj_" + instanceId, name, scene, instanceId);
    }

    public static Integer parseTransientInstanceId(String objectId) {
        if (objectId == null || objectId.isBlank()) return null;
        if (objectId.startsWith("obj_")) {
            try {
                return Integer.parseInt(objectId.substring(4));
            } catch (NumberFormatException ignored) {
            }
        }
        try {
            return Integer.parseInt(objectId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public String getObjectId() {
        return objectId;
    }

    public String getName() {
        return name;
    }

    public String getScene() {
        return scene;
    }

    /**
     * @return transient engine instance ID, or null if not an instance-ID based identity
     */
    public Integer getTransientInstanceId() {
        return transientInstanceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnityObjectIdentity that = (UnityObjectIdentity) o;
        return Objects.equals(objectId, that.objectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectId);
    }

    @Override
    public String toString() {
        return "UnityObjectIdentity{" +
                "objectId='" + objectId + '\'' +
                ", name='" + name + '\'' +
                ", scene='" + scene + '\'' +
                ", transientInstanceId=" + transientInstanceId +
                '}';
    }
}
