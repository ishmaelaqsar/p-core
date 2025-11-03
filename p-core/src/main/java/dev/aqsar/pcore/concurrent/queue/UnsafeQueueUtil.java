package dev.aqsar.pcore.concurrent.queue;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Utility class for Unsafe operations and cache-line padding.
 */
@SuppressWarnings("restriction")
final class UnsafeQueueUtil {

    static final Unsafe UNSAFE;
    static final long ARRAY_OBJECT_BASE_OFFSET;
    static final int ARRAY_OBJECT_INDEX_SCALE;

    private static final int CACHE_LINE = 64;
    private static final int PADDING_FIELDS = 7; // 7 * 8-byte long = 56 bytes

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);

            ARRAY_OBJECT_BASE_OFFSET = UNSAFE.arrayBaseOffset(Object[].class);
            ARRAY_OBJECT_INDEX_SCALE = UNSAFE.arrayIndexScale(Object[].class);

            if (ARRAY_OBJECT_INDEX_SCALE != 4 && ARRAY_OBJECT_INDEX_SCALE != 8) {
                throw new IllegalStateException("Unexpected object reference size: " + ARRAY_OBJECT_INDEX_SCALE);
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Calculates the memory offset for an item in the array.
     */
    static long calcOffset(long index, long mask) {
        return ARRAY_OBJECT_BASE_OFFSET + ((index & mask) * ARRAY_OBJECT_INDEX_SCALE);
    }

    /**
     * Rounds the given capacity up to the next power of two.
     */
    static int ceilingNextPowerOfTwo(int x) {
        if (x <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        return 1 << (32 - Integer.numberOfLeadingZeros(x - 1));
    }

    // --- Padded Base Classes for Cache Friendliness ---

    /**
     * Padded base for producer fields.
     */
    abstract static class PaddedProducerIndex extends java.util.concurrent.atomic.AtomicLong {
        // 56 bytes of padding
        protected long p1, p2, p3, p4, p5, p6, p7;

        PaddedProducerIndex(long initialValue) {
            super(initialValue);
        }
    }

    /**
     * Padded producer index + cache (for SPSC).
     */
    abstract static class PaddedSPSCProducerFields extends PaddedProducerIndex {
        protected long producerCache; // Cache of consumer index

        PaddedSPSCProducerFields(long initialValue) {
            super(initialValue);
        }
    }

    /**
     * Padded base for consumer fields.
     */
    static class PaddedConsumerIndex extends java.util.concurrent.atomic.AtomicLong {
        // 56 bytes of padding
        protected long p8, p9, p10, p11, p12, p13, p14;

        PaddedConsumerIndex(long initialValue) {
            super(initialValue);
        }
    }
}
