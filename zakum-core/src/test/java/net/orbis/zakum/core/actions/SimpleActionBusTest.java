package net.orbis.zakum.core.actions;

import net.orbis.zakum.api.actions.ActionEvent;
import net.orbis.zakum.api.actions.ActionHandler;
import net.orbis.zakum.api.actions.ActionSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SimpleActionBus implementation.
 * 
 * Verifies:
 * - Basic publish/subscribe functionality
 * - Multiple subscribers
 * - Subscription cancellation
 * - Concurrent access safety
 * - Event data integrity
 */
class SimpleActionBusTest {

    private SimpleActionBus bus;

    @BeforeEach
    void setUp() {
        bus = new SimpleActionBus();
    }

    @Test
    void testBasicPublishSubscribe() {
        // Given: A handler that captures events
        List<ActionEvent> captured = new ArrayList<>();
        ActionHandler handler = captured::add;
        
        // When: Subscribe and publish an event
        bus.subscribe(handler);
        ActionEvent event = createTestEvent("test_action");
        bus.publish(event);
        
        // Then: Handler should receive the event
        assertEquals(1, captured.size());
        assertSame(event, captured.get(0));
    }

    @Test
    void testMultipleSubscribers() {
        // Given: Multiple handlers
        AtomicInteger count1 = new AtomicInteger();
        AtomicInteger count2 = new AtomicInteger();
        
        bus.subscribe(e -> count1.incrementAndGet());
        bus.subscribe(e -> count2.incrementAndGet());
        
        // When: Publish an event
        bus.publish(createTestEvent("test"));
        
        // Then: All handlers should be called
        assertEquals(1, count1.get());
        assertEquals(1, count2.get());
    }

    @Test
    void testUnsubscribe() {
        // Given: A subscribed handler
        AtomicInteger count = new AtomicInteger();
        ActionSubscription subscription = bus.subscribe(e -> count.incrementAndGet());
        
        // When: Publish, unsubscribe, then publish again
        bus.publish(createTestEvent("test1"));
        assertEquals(1, count.get());
        
        subscription.close();
        bus.publish(createTestEvent("test2"));
        
        // Then: Second event should not be received
        assertEquals(1, count.get(), "Handler should not receive events after unsubscribe");
    }

    @Test
    void testMultiplePublishEvents() {
        // Given: A handler that counts events
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(e -> count.incrementAndGet());
        
        // When: Publish multiple events
        for (int i = 0; i < 5; i++) {
            bus.publish(createTestEvent("test_" + i));
        }
        
        // Then: All events should be received
        assertEquals(5, count.get());
    }

    @Test
    void testEventDataIntegrity() {
        // Given: A handler that captures event details
        List<String> capturedActions = new ArrayList<>();
        List<UUID> capturedPlayers = new ArrayList<>();
        
        bus.subscribe(e -> {
            capturedActions.add(e.type());
            capturedPlayers.add(e.playerId());
        });
        
        // When: Publish events with specific data
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        
        bus.publish(new ActionEvent("action1", player1, 1L, null, null));
        bus.publish(new ActionEvent("action2", player2, 1L, null, null));
        
        // Then: Data should be preserved
        assertEquals(List.of("action1", "action2"), capturedActions);
        assertEquals(List.of(player1, player2), capturedPlayers);
    }

    @Test
    void testConcurrentPublish() throws InterruptedException {
        // Given: A handler with a counter
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(e -> count.incrementAndGet());
        
        // When: Multiple threads publish simultaneously
        int threadCount = 10;
        int eventsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    bus.publish(createTestEvent("thread_" + threadId + "_event_" + j));
                }
                latch.countDown();
            }).start();
        }
        
        // Then: All events should be received
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(threadCount * eventsPerThread, count.get());
    }

    @Test
    void testSubscribeDuringPublish() {
        // Given: A handler that subscribes another handler
        AtomicInteger count1 = new AtomicInteger();
        AtomicInteger count2 = new AtomicInteger();
        
        bus.subscribe(e -> {
            count1.incrementAndGet();
            // Subscribe a new handler during event processing
            bus.subscribe(ev -> count2.incrementAndGet());
        });
        
        // When: Publish events
        bus.publish(createTestEvent("test1"));
        bus.publish(createTestEvent("test2"));
        
        // Then: First handler gets both, second only gets the second
        assertEquals(2, count1.get());
        assertEquals(1, count2.get());
    }

    @Test
    void testNullEventThrows() {
        // Then: Publishing null should throw
        assertThrows(NullPointerException.class, () -> bus.publish(null));
    }

    @Test
    void testNullHandlerThrows() {
        // Then: Subscribing null should throw
        assertThrows(NullPointerException.class, () -> bus.subscribe(null));
    }

    @Test
    void testDoubleUnsubscribe() {
        // Given: A subscription
        ActionSubscription subscription = bus.subscribe(e -> {});
        
        // When: Close twice
        subscription.close();
        
        // Then: Should not throw
        assertDoesNotThrow(() -> subscription.close());
    }

    /**
     * Helper to create test events
     */
    private ActionEvent createTestEvent(String action) {
        return new ActionEvent(
            action,
            UUID.randomUUID(),
            1L,
            null,
            null
        );
    }
}
