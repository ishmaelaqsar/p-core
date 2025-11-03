package dev.aqsar.pcore.concurrent;

import dev.aqsar.pcore.concurrent.ringbuffer.MPMCRingBuffer;
import dev.aqsar.pcore.concurrent.ringbuffer.RingBuffer;
import dev.aqsar.pcore.concurrent.ringbuffer.RingBufferAllocator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MPMCRingBuffer}.
 */
class MPMCRingBufferTest {

    private static final int TEST_CAPACITY = 1024;
    private MPMCRingBuffer ring;
    private ByteBuffer testPayload;

    @BeforeEach
    void setup() {
        ring = new MPMCRingBuffer(RingBufferAllocator.allocate(TEST_CAPACITY));
        testPayload = ByteBuffer.allocateDirect(128).order(ByteOrder.nativeOrder());
        for (int i = 0; i < 128; i++) {
            testPayload.put(i, (byte) i);
        }
    }

    /**
     * Verifies the basic offer/poll mechanism with data validation.
     */
    @Test
    void testOfferAndPollSingle() {
        assertTrue(ring.offer(1, testPayload, 0, 32));

        AtomicLong sum = new AtomicLong();
        int read = ring.poll((typeId, buf) -> {
            assertEquals(1, typeId);
            assertEquals(32, buf.length());
            for (int i = 0; i < buf.length(); i++) {
                sum.addAndGet(buf.getByte(i));
            }
        }, 1);

        assertEquals(1, read);
        assertEquals(496, sum.get()); // Sum of 0..31
        assertEquals(0, ring.utilization());
    }

    /**
     * Tests that poll returns 0 on an empty buffer.
     */
    @Test
    void testPollEmpty() {
        int read = ring.poll((id, buf) -> fail("Should not be called"));
        assertEquals(0, read);
    }

    /**
     * Tests that offer returns false on a full buffer.
     */
    @Test
    void testOfferFull() {
        int msgSize = 128;
        int msgWithHeader = msgSize + 8; // 8 is RECORD_HEADER_SIZE
        int maxMessages = TEST_CAPACITY / msgWithHeader;

        for (int i = 0; i < maxMessages; i++) {
            assertTrue(ring.offer(1, testPayload, 0, msgSize), "Failed to offer message #" + i);
        }

        assertFalse(ring.offer(1, testPayload, 0, msgSize));
    }

    /**
     * Tests the zero-copy claim/publish API path.
     */
    @Test
    void testClaimPublish() {
        int len = 64;
        int payloadOffset = ring.claim(2, len);
        assertTrue(payloadOffset >= 0, "Claim failed");

        ByteBuffer underlying = ring.underlyingBuffer();
        for (int i = 0; i < len; i++) {
            underlying.put(payloadOffset + i, (byte) (i + 1));
        }

        ring.publish(payloadOffset);

        int read = ring.poll((typeId, buf) -> {
            assertEquals(2, typeId);
            assertEquals(len, buf.length());
            for (int i = 0; i < len; i++) {
                assertEquals((byte) (i + 1), buf.getByte(i));
            }
        });
        assertEquals(1, read);
    }

    /**
     * Tests the zero-copy claim/abandon API path.
     */
    @Test
    void testClaimAbandon() {
        int payloadOffset = ring.claim(1, 64);
        assertTrue(payloadOffset >= 0);
        ring.abandon(payloadOffset);

        int read = ring.poll((id, buf) -> fail("Should be no message"));
        assertEquals(0, read);

        assertTrue(ring.offer(2, testPayload, 0, 32));
        AtomicInteger msgType = new AtomicInteger();
        read = ring.poll((id, buf) -> msgType.set(id));
        assertEquals(1, read);
        assertEquals(2, msgType.get());
    }

