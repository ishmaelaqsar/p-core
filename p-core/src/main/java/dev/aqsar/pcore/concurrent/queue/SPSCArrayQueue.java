package dev.aqsar.pcore.concurrent.queue;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.aqsar.pcore.concurrent.queue.UnsafeQueueUtil.*;

/**
 * A Single-Producer, Single-Consumer (SPSC) lock-free, bounded,
 * array-backed queue.
 */
@SuppressWarnings({"restriction", "unchecked"})
public final class SPSCArrayQueue<T> implements ConcurrentArrayQueue<T> {

    // Producer fields (separate cache line)
    private long p1, p2, p3, p4, p5, p6, p7;
    private volatile long producerIndex;
    private long producerCache; // Cache of consumer index
    private long p8, p9, p10, p11, p12, p13, p14;

    // Consumer fields (separate cache line)
    private long p15, p16, p17, p18, p19, p20, p21;
    private volatile long consumerIndex;
    private long consumerCache; // Cache of producer index
    private long p22, p23, p24, p25, p26, p27, p28;

    // --- Shared Fields ---
    private final int capacity;
    private final long mask;
    private final Object[] buffer;

    private final long P_INDEX_OFFSET;
    private final long C_INDEX_OFFSET;

    public SPSCArrayQueue(final int requestedCapacity) {
        this.capacity = ceilingNextPowerOfTwo(requestedCapacity);
        this.mask = capacity - 1;
        this.buffer = new Object[capacity];

        try {
            P_INDEX_OFFSET = UNSAFE.objectFieldOffset(SPSCArrayQueue.class.getDeclaredField("producerIndex"));
            C_INDEX_OFFSET = UNSAFE.objectFieldOffset(SPSCArrayQueue.class.getDeclaredField("consumerIndex"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean offer(final T element) {
        if (element == null) {
            throw new NullPointerException("Element cannot be null");
        }

        final long pIndex = UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
        final long pCache = this.producerCache;

        if (pIndex - pCache == capacity) {
            final long cIndex = UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
            if (pIndex - cIndex == capacity) {
                return false;
            }
            this.producerCache = cIndex;
        }

        final long offset = calcOffset(pIndex, mask);
        UNSAFE.putOrderedObject(buffer, offset, element);
        UNSAFE.putLongVolatile(this, P_INDEX_OFFSET, pIndex + 1);
        return true;
    }

    /**
     * @inheritDoc
     */
    @Override
    public T poll() {
        final long cIndex = UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
        final long cCache = this.consumerCache;

        if (cIndex == cCache) {
            final long pIndex = UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
            if (cIndex == pIndex) {
                return null;
            }
            this.consumerCache = pIndex;
        }

        final long offset = calcOffset(cIndex, mask);
        final T element = (T) UNSAFE.getObjectVolatile(buffer, offset);
        UNSAFE.putObject(buffer, offset, null);
        UNSAFE.putLongVolatile(this, C_INDEX_OFFSET, cIndex + 1);
        return element;
    }

    /**
     * @inheritDoc
     */
    @Override
    public int drain(Consumer<T> consumer, int limit) {
        final long cIndex = UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
        long pIndex = this.consumerCache;

        if (cIndex == pIndex) {
            pIndex = UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
            if (cIndex == pIndex) {
                return 0;
            }
            this.consumerCache = pIndex;
        }

        final int available = (int) (pIndex - cIndex);
        final int toRead = Math.min(available, limit);

        // First read must be volatile to synchronize with ordered writes
        for (int i = 0; i < toRead; i++) {
            final long offset = calcOffset(cIndex + i, mask);
            final T element =
                (i == 0) ? (T) UNSAFE.getObjectVolatile(buffer, offset) : (T) UNSAFE.getObject(buffer, offset);
            UNSAFE.putObject(buffer, offset, null);
            consumer.accept(element);
        }

        UNSAFE.putLongVolatile(this, C_INDEX_OFFSET, cIndex + toRead);
        return toRead;
    }

    /**
     * @inheritDoc
     */
    @Override
    public int fill(Supplier<T> supplier, int limit) {
        final long pIndex = UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
        long cIndex = this.producerCache;

        if (pIndex - cIndex == capacity) {
            cIndex = UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
            if (pIndex - cIndex == capacity) {
                return 0;
            }
            this.producerCache = cIndex;
        }

        final int available = (int) (capacity - (pIndex - cIndex));
        final int toWrite = Math.min(available, limit);

        for (int i = 0; i < toWrite; i++) {
            final long offset = calcOffset(pIndex + i, mask);
            UNSAFE.putOrderedObject(buffer, offset, supplier.get());
        }

        UNSAFE.putLongVolatile(this, P_INDEX_OFFSET, pIndex + toWrite);
        return toWrite;
    }

    /**
     * @inheritDoc
     */
    @Override
    public int size() {
        return (int) (UNSAFE.getLongVolatile(this, P_INDEX_OFFSET) - UNSAFE.getLongVolatile(this, C_INDEX_OFFSET));
    }

    /**
     * @inheritDoc
     */
    @Override
    public int capacity() {
        return capacity;
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isEmpty() {
        return UNSAFE.getLongVolatile(this, P_INDEX_OFFSET) == UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void clear() {
        Arrays.fill(buffer, null);
        UNSAFE.putLongVolatile(this, P_INDEX_OFFSET, 0L);
        UNSAFE.putLongVolatile(this, C_INDEX_OFFSET, 0L);
        this.producerCache = 0L;
        this.consumerCache = 0L;
    }
}
