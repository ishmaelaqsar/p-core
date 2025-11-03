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
    static long calcOffset(final long index, final long mask) {
        return ARRAY_OBJECT_BASE_OFFSET + ((index & mask) * ARRAY_OBJECT_INDEX_SCALE);
    }

    /**
     * Rounds the given capacity up to the next power of two.
     */
    static int ceilingNextPowerOfTwo(final int x) {
        if (x <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        return 1 << (32 - Integer.numberOfLeadingZeros(x - 1));
    }
}