    /**
     * Forces a message to wrap around the end of the buffer,
     * which implicitly tests padding record handling.
     */
    @Test
    void testWrapAroundAndPadding() {
        int dataSize = ring.size(); // 1024
        int msg1Len = dataSize - 64;
        ByteBuffer msg1 = ByteBuffer.allocateDirect(msg1Len).order(ByteOrder.nativeOrder());

        assertTrue(ring.offer(1, msg1, 0, msg1Len));

        AtomicInteger type = new AtomicInteger();
        assertEquals(1, ring.poll((id, b) -> type.set(id)));
        assertEquals(1, type.get());

        int msg2Len = 128;
        assertTrue(msg2Len + 8 > (dataSize - (msg1Len + 8)));
        assertTrue(ring.offer(2, testPayload, 0, msg2Len));

        type.set(0);
        assertEquals(1, ring.poll((id, b) -> {
            type.set(id);
            assertEquals(msg2Len, b.length());
        }));
        assertEquals(2, type.get());
    }

    /**
     * Tests the ControlledPoll BREAK action.
     */
    @Test
    void testControlledPoll_Break() {
        ring.offer(1, testPayload, 0, 10);
        ring.offer(2, testPayload, 0, 10);
        ring.offer(3, testPayload, 0, 10);

        AtomicInteger count = new AtomicInteger(0);
        int read = ring.controlledPoll((typeId, buf) -> {
            count.incrementAndGet();
            if (typeId == 2) {
                return RingBuffer.ConsumerAction.BREAK;
            }
            return RingBuffer.ConsumerAction.CONTINUE;
        });

        assertEquals(2, read, "Should have read 2 messages");
        assertEquals(2, count.get(), "Handler should have been called 2 times");

        AtomicInteger remainingType = new AtomicInteger(0);
        assertEquals(1, ring.poll((id, b) -> remainingType.set(id)));
        assertEquals(3, remainingType.get());
    }

