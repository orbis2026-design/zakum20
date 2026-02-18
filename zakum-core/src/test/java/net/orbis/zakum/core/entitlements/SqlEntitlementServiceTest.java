package net.orbis.zakum.core.entitlements;

import net.orbis.zakum.api.db.DatabaseState;
import net.orbis.zakum.api.db.Jdbc;
import net.orbis.zakum.api.db.ZakumDatabase;
import net.orbis.zakum.api.entitlements.EntitlementScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SqlEntitlementService.
 * 
 * Verifies:
 * - Cache hit/miss behavior
 * - Grant/revoke operations
 * - Expiration handling
 * - Concurrent access safety
 * - Database offline handling
 * - Cache invalidation
 */
class SqlEntitlementServiceTest {

    private SqlEntitlementService service;
    private MockDatabase mockDb;
    private Executor syncExecutor;

    @BeforeEach
    void setUp() {
        mockDb = new MockDatabase();
        syncExecutor = Runnable::run; // Execute synchronously for testing
        service = new SqlEntitlementService(mockDb, syncExecutor, 1000, Duration.ofMinutes(5));
    }

    @Test
    void testHasEntitlement_CacheMiss_DatabaseReturnsTrue() throws ExecutionException, InterruptedException {
        // Given: Database has the entitlement
        mockDb.shouldReturnEntitlement = true;
        UUID playerId = UUID.randomUUID();
        
        // When: Check entitlement
        boolean result = service.has(playerId, EntitlementScope.NETWORK, null, "vip").get();
        
        // Then: Should return true and query database
        assertTrue(result);
        assertEquals(1, mockDb.queryCount);
    }

    @Test
    void testHasEntitlement_CacheMiss_DatabaseReturnsFalse() throws ExecutionException, InterruptedException {
        // Given: Database does not have the entitlement
        mockDb.shouldReturnEntitlement = false;
        UUID playerId = UUID.randomUUID();
        
        // When: Check entitlement
        boolean result = service.has(playerId, EntitlementScope.NETWORK, null, "vip").get();
        
        // Then: Should return false
        assertFalse(result);
        assertEquals(1, mockDb.queryCount);
    }

    @Test
    void testHasEntitlement_CacheHit() throws ExecutionException, InterruptedException {
        // Given: First call populates cache
        mockDb.shouldReturnEntitlement = true;
        UUID playerId = UUID.randomUUID();
        service.has(playerId, EntitlementScope.NETWORK, null, "vip").get();
        assertEquals(1, mockDb.queryCount);
        
        // When: Check same entitlement again
        boolean result = service.has(playerId, EntitlementScope.NETWORK, null, "vip").get();
        
        // Then: Should return cached value without database query
        assertTrue(result);
        assertEquals(1, mockDb.queryCount, "Should not query database on cache hit");
    }

    @Test
    void testHasEntitlement_ServerScope() throws ExecutionException, InterruptedException {
        // Given: Server-scoped entitlement
        mockDb.shouldReturnEntitlement = true;
        UUID playerId = UUID.randomUUID();
        
        // When: Check with server scope
        boolean result = service.has(playerId, EntitlementScope.SERVER, "server-1", "fly").get();
        
        // Then: Should work correctly
        assertTrue(result);
        assertEquals(1, mockDb.queryCount);
    }

    @Test
    void testHasEntitlement_DatabaseOffline() throws ExecutionException, InterruptedException {
        // Given: Database is offline
        mockDb.state = DatabaseState.OFFLINE;
        UUID playerId = UUID.randomUUID();
        
        // When: Check entitlement
        boolean result = service.has(playerId, EntitlementScope.NETWORK, null, "vip").get();
        
        // Then: Should return false
        assertFalse(result);
        assertEquals(0, mockDb.queryCount, "Should not query when offline");
    }

    @Test
    void testGrant_Success() throws ExecutionException, InterruptedException {
        // Given: A player without entitlement
        UUID playerId = UUID.randomUUID();
        
        // When: Grant entitlement
        service.grant(playerId, EntitlementScope.NETWORK, null, "vip", null).get();
        
        // Then: Should update database
        assertEquals(1, mockDb.updateCount);
    }

    @Test
    void testGrant_WithExpiration() throws ExecutionException, InterruptedException {
        // Given: An expiration time
        UUID playerId = UUID.randomUUID();
        long expiresAt = System.currentTimeMillis() / 1000 + 86400; // +24 hours
        
        // When: Grant with expiration
        service.grant(playerId, EntitlementScope.NETWORK, null, "temp_boost", expiresAt).get();
        
        // Then: Should update database
        assertEquals(1, mockDb.updateCount);
    }

    @Test
    void testGrant_InvalidatesCache() throws ExecutionException, InterruptedException {
        // Given: Cached entitlement check (false)
        UUID playerId = UUID.randomUUID();
        mockDb.shouldReturnEntitlement = false;
        service.has(playerId, EntitlementScope.NETWORK, null, "vip").get();
        assertEquals(1, mockDb.queryCount);
        
        // When: Grant the entitlement
        service.grant(playerId, EntitlementScope.NETWORK, null, "vip", null).get();
        
        // And: Check again
        mockDb.shouldReturnEntitlement = true; // Now it exists
        boolean result = service.has(playerId, EntitlementScope.NETWORK, null, "vip").get();
        
        // Then: Should query database again (cache invalidated)
        assertTrue(result);
        assertEquals(2, mockDb.queryCount, "Cache should be invalidated after grant");
    }

