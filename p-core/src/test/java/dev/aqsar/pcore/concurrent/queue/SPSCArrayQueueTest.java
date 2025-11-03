package dev.aqsar.pcore.concurrent.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SPSCArrayQueue}.
 */
class SPSCArrayQueueTest {

    private static final int TEST_CAPACITY = 1024;
    private SPSCArrayQueue<Integer> queue;

    @BeforeEach
    void setup() {
        // The SPSCArrayQueue rounds up to the next power of two
        queue = new SPSCArrayQueue<>(TEST_CAPACITY);
        assertEquals(TEST_CAPACITY, queue.capacity());
    }

    @Test
    void testOfferAndPollSingle() {
        assertTrue(queue.offer(123));
        assertEquals(1, queue.size());
        assertFalse(queue.isEmpty());

        Integer val = queue.poll();
        assertEquals(123, val);
        assertEquals(0, queue.size());
        assertTrue(queue.isEmpty());
    }

    @Test
    void testPollEmpty() {
        assertNull(queue.poll());
    }

    @Test
    void testOfferFull() {
        for (int i = 0; i < TEST_CAPACITY; i++) {
            assertTrue(queue.offer(i), "Failed to offer message #" + i);
        }
        assertEquals(TEST_CAPACITY, queue.size());

        // Queue is full, next offer should fail
        assertFalse(queue.offer(9999));
    }

    @Test
    void testOfferNull() {
        assertThrows(NullPointerException.class, () -> queue.offer(null));
    }

    @Test
    void testDrain() {
        for (int i = 0; i < 100; i++) {
            queue.offer(i);
        }
        assertEquals(100, queue.size());

        List<Integer> drained = new ArrayList<>();
        int drainCount = queue.drain(drained::add);

        assertEquals(100, drainCount);
        assertEquals(100, drained.size());
        assertEquals(0, queue.size());
        assertEquals(50, drained.get(50));
    }

    @Test
    void testDrainWithLimit() {
        for (int i = 0; i < 100; i++) {
            queue.offer(i);
        }
        assertEquals(100, queue.size());

        List<Integer> drained = new ArrayList<>();
        int drainCount = queue.drain(drained::add, 40);

        assertEquals(40, drainCount);
        assertEquals(40, drained.size());
        assertEquals(60, queue.size());
        assertEquals(39, drained.get(39));
    }

    @Test
    void testDrainEmpty() {
        int drainCount = queue.drain(x -> fail("Should not be called"));
        assertEquals(0, drainCount);
    }

    @Test
    void testFill() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<Integer> supplier = counter::getAndIncrement;

        int fillCount = queue.fill(supplier, 500);

        assertEquals(500, fillCount);
        assertEquals(500, queue.size());

        // Now, poll all items and verify their order
        for (int i = 0; i < 500; i++) {
            Integer val = queue.poll();
            assertNotNull(val);
            assertEquals(i, val, "Item at index " + i + " was incorrect");
        }

