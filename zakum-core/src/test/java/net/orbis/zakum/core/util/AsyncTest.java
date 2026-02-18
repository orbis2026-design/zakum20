package net.orbis.zakum.core.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Async executor utilities.
 * 
 * Verifies:
 * - Thread pool creation
 * - Task execution
 * - Uncaught exception handling
 * - Proper shutdown behavior
 * - Virtual thread usage (Java 21)
 */
class AsyncTest {

    @Test
    void testNewSharedPool_CreatesExecutor() {
        // Given: A logger
        Logger log = Logger.getLogger("test");
        
        // When: Create shared pool
        ExecutorService executor = Async.newSharedPool(log);
        
        // Then: Should create valid executor
        assertNotNull(executor);
        assertFalse(executor.isShutdown());
        
        // Cleanup
        executor.shutdown();
    }

    @Test
    void testExecutorSubmitsTask() throws ExecutionException, InterruptedException, TimeoutException {
        // Given: A shared pool
        Logger log = Logger.getLogger("test");
        ExecutorService executor = Async.newSharedPool(log);
        
        try {
            // When: Submit a task
            Future<String> future = executor.submit(() -> "test-result");
            
            // Then: Task should complete
            String result = future.get(1, TimeUnit.SECONDS);
            assertEquals("test-result", result);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testConcurrentTasks() throws InterruptedException {
        // Given: A shared pool and multiple tasks
        Logger log = Logger.getLogger("test");
        ExecutorService executor = Async.newSharedPool(log);
        
        try {
            AtomicInteger counter = new AtomicInteger();
            int taskCount = 100;
            CountDownLatch latch = new CountDownLatch(taskCount);
            
            // When: Submit many concurrent tasks
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    counter.incrementAndGet();
                    latch.countDown();
                });
            }
            
            // Then: All tasks should complete
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(taskCount, counter.get());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testShutdown() throws InterruptedException {
        // Given: A running executor
        Logger log = Logger.getLogger("test");
        ExecutorService executor = Async.newSharedPool(log);
        
        // When: Shutdown
        executor.shutdown();
        
        // Then: Should shutdown gracefully
        assertTrue(executor.isShutdown());
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }

    @Test
    void testShutdownNow() throws InterruptedException {
        // Given: A running executor with pending tasks
        Logger log = Logger.getLogger("test");
        ExecutorService executor = Async.newSharedPool(log);
        
        // Submit a long-running task
        executor.submit(() -> {
            try {
                Thread.sleep(10000); // 10 seconds
            } catch (InterruptedException e) {
                // Expected
            }
        });
        
        // When: Shutdown immediately
        executor.shutdownNow();
        
        // Then: Should interrupt and shutdown
        assertTrue(executor.isShutdown());
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }

    @Test
    void testUncaughtException() throws InterruptedException {
        // Given: A shared pool with exception handler
        Logger log = Logger.getLogger("test");
        ExecutorService executor = Async.newSharedPool(log);
        
        try {
            // When: Submit task that throws exception
            CountDownLatch latch = new CountDownLatch(1);
            executor.submit(() -> {
                try {
                    throw new RuntimeException("Test exception");
                } finally {
                    latch.countDown();
                }
            });
            
            // Then: Exception should be caught by handler (not crash executor)
            assertTrue(latch.await(1, TimeUnit.SECONDS));
            
            // Executor should still be functional
            Future<String> future = executor.submit(() -> "still-working");
            assertEquals("still-working", future.get(1, TimeUnit.SECONDS));
        } catch (ExecutionException | TimeoutException e) {
            fail("Executor should handle exceptions gracefully");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testMultiplePoolsIndependent() {
        // Given: Multiple pools
        Logger log = Logger.getLogger("test");
        ExecutorService executor1 = Async.newSharedPool(log);
        ExecutorService executor2 = Async.newSharedPool(log);
        
        try {
            // Then: Should be independent instances
            assertNotSame(executor1, executor2);
            
            // Shutdown one
            executor1.shutdown();
            assertTrue(executor1.isShutdown());
            assertFalse(executor2.isShutdown());
        } finally {
            executor1.shutdown();
            executor2.shutdown();
        }
    }

    @Test
    void testVirtualThreadsUsed() throws ExecutionException, InterruptedException, TimeoutException {
        // Given: A shared pool (uses virtual threads in Java 21)
        Logger log = Logger.getLogger("test");
        ExecutorService executor = Async.newSharedPool(log);
        
        try {
            // When: Submit task and get thread name
            Future<String> future = executor.submit(() -> Thread.currentThread().getName());
            String threadName = future.get(1, TimeUnit.SECONDS);
            
            // Then: Should use virtual thread naming pattern
            assertTrue(threadName.startsWith("zakum-vt-"),
                "Thread name should start with 'zakum-vt-', got: " + threadName);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testHighThroughput() throws InterruptedException {
        // Given: A shared pool
        Logger log = Logger.getLogger("test");
        ExecutorService executor = Async.newSharedPool(log);
        
        try {
            // When: Submit many tasks quickly
            int taskCount = 10000;
            CountDownLatch latch = new CountDownLatch(taskCount);
            
            long start = System.currentTimeMillis();
            for (int i = 0; i < taskCount; i++) {
                executor.submit(latch::countDown);
            }
            
            // Then: Should handle high throughput
            assertTrue(latch.await(10, TimeUnit.SECONDS));
            long duration = System.currentTimeMillis() - start;
            assertTrue(duration < 10000, "Should complete in reasonable time: " + duration + "ms");
        } finally {
            executor.shutdown();
        }
    }
}
