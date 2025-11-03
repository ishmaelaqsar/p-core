package dev.aqsar.pcore.concurrent;

import dev.aqsar.pcore.concurrent.ringbuffer.RingBuffer;
import dev.aqsar.pcore.concurrent.ringbuffer.RingBufferAllocator;
import dev.aqsar.pcore.concurrent.ringbuffer.SPSCRingBuffer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic and stress tests for {@link SPSCRingBuffer}.
 */
class SPSCRingBufferTest {

    private static final int TEST_CAPACITY = 1024;
    private SPSCRingBuffer ring;
    private ByteBuffer testPayload;

    @BeforeAll
    static void setupUnsafe() throws Exception {
        // We still need to allow Unsafe for the *implementation*
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
    }

    @BeforeEach
    void setup() {
        ring = new SPSCRingBuffer(RingBufferAllocator.allocate(TEST_CAPACITY));

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
        }, 1); // <-- Specify limit

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

        // Write directly to the underlying buffer using ByteBuffer API
        ByteBuffer underlying = ring.underlyingBuffer();
        for (int i = 0; i < len; i++) {
            underlying.put(payloadOffset + i, (byte) (i + 1));
        }

        ring.publish(payloadOffset);

        // Now poll and verify
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
        int msg1Len = dataSize - 64; // Almost fill the buffer
        ByteBuffer msg1 = ByteBuffer.allocateDirect(msg1Len).order(ByteOrder.nativeOrder());

        assertTrue(ring.offer(1, msg1, 0, msg1Len));

        AtomicInteger type = new AtomicInteger();
        assertEquals(1, ring.poll((id, b) -> type.set(id)));
        assertEquals(1, type.get());

        int msg2Len = 128;
        assertTrue(msg2Len + 8 > (dataSize - (msg1Len + 8))); // 136 > 56
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
     * Tests the ControlledPoll ABORT action.
     */
    @Test
    void testControlledPoll_Abort() {
        ring.offer(1, testPayload, 0, 10);
        ring.offer(2, testPayload, 0, 10);
        ring.offer(3, testPayload, 0, 10);

        AtomicInteger count = new AtomicInteger(0);
        int read = ring.controlledPoll((typeId, buf) -> {
            count.incrementAndGet();
            if (typeId == 2) {
                return RingBuffer.ConsumerAction.ABORT;
            }
            return RingBuffer.ConsumerAction.CONTINUE;
        });

        assertEquals(1, read, "Should have read 1 message");
        assertEquals(2, count.get(), "Handler should have been called 2 times");

        AtomicInteger remainingType = new AtomicInteger(0);
        assertEquals(1, ring.poll((id, b) -> remainingType.set(id), 1));
        assertEquals(2, remainingType.get()); // 2 was aborted, so it's first

        assertEquals(1, ring.poll((id, b) -> remainingType.set(id), 1));
        assertEquals(3, remainingType.get()); // 3 is next

        assertEquals(0, ring.poll((id, b) -> fail(), 1));
    }

    /**
     * Tests invalid arguments for offer/claim.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void testInvalidTypeId(int invalidId) {
        assertThrows(IllegalArgumentException.class, () -> ring.offer(invalidId, testPayload, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> ring.claim(invalidId, 10));
    }

    /**
     * Tests invalid lengths for offer/claim.
     */
    @Test
    void testInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> ring.offer(1, testPayload, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> ring.claim(1, -1));

        int tooLarge = ring.maxPayloadLength() + 1;
        assertThrows(IllegalArgumentException.class, () -> ring.offer(1, testPayload, 0, tooLarge));
        assertThrows(IllegalArgumentException.class, () -> ring.claim(1, tooLarge));
    }

    /**
     * Tests SPSC-specific utilities.
     */
    @Test
    void testHeartbeatAndCorrelation() {
        long id1 = ring.nextCorrelation();
        long id2 = ring.nextCorrelation();
        assertEquals(id1 + 1, id2);

        ring.markHeartbeat(123456L);
        assertEquals(123456L, ring.readHeartbeat());
    }

    /**
     * Stress test that verifies data integrity (content and order)
     * between a single producer and single consumer.
     */
    @Test
    void stressTestConcurrentWithDataVerification() throws Exception {
        SPSCRingBuffer stressRing = new SPSCRingBuffer(RingBufferAllocator.allocate(1 << 18)); // 256KiB
        int messages = 5_000_000;
        ByteBuffer msg = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()); // Use 8 bytes for a long

        ExecutorService exec = Executors.newFixedThreadPool(2);
        AtomicLong receivedCount = new AtomicLong();
        AtomicLong expectedSeq = new AtomicLong(0);
        AtomicBoolean dataError = new AtomicBoolean(false);

        CountDownLatch done = new CountDownLatch(1);

        // CONSUMER
        exec.submit(() -> {
            Thread.currentThread().setName("CONSUMER");
            while (receivedCount.get() < messages) {
                int n = stressRing.poll((id, buf) -> {
                    long receivedVal = buf.getLong(0); // Read from start of payload
                    long expectedVal = expectedSeq.getAndIncrement();

                    if (receivedVal != expectedVal) {
                        System.err.printf("Data error! Expected: %d, Got: %d%n", expectedVal, receivedVal);
                        dataError.set(true);
                    }
                    receivedCount.incrementAndGet();
                });
                if (n == 0) {
                    Thread.onSpinWait();
                }
            }
            done.countDown();
        });

        // PRODUCER
        exec.submit(() -> {
            Thread.currentThread().setName("PRODUCER");
            for (long i = 0; i < messages; i++) {
                msg.putLong(0, i);
                while (!stressRing.offer(1, msg, 0, 8)) {
                    Thread.onSpinWait(); // Busy-spin
                }
            }
        });

        assertTrue(done.await(15, TimeUnit.SECONDS), "Test timed out");
        assertFalse(dataError.get(), "Data integrity error detected");
        assertEquals(messages, receivedCount.get());

        exec.shutdownNow();
    }

    @Test
    void stressTestConcurrentZeroCopy() throws Exception {
        // 256KiB buffer
        SPSCRingBuffer stressRing = new SPSCRingBuffer(RingBufferAllocator.allocate(1 << 18));
        int messages = 5_000_000;
        int payloadSize = 8; // 8 bytes for a long
        int typeId = 1;

        // Get the backing buffer once for the producer
        ByteBuffer backingBuffer = stressRing.underlyingBuffer();

        ExecutorService exec = Executors.newFixedThreadPool(2);
        AtomicLong receivedCount = new AtomicLong();
        AtomicLong expectedSeq = new AtomicLong(0);
        AtomicBoolean dataError = new AtomicBoolean(false);

        CountDownLatch done = new CountDownLatch(1);

        // CONSUMER
        exec.submit(() -> {
            Thread.currentThread().setName("CONSUMER");
            while (receivedCount.get() < messages) {
                int n = stressRing.poll((id, buf) -> {
                    long receivedVal = buf.getLong(0); // Read from start of payload
                    long expectedVal = expectedSeq.getAndIncrement();

                    if (receivedVal != expectedVal) {
                        System.err.printf("Data error! Expected: %d, Got: %d%n", expectedVal, receivedVal);
                        dataError.set(true);
                    }
                    receivedCount.incrementAndGet();
                });
                if (n == 0) {
                    Thread.onSpinWait();
                }
            }
            done.countDown();
        });

        // PRODUCER (Using claim/publish)
        exec.submit(() -> {
            Thread.currentThread().setName("PRODUCER");
            for (long i = 0; i < messages; i++) {
                int payloadOffset;

                // 1. Claim a slot
                while ((payloadOffset = stressRing.claim(typeId, payloadSize)) < 0) {
                    Thread.onSpinWait(); // Busy-spin if buffer is full
                }

                // 2. Write data DIRECTLY into the backing buffer
                backingBuffer.putLong(payloadOffset, i);

                // 3. Publish the slot
                stressRing.publish(payloadOffset);
            }
        });

        assertTrue(done.await(15, TimeUnit.SECONDS), "Test timed out");
        assertFalse(dataError.get(), "Data integrity error detected");
        assertEquals(messages, receivedCount.get());

        exec.shutdownNow();
    }
}
