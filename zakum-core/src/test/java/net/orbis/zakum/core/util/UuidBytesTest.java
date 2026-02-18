package net.orbis.zakum.core.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UuidBytes utility class.
 * 
 * Verifies:
 * - UUID to byte array conversion
 * - Byte array to UUID conversion
 * - Round-trip conversion
 * - Edge cases (null handling)
 */
class UuidBytesTest {

    @Test
    void testToBytesAndBack() {
        // Given: A random UUID
        UUID original = UUID.randomUUID();
        
        // When: Convert to bytes and back
        byte[] bytes = UuidBytes.toBytes(original);
        UUID restored = UuidBytes.fromBytes(bytes);
        
        // Then: Should match original
        assertEquals(original, restored);
    }

    @Test
    void testBytesLength() {
        // Given: Any UUID
        UUID uuid = UUID.randomUUID();
        
        // When: Convert to bytes
        byte[] bytes = UuidBytes.toBytes(uuid);
        
        // Then: Should be 16 bytes (128 bits)
        assertEquals(16, bytes.length);
    }

    @Test
    void testSpecificUuid() {
        // Given: A known UUID
        UUID uuid = new UUID(0x123456789ABCDEF0L, 0xFEDCBA9876543210L);
        
        // When: Convert to bytes and back
        byte[] bytes = UuidBytes.toBytes(uuid);
        UUID restored = UuidBytes.fromBytes(bytes);
        
        // Then: Should preserve the exact UUID
        assertEquals(uuid, restored);
        assertEquals(0x123456789ABCDEF0L, restored.getMostSignificantBits());
        assertEquals(0xFEDCBA9876543210L, restored.getLeastSignificantBits());
    }

    @Test
    void testNilUuid() {
        // Given: A nil UUID (all zeros)
        UUID nil = new UUID(0L, 0L);
        
        // When: Convert to bytes and back
        byte[] bytes = UuidBytes.toBytes(nil);
        UUID restored = UuidBytes.fromBytes(bytes);
        
        // Then: Should preserve nil UUID
        assertEquals(nil, restored);
        
        // And: Bytes should be all zeros
        for (byte b : bytes) {
            assertEquals(0, b);
        }
    }

    @Test
    void testMaxUuid() {
        // Given: A max UUID (all ones)
        UUID max = new UUID(-1L, -1L);
        
        // When: Convert to bytes and back
        byte[] bytes = UuidBytes.toBytes(max);
        UUID restored = UuidBytes.fromBytes(bytes);
        
        // Then: Should preserve max UUID
        assertEquals(max, restored);
        
        // And: Bytes should be all 0xFF
        for (byte b : bytes) {
            assertEquals((byte) 0xFF, b);
        }
    }

    @Test
    void testMultipleConversions() {
        // Given: Multiple UUIDs
        UUID[] uuids = {
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            new UUID(0L, 0L),
            new UUID(-1L, -1L)
        };
        
        // When/Then: Each should round-trip correctly
        for (UUID original : uuids) {
            byte[] bytes = UuidBytes.toBytes(original);
            UUID restored = UuidBytes.fromBytes(bytes);
            assertEquals(original, restored, 
                "Round-trip failed for UUID: " + original);
        }
    }

    @Test
    void testDeterministic() {
        // Given: The same UUID
        UUID uuid = UUID.randomUUID();
        
        // When: Convert multiple times
        byte[] bytes1 = UuidBytes.toBytes(uuid);
        byte[] bytes2 = UuidBytes.toBytes(uuid);
        
        // Then: Results should be identical
        assertArrayEquals(bytes1, bytes2);
    }

    @Test
    void testNullToBytes() {
        // Then: Converting null should throw
        assertThrows(NullPointerException.class, () -> UuidBytes.toBytes(null));
    }

    @Test
    void testNullFromBytes() {
        // Then: Converting null bytes should throw
        assertThrows(NullPointerException.class, () -> UuidBytes.fromBytes(null));
    }

    @Test
    void testInvalidBytesLength() {
        // Given: Invalid byte array lengths
        byte[] tooShort = new byte[15];
        byte[] tooLong = new byte[17];
        
        // Then: Should throw for invalid lengths
        assertThrows(IllegalArgumentException.class, () -> UuidBytes.fromBytes(tooShort));
        assertThrows(IllegalArgumentException.class, () -> UuidBytes.fromBytes(tooLong));
    }
}
