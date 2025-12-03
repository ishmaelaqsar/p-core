package dev.aqsar.pcore.concurrent.ringbuffer;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.Buffer;

/**
 * Utility class for allocating correctly sized and cache-aligned
 * {@link java.nio.ByteBuffer} instances suitable for {@link RingBuffer} implementations.
 *
 * <p>Typical usage:
 * <pre>
 * ByteBuffer buf = RingBufferAllocator.allocate(1 << 16);
 * RingBuffer ring = new SPSCRingBuffer(buf); // or MPSCRingBuffer, etc.
 * </pre>
 *
 * <p>This ensures the buffer is:
 * <ul>
 * <li>Direct (off-heap)</li>
 * <li>Capacity = power-of-two data region + {@link RingBuffer#METADATA_SIZE}</li>
 * <li>Native byte order</li>
 * <li>Cache-aligned (default 64 B)</li>
 * </ul>
 */
public final class RingBufferAllocator {

    /**
     * Default alignment (64 bytes for modern CPUs).
     */
    public static final int DEFAULT_ALIGNMENT = 64;

    private RingBufferAllocator() { /* utility class */ }

    /**
     * Allocates a direct, cache-aligned {@link ByteBuffer} suitable for {@link RingBuffer}.
     *
     * @param dataSize desired data region size (must be a power of two and ≥ {@link RingBuffer#MIN_SIZE})
     * @return a new {@link ByteBuffer} with native byte order and total capacity = dataSize + {@link RingBuffer#METADATA_SIZE}
     * @throws IllegalArgumentException if dataSize is invalid or system cannot align
     */
    public static ByteBuffer allocate(final int dataSize) {
        return allocateAligned(dataSize, DEFAULT_ALIGNMENT);
    }

    /**
     * Allocates a direct {@link ByteBuffer} with a specific alignment.
     *
     * @param dataSize  desired data region size (power-of-two).
     * @param alignment alignment boundary (must be power-of-two, typically 64).
     * @return a new aligned direct buffer.
     */
    public static ByteBuffer allocateAligned(final int dataSize, final int alignment) {
        if (!isPowerOfTwo(dataSize)) {
            throw new IllegalArgumentException("dataSize must be a power of two: " + dataSize);
        }
        if (dataSize < RingBuffer.MIN_SIZE) {
            throw new IllegalArgumentException("dataSize too small: " + dataSize);
        }
        if (!isPowerOfTwo(alignment) || alignment < 8) {
            throw new IllegalArgumentException("alignment must be power-of-two and ≥ 8: " + alignment);
        }

        final int requiredCapacity = dataSize + RingBuffer.METADATA_SIZE;
        final int totalToAllocate = requiredCapacity + alignment;

        final ByteBuffer raw = ByteBuffer.allocateDirect(totalToAllocate).order(ByteOrder.nativeOrder());

        // Align the buffer start to the desired boundary
        final long address = addressOf(raw);
        final int misalignment = (int) (address & (alignment - 1));
        final int padding = misalignment == 0 ? 0 : alignment - misalignment;

        // Set position and limit to create a slice of the *exact* required capacity
        raw.position(padding);
        raw.limit(padding + requiredCapacity);

        return raw.slice().order(ByteOrder.nativeOrder());
    }

    /**
     * Retrieves the native memory address of a direct buffer.
     * This is private as it's only needed for alignment calculation.
     */
    private static long addressOf(final ByteBuffer direct) {
        try {
            // Use java.nio.Buffer to access the 'address' field
            final Field field = Buffer.class.getDeclaredField("address");
            field.setAccessible(true);
            return field.getLong(direct);
        } catch (Exception e) {
            throw new RuntimeException("Cannot access direct buffer address", e);
        }
    }

    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
}
