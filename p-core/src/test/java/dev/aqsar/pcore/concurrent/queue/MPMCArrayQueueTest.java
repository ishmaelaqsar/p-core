package dev.aqsar.pcore.concurrent.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MPMCArrayQueue}.
 */
class MPMCArrayQueueTest {

    private static final int TEST_CAPACITY = 1024;
    private MPMCArrayQueue<Integer> queue;

    @BeforeEach
    void setup() {
        // The MPMCArrayQueue rounds up to the next power of two
        queue = new MPMCArrayQueue<>(TEST_CAPACITY);
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
        assertEquals(0, queue.poll());
    }

    @Test
    void testFillFull() {
        int fillCount = queue.fill(() -> 1, TEST_CAPACITY);
        assertEquals(TEST_CAPACITY, fillCount);
        assertEquals(TEST_CAPACITY, queue.size());

        int secondFill = queue.fill(() -> 2, 100);
        assertEquals(0, secondFill);
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
    void stressTestMPMC_DataIntegrity() throws Exception {
        final MPMCArrayQueue<Integer> stressQueue = new MPMCArrayQueue<>(1 << 18); // 256k
        final int numProducers = 4;
        final int numConsumers = 4;
        final int messagesPerProducer = 1_000_000;
        final int totalMessages = numProducers * messagesPerProducer;

        ExecutorService exec = Executors.newFixedThreadPool(numProducers + numConsumers);
        CountDownLatch producersDone = new CountDownLatch(numProducers);

        final ConcurrentHashMap<Integer, Boolean> receivedSet = new ConcurrentHashMap<>(totalMessages);

        // --- CONSUMERS ---
        for (int i = 0; i < numConsumers; i++) {
            exec.submit(() -> {
                Thread.currentThread().setName("CONSUMER-" + Thread.currentThread().getId());

                // The while loop's condition is the *only* exit.
                // receivedSet is concurrent, so its size() will
                // eventually be visible to all threads.
                while (receivedSet.size() < totalMessages) {
                    Integer val = stressQueue.poll();
                    if (val != null) {
                        receivedSet.put(val, Boolean.TRUE);
                    } else {
                        // We must spin if poll returns null,
                        // as other consumers/producers might be active.
                        Thread.onSpinWait();
                    }
                }
            });
        }

        // --- PRODUCERS ---
        for (int i = 0; i < numProducers; i++) {
            final int producerId = i;
            exec.submit(() -> {
                Thread.currentThread().setName("PRODUCER-" + producerId);
                final int baseValue = producerId * messagesPerProducer;
                for (int j = 0; j < messagesPerProducer; j++) {
                    int value = baseValue + j;
                    while (!stressQueue.offer(value)) {
                        Thread.onSpinWait();
                    }
                }
                producersDone.countDown();
            });
        }

        // --- MAIN THREAD (Test) ---
        // 1. Wait for producers to finish
        assertTrue(producersDone.await(20, TimeUnit.SECONDS), "Producers timed out");

        // 2. Wait for consumers to drain the queue (with stall detection)
        long startTime = System.currentTimeMillis();
        while (receivedSet.size() < totalMessages) {
            if (System.currentTimeMillis() - startTime > 5000) {
                // If we've stalled, check if the queue is *really* empty
                // or if the consumers are just slow.
                if (stressQueue.isEmpty()) {
                    fail("Consumers stalled with an empty queue. Expected: " + totalMessages + " Got: " +
                         receivedSet.size());
                }
                // Reset timer and keep waiting
                startTime = System.currentTimeMillis();
            }
            Thread.sleep(100);
        }

        // 3. Final check
        assertEquals(totalMessages, receivedSet.size());
        exec.shutdownNow();
    }
}