    /**
     * Tests that the ControlledPoll ABORT action throws UnsupportedOperationException.
     */
    @Test
    void testControlledPoll_Abort() {
        ring.offer(1, testPayload, 0, 10);

        assertThrows(UnsupportedOperationException.class, () -> {
            ring.controlledPoll((typeId, buf) -> RingBuffer.ConsumerAction.ABORT);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void testInvalidTypeId(int invalidId) {
        assertThrows(IllegalArgumentException.class, () -> ring.offer(invalidId, testPayload, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> ring.claim(invalidId, 10));
    }

    @Test
    void testInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> ring.offer(1, testPayload, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> ring.claim(1, -1));

        int tooLarge = ring.maxPayloadLength() + 1;
        assertThrows(IllegalArgumentException.class, () -> ring.offer(1, testPayload, 0, tooLarge));
        assertThrows(IllegalArgumentException.class, () -> ring.claim(1, tooLarge));
    }

    /**
     * Tests shared utilities.
     */
    @Test
    void testSharedUtilities() {
        long id1 = ring.nextCorrelation();
        long id2 = ring.nextCorrelation();
        assertEquals(id1 + 1, id2);
    }

    /**
     * Stress test with multiple producers and multiple consumers (using offer).
     */
    @Test
    void stressTestMultiProducerMultiConsumer() throws Exception {
        RingBuffer stressRing = new MPMCRingBuffer(RingBufferAllocator.allocate(1 << 20)); // 1MB
        int numProducers = 4;
        int numConsumers = 4;
        int messagesPerProducer = 1_000_000;
        int totalMessages = numProducers * messagesPerProducer;

        ExecutorService exec = Executors.newFixedThreadPool(numProducers + numConsumers);
        CountDownLatch producersDone = new CountDownLatch(numProducers);
        AtomicLong totalReceived = new AtomicLong(0);

        // CONSUMERS
        for (int i = 0; i < numConsumers; i++) {
            final int consumerId = i;
            exec.submit(() -> {
                Thread.currentThread().setName("MPMC-CONSUMER-" + consumerId);
                while (totalReceived.get() < totalMessages) {
                    int n = stressRing.poll((id, buf) -> {
                        totalReceived.incrementAndGet();
                    });
                    if (n == 0 && producersDone.getCount() == 0 && stressRing.utilization() == 0) {
                        break;
                    }
                    if (n == 0) {
                        Thread.onSpinWait();
                    }
                }
            });
        }

        // PRODUCERS
        for (int i = 0; i < numProducers; i++) {
            final int producerId = i + 1;
            final ByteBuffer msg = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
            exec.submit(() -> {
                Thread.currentThread().setName("MPMC-PRODUCER-" + producerId);
                for (int j = 0; j < messagesPerProducer; j++) {
                    while (!stressRing.offer(producerId, msg, 0, 0)) {
                        Thread.onSpinWait();
                    }
                }
                producersDone.countDown();
            });
        }

        assertTrue(producersDone.await(15, TimeUnit.SECONDS), "Producers timed out");

        long lastCount = -1;
        long startTime = System.currentTimeMillis();
        while (totalReceived.get() < totalMessages) {
            long currentCount = totalReceived.get();
            if (currentCount == lastCount) {
                if (System.currentTimeMillis() - startTime > 3000) {
                    fail("Consumers stalled for 3s, test failed. Expected: " + totalMessages + " Got: " + currentCount);
                }
            } else {
                lastCount = currentCount;
                startTime = System.currentTimeMillis();
            }
            Thread.sleep(100);
        }

        assertEquals(totalMessages, totalReceived.get());
        exec.shutdownNow();
    }

    /**
     * Stress test with multiple producers and multiple consumers (using claim/publish).
     */
    @Test
    void stressTestMultiProducerMultiConsumerZeroCopy() throws Exception {
        RingBuffer stressRing = new MPMCRingBuffer(RingBufferAllocator.allocate(1 << 20)); // 1MB
        int numProducers = 4;
        int numConsumers = 4;
        int messagesPerProducer = 1_000_000;
        int totalMessages = numProducers * messagesPerProducer;

        ExecutorService exec = Executors.newFixedThreadPool(numProducers + numConsumers);
        CountDownLatch producersDone = new CountDownLatch(numProducers);
        AtomicLong totalReceived = new AtomicLong(0);

        // CONSUMERS
        for (int i = 0; i < numConsumers; i++) {
            final int consumerId = i;
            exec.submit(() -> {
                Thread.currentThread().setName("MPMC-CONSUMER-ZC-" + consumerId);
                while (totalReceived.get() < totalMessages) {
                    int n = stressRing.poll((id, buf) -> {
                        totalReceived.incrementAndGet();
                    });
                    if (n == 0 && producersDone.getCount() == 0 && stressRing.utilization() == 0) {
                        break;
                    }
                    if (n == 0) {
                        Thread.onSpinWait();
                    }
                }
            });
        }

        // PRODUCERS
        for (int i = 0; i < numProducers; i++) {
            final int producerId = i + 1;
            exec.submit(() -> {
                Thread.currentThread().setName("MPMC-PRODUCER-ZC-" + producerId);
                for (int j = 0; j < messagesPerProducer; j++) {
                    int payloadOffset;
                    while ((payloadOffset = stressRing.claim(producerId, 0)) < 0) {
                        Thread.onSpinWait();
                    }
                    stressRing.publish(payloadOffset);
                }
                producersDone.countDown();
            });
        }

        assertTrue(producersDone.await(15, TimeUnit.SECONDS), "Producers timed out");

        long lastCount = -1;
        long startTime = System.currentTimeMillis();
        while (totalReceived.get() < totalMessages) {
            long currentCount = totalReceived.get();
            if (currentCount == lastCount) {
                if (System.currentTimeMillis() - startTime > 3000) {
                    fail("Consumers stalled for 3s, test failed. Expected: " + totalMessages + " Got: " + currentCount);
                }
            } else {
                lastCount = currentCount;
                startTime = System.currentTimeMillis();
            }
            Thread.sleep(100);
        }

        assertEquals(totalMessages, totalReceived.get());
        exec.shutdownNow();
    }
}
