package com.unityagent.agent;

import com.unityagent.agent.identity.UnityObjectIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnityObjectIdentity Tests")
class UnityObjectIdentityTest {

    @Test
    @DisplayName("Should create identity from transient instance ID")
    void testFromTransientInstanceId() {
        UnityObjectIdentity identity = UnityObjectIdentity.fromTransientInstanceId(12345, "Player", "SampleScene");

        assertEquals("obj_12345", identity.getObjectId());
        assertEquals("Player", identity.getName());
        assertEquals("SampleScene", identity.getScene());
        assertEquals(12345, identity.getTransientInstanceId());
    }

    @Test
    @DisplayName("Should create identity from logical objectId string and parse transient instance ID")
    void testOfWithPrefixedObjectId() {
        UnityObjectIdentity identity = UnityObjectIdentity.of("obj_67890", "Ground", "MainScene");

        assertEquals("obj_67890", identity.getObjectId());
        assertEquals("Ground", identity.getName());
        assertEquals("MainScene", identity.getScene());
        assertEquals(67890, identity.getTransientInstanceId());
    }

    @Test
    @DisplayName("Should support non-instance-id based objectId for future GUID abstraction")
    void testOfWithGuidObjectId() {
        String guidId = "guid_a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        UnityObjectIdentity identity = UnityObjectIdentity.of(guidId, "MainCamera", "MainScene");

        assertEquals(guidId, identity.getObjectId());
        assertEquals("MainCamera", identity.getName());
        assertNull(identity.getTransientInstanceId(), "Future GUID identities should have null transientInstanceId");
    }

    @Test
    @DisplayName("Should parse transient instance IDs safely")
    void testParseTransientInstanceId() {
        assertEquals(42, UnityObjectIdentity.parseTransientInstanceId("obj_42"));
        assertEquals(100, UnityObjectIdentity.parseTransientInstanceId("100"));
        assertNull(UnityObjectIdentity.parseTransientInstanceId(null));
        assertNull(UnityObjectIdentity.parseTransientInstanceId(""));
        assertNull(UnityObjectIdentity.parseTransientInstanceId("   "));
        assertNull(UnityObjectIdentity.parseTransientInstanceId("invalid_id"));
        assertNull(UnityObjectIdentity.parseTransientInstanceId("obj_not_a_number"));
    }

    @Test
    @DisplayName("Should test equality based on logical objectId")
    void testEqualityAndHashCode() {
        UnityObjectIdentity id1 = UnityObjectIdentity.of("obj_100", "OldName", "Scene1");
        UnityObjectIdentity id2 = UnityObjectIdentity.of("obj_100", "NewName", "Scene2");
        UnityObjectIdentity id3 = UnityObjectIdentity.of("obj_200", "OldName", "Scene1");

        assertEquals(id1, id2, "Equal objectId implies equality regardless of name/scene changes");
        assertNotEquals(id1, id3);
        assertEquals(id1.hashCode(), id2.hashCode());
    }
}