        assertTrue(queue.isEmpty());
    }

    @Test
    void testFillFull() {
        // Fill queue to capacity
        int fillCount = queue.fill(() -> 1, TEST_CAPACITY);
        assertEquals(TEST_CAPACITY, fillCount);
        assertEquals(TEST_CAPACITY, queue.size());

        // Try to fill more
        int secondFill = queue.fill(() -> 2, 100);
        assertEquals(0, secondFill);
    }

    @Test
    void testFillWithLimit() {
        // Fill all but 10 slots
        int fillCount = queue.fill(() -> 1, TEST_CAPACITY - 10);
        assertEquals(TEST_CAPACITY - 10, fillCount);
        assertEquals(TEST_CAPACITY - 10, queue.size());

        // Try to add 20 items, only 10 should fit
        int secondFill = queue.fill(() -> 2, 20);
        assertEquals(10, secondFill);
        assertEquals(TEST_CAPACITY, queue.size());
    }

    @Test
    void testBasicUtilities() {
        assertEquals(TEST_CAPACITY, queue.capacity());
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());

        queue.offer(1);
        assertFalse(queue.isEmpty());
        assertEquals(1, queue.size());

        queue.poll();
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void testClear() {
        for (int i = 0; i < 100; i++) {
            queue.offer(i);
        }
        assertEquals(100, queue.size());

        queue.clear();
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
        assertNull(queue.poll());
    }

    @Test
    void stressTestSPSC_OfferPoll() throws Exception {
        final SPSCArrayQueue<Long> stressQueue = new SPSCArrayQueue<>(1 << 18); // 256k
        final int messages = 10_000_000;

        ExecutorService exec = Executors.newFixedThreadPool(2);
        AtomicLong receivedCount = new AtomicLong(0);
        AtomicLong expectedSeq = new AtomicLong(0);
        AtomicBoolean dataError = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);

        // CONSUMER
        exec.submit(() -> {
            Thread.currentThread().setName("CONSUMER");
            while (receivedCount.get() < messages) {
                Long val = stressQueue.poll();
                if (val != null) {
                    long expected = expectedSeq.getAndIncrement();
                    if (val != expected) {
                        System.err.printf("Data error! Expected: %d, Got: %d%n", expected, val);
                        dataError.set(true);
                    }
                    receivedCount.incrementAndGet();
                } else {
                    Thread.onSpinWait();
                }
            }
            done.countDown();
        });

        // PRODUCER
        exec.submit(() -> {
            Thread.currentThread().setName("PRODUCER");
            for (long i = 0; i < messages; i++) {
                while (!stressQueue.offer(i)) {
                    Thread.onSpinWait();
                }
            }
        });

        assertTrue(done.await(15, TimeUnit.SECONDS), "Test timed out");
        assertFalse(dataError.get(), "Data integrity error detected");
        assertEquals(messages, receivedCount.get());

        exec.shutdownNow();
    }

    @Test
    void stressTestSPSC_FillDrain() throws Exception {
        final SPSCArrayQueue<Long> stressQueue = new SPSCArrayQueue<>(1 << 18); // 256k
        final int messages = 10_000_000;
        final int batchSize = 256;

        ExecutorService exec = Executors.newFixedThreadPool(2);
        AtomicLong receivedCount = new AtomicLong(0);
        AtomicLong producedCount = new AtomicLong(0); // This will be the value supplier
        AtomicLong expectedSeq = new AtomicLong(0);
        AtomicBoolean dataError = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);

        // CONSUMER
        exec.submit(() -> {
            Thread.currentThread().setName("CONSUMER-DRAIN");
            final AtomicLong localReceived = new AtomicLong(0);
            while (localReceived.get() < messages) {
                int n = stressQueue.drain(val -> {
                    long expected = expectedSeq.getAndIncrement();
                    if (val != expected) {
                        dataError.set(true);
                    }
                    localReceived.incrementAndGet();
                }, batchSize);

                if (n == 0) {
                    Thread.onSpinWait();
                }
            }
            receivedCount.set(localReceived.get());
            done.countDown();
        });

        // PRODUCER
        exec.submit(() -> {
            Thread.currentThread().setName("PRODUCER-FILL");
            final Supplier<Long> supplier = producedCount::getAndIncrement;

            while (true) {
                long currentProduced = producedCount.get();
                if (currentProduced >= messages) {
                    break; // We're done
                }

                // Calculate remaining items and cap the batch size
                long remaining = messages - currentProduced;
                int batch = (int) Math.min(remaining, batchSize);

                if (batch <= 0) {
                    break; // Should be covered by the check above
                }

                // 'n' is the number of items *actually* filled
                int n = stressQueue.fill(supplier, batch);
                if (n == 0) {
                    Thread.onSpinWait();
                }
            }
        });

        assertTrue(done.await(15, TimeUnit.SECONDS), "Test timed out");
        assertFalse(dataError.get(), "Data integrity error detected");
        assertEquals(messages, receivedCount.get());

        assertEquals(messages, producedCount.get());

        exec.shutdownNow();
    }
}