    @Test
    void testRevoke_Success() throws ExecutionException, InterruptedException {
        // Given: A player with entitlement
        UUID playerId = UUID.randomUUID();
        
        // When: Revoke entitlement
        service.revoke(playerId, EntitlementScope.NETWORK, null, "vip").get();
        
        // Then: Should update database
        assertEquals(1, mockDb.updateCount);
    }

    @Test
    void testRevoke_InvalidatesCache() throws ExecutionException, InterruptedException {
        // Given: Cached entitlement check (true)
        UUID playerId = UUID.randomUUID();
        mockDb.shouldReturnEntitlement = true;
        service.has(playerId, EntitlementScope.NETWORK, null, "vip").get();
        assertEquals(1, mockDb.queryCount);
        
        // When: Revoke the entitlement
        service.revoke(playerId, EntitlementScope.NETWORK, null, "vip").get();
        
        // And: Check again
        mockDb.shouldReturnEntitlement = false; // Now it's gone
        boolean result = service.has(playerId, EntitlementScope.NETWORK, null, "vip").get();
        
        // Then: Should query database again (cache invalidated)
        assertFalse(result);
        assertEquals(2, mockDb.queryCount, "Cache should be invalidated after revoke");
    }

    @Test
    void testInvalidate_ClearsAllPlayerEntitlements() throws ExecutionException, InterruptedException {
        // Given: Multiple cached entitlements for a player
        UUID playerId = UUID.randomUUID();
        mockDb.shouldReturnEntitlement = true;
        
        service.has(playerId, EntitlementScope.NETWORK, null, "vip").get();
        service.has(playerId, EntitlementScope.SERVER, "server-1", "fly").get();
        assertEquals(2, mockDb.queryCount);
        
        // When: Invalidate player cache
        service.invalidate(playerId);
        
        // And: Check entitlements again
        service.has(playerId, EntitlementScope.NETWORK, null, "vip").get();
        service.has(playerId, EntitlementScope.SERVER, "server-1", "fly").get();
        
        // Then: Should query database again for all
        assertEquals(4, mockDb.queryCount, "All player entitlements should be invalidated");
    }

    @Test
    void testInvalidate_OnlyAffectsSpecificPlayer() throws ExecutionException, InterruptedException {
        // Given: Cached entitlements for two players
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        mockDb.shouldReturnEntitlement = true;
        
        service.has(player1, EntitlementScope.NETWORK, null, "vip").get();
        service.has(player2, EntitlementScope.NETWORK, null, "vip").get();
        assertEquals(2, mockDb.queryCount);
        
        // When: Invalidate only player1
        service.invalidate(player1);
        
        // And: Check both players again
        service.has(player1, EntitlementScope.NETWORK, null, "vip").get();
        service.has(player2, EntitlementScope.NETWORK, null, "vip").get();
        
        // Then: Only player1 should query database again
        assertEquals(3, mockDb.queryCount, "Only invalidated player should query database");
    }

    @Test
    void testConcurrentAccess() throws InterruptedException, ExecutionException {
        // Given: Multiple threads checking entitlements
        UUID playerId = UUID.randomUUID();
        mockDb.shouldReturnEntitlement = true;
        
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Boolean>> futures = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // When: Multiple threads check simultaneously
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    return service.has(playerId, EntitlementScope.NETWORK, null, "vip").get();
                } finally {
                    latch.countDown();
                }
            }));
        }
        
        // Then: All should complete successfully
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        for (Future<Boolean> future : futures) {
            assertTrue(future.get());
        }
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testNullPlayerIdThrows() {
        // Then: Null playerId should throw
        assertThrows(NullPointerException.class, () -> 
            service.has(null, EntitlementScope.NETWORK, null, "vip"));
    }

    @Test
    void testNullScopeThrows() {
        // Then: Null scope should throw
        assertThrows(NullPointerException.class, () -> 
            service.has(UUID.randomUUID(), null, null, "vip"));
    }

    @Test
    void testNullEntitlementKeyThrows() {
        // Then: Null entitlement key should throw
        assertThrows(NullPointerException.class, () -> 
            service.has(UUID.randomUUID(), EntitlementScope.NETWORK, null, null));
    }

    @Test
    void testServerScopeRequiresServerId() {
        // Then: SERVER scope without serverId should throw
        assertThrows(NullPointerException.class, () -> 
            service.has(UUID.randomUUID(), EntitlementScope.SERVER, null, "fly"));
    }

    /**
     * Mock database for testing
     */
    private static class MockDatabase implements ZakumDatabase {
        DatabaseState state = DatabaseState.ONLINE;
        boolean shouldReturnEntitlement = false;
        int queryCount = 0;
        int updateCount = 0;
        
        @Override
        public DatabaseState state() {
            return state;
        }

        @Override
        public javax.sql.DataSource dataSource() {
            return null;
        }

        @Override
        public Jdbc jdbc() {
            return new MockJdbc();
        }

        private class MockJdbc implements Jdbc {
            @Override
            public <T> List<T> query(String sql, Jdbc.RowMapper<T> mapper, Object... params) {
                queryCount++;
                if (shouldReturnEntitlement) {
                    @SuppressWarnings("unchecked")
                    T result = (T) Integer.valueOf(1);
                    return List.of(result);
                }
                return List.of();
            }

            @Override
            public int update(String sql, Object... params) {
                updateCount++;
                return 1;
            }

            @Override
            public <T> T queryOne(String sql, Jdbc.RowMapper<T> mapper, Object... params) {
                var results = query(sql, mapper, params);
                return results.isEmpty() ? null : results.get(0);
            }
        }
    }
}
