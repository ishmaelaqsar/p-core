package dev.aqsar.pcore.concurrent.queue;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.aqsar.pcore.concurrent.queue.UnsafeQueueUtil.*;

/**
 * A Single-Producer, Single-Consumer (SPSC) lock-free, bounded,
 * array-backed queue.
 * <p>
 * This implementation uses cache-line padding and Unsafe operations
 * to achieve wait-free performance, modeling the logic from
 * high-performance libraries like JCTools.
 */
@SuppressWarnings({"restriction", "unchecked"})
public final class SPSCArrayQueue<T> extends PaddedSPSCProducerFields implements ConcurrentArrayQueue<T> {

    // --- Consumer Fields (on a separate cache line) ---
    private long p8, p9, p10, p11, p12, p13, p14;
    private volatile long consumerIndex;
    private long consumerCache; // Cache of producer index
    private long p15, p16, p17, p18, p19, p20, p21;

    // --- Shared Fields ---
    private final int capacity;
    private final long mask;
    private final Object[] buffer;

    private final long P_INDEX_OFFSET;
    private final long C_INDEX_OFFSET;

    public SPSCArrayQueue(int requestedCapacity) {
        super(0L); // PaddedProducerIndex
        this.capacity = ceilingNextPowerOfTwo(requestedCapacity);
        this.mask = capacity - 1;
        this.buffer = new Object[capacity];

        try {
            P_INDEX_OFFSET =
                UNSAFE.objectFieldOffset(java.util.concurrent.atomic.AtomicLong.class.getDeclaredField("value"));

            C_INDEX_OFFSET = UNSAFE.objectFieldOffset(SPSCArrayQueue.class.getDeclaredField("consumerIndex"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean offer(T element) {
        if (element == null) {
            throw new NullPointerException("Element cannot be null");
        }

        final long pIndex = get(); // Volatile read of producer index
        final long pCache = this.producerCache; // Plain read of consumer cache

        if (pIndex - pCache == capacity) {
            // Cache is stale, refresh
            final long cIndex = UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
            if (pIndex - cIndex == capacity) {
                return false; // Full
            }
            this.producerCache = cIndex; // Update cache
        }

        final long offset = calcOffset(pIndex, mask);
        // This is a "release" write. It ensures the element is visible
        // *before* the index is updated.
        UNSAFE.putOrderedObject(buffer, offset, element);

        // This is the "publish" write.
        UNSAFE.putLongVolatile(this, P_INDEX_OFFSET, pIndex + 1);
        return true;
    }

    @Override
    public T poll() {
        final long cIndex = UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
        final long cCache = this.consumerCache; // Plain read of producer cache

        if (cIndex == cCache) {
            // Cache is stale, refresh
            final long pIndex = UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
            if (cIndex == pIndex) {
                return null; // Empty
            }
            this.consumerCache = pIndex; // Update cache
        }

        final long offset = calcOffset(cIndex, mask);
        // This is an "acquire" read. It pairs with the putOrderedObject.
        final T element = (T) UNSAFE.getObjectVolatile(buffer, offset);

        // Clear the slot to prevent "leaking" objects
        UNSAFE.putObject(buffer, offset, null); // Plain write is fine

        // This is the "publish" write, making the slot available to producer.
        UNSAFE.putLongVolatile(this, C_INDEX_OFFSET, cIndex + 1);
        return element;
    }

    @Override
    public int drain(Consumer<T> consumer, int limit) {
        final long cIndex = UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
        long pIndex = this.consumerCache; // Use cache first

        if (cIndex == pIndex) {
            pIndex = UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
            if (cIndex == pIndex) {
                return 0; // Empty
            }
            this.consumerCache = pIndex; // Update cache
        }

        final int available = (int) (pIndex - cIndex);
        final int toRead = Math.min(available, limit);

        for (int i = 0; i < toRead; i++) {
            final long offset = calcOffset(cIndex + i, mask);
            // This can be a plain read because we are bounded by the
            // volatile read of pIndex.
            final T element = (T) UNSAFE.getObject(buffer, offset);
            UNSAFE.putObject(buffer, offset, null); // Clear slot
            consumer.accept(element);
        }

        // Publish consumer progress in one volatile write
        UNSAFE.putLongVolatile(this, C_INDEX_OFFSET, cIndex + toRead);
        return toRead;
    }

    @Override
    public int fill(Supplier<T> supplier, int limit) {
        final long pIndex = get(); // Volatile read
        long cIndex = this.producerCache; // Use cache first

        if (pIndex - cIndex == capacity) {
            cIndex = UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
            if (pIndex - cIndex == capacity) {
                return 0; // Full
            }
            this.producerCache = cIndex; // Update cache
        }

        final int available = (int) (capacity - (pIndex - cIndex));
        final int toWrite = Math.min(available, limit);

        for (int i = 0; i < toWrite; i++) {
            final long offset = calcOffset(pIndex + i, mask);
            // Plain write is fine, we publish with the index update.
            UNSAFE.putObject(buffer, offset, supplier.get());
        }

        // Publish all writes in one volatile write
        UNSAFE.putLongVolatile(this, P_INDEX_OFFSET, pIndex + toWrite);
        return toWrite;
    }

    @Override
    public int size() {
        return (int) (UNSAFE.getLongVolatile(this, P_INDEX_OFFSET) - UNSAFE.getLongVolatile(this, C_INDEX_OFFSET));
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public boolean isEmpty() {
        return UNSAFE.getLongVolatile(this, P_INDEX_OFFSET) == UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
    }

    @Override
    public void clear() {
        Arrays.fill(buffer, null);
        UNSAFE.putLongVolatile(this, P_INDEX_OFFSET, 0L);
        UNSAFE.putLongVolatile(this, C_INDEX_OFFSET, 0L);
        this.producerCache = 0L;
        this.consumerCache = 0L;
    }
}
