package dev.aqsar.pcore.concurrent.queue;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.aqsar.pcore.concurrent.queue.UnsafeQueueUtil.*;

/**
 * A Multi-Producer, Multi-Consumer (MPMC) lock-free, bounded,
 * array-backed queue using sequence numbers for coordination.
 */
@SuppressWarnings({"restriction", "unchecked", "unused"})
public final class MPMCArrayQueue<T> implements ConcurrentArrayQueue<T> {

    // Producer fields (separate cache line)
    private long p1, p2, p3, p4, p5, p6, p7;
    private volatile long producerIndex;
    private long p8, p9, p10, p11, p12, p13, p14;

    // Consumer fields (separate cache line)
    private long p15, p16, p17, p18, p19, p20, p21;
    private volatile long consumerIndex;
    private long p22, p23, p24, p25, p26, p27, p28;

    private final int capacity;
    private final long mask;
    private final Cell[] buffer;

    private final long P_INDEX_OFFSET;
    private final long C_INDEX_OFFSET;

    /**
     * Cell holds both the element and a sequence number for coordination.
     */
    private static final class Cell {
        // Padding to prevent false sharing between cells
        private long p1, p2, p3, p4, p5, p6, p7;
        private volatile long sequence;
        private volatile Object element;
        private long p8, p9, p10, p11, p12, p13, p14;
    }

    public MPMCArrayQueue(final int requestedCapacity) {
        this.capacity = ceilingNextPowerOfTwo(requestedCapacity);
        this.mask = capacity - 1;
        this.buffer = new Cell[capacity];

        // Initialize cells with sequence numbers
        for (int i = 0; i < capacity; i++) {
            buffer[i] = new Cell();
            buffer[i].sequence = i;
        }

        try {
            P_INDEX_OFFSET = UNSAFE.objectFieldOffset(MPMCArrayQueue.class.getDeclaredField("producerIndex"));
            C_INDEX_OFFSET = UNSAFE.objectFieldOffset(MPMCArrayQueue.class.getDeclaredField("consumerIndex"));
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

        long pIndex;
        Cell cell;

        while (true) {
            pIndex = UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
            cell = buffer[(int) (pIndex & mask)];

            final long seq = getSequenceVolatile(cell);
            final long diff = seq - pIndex;

            if (diff == 0) {
                // Slot is available for writing
                if (UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, pIndex, pIndex + 1)) {
                    break; // Successfully claimed
                }
            } else if (diff < 0) {
                // Queue is full
                return false;
            }
            // else diff > 0: slot not ready yet (another producer is working on it), retry
        }

        // We own this slot, write the element
        putElementVolatile(cell, element);

        // Publish by updating sequence
        putSequenceVolatile(cell, pIndex + 1);
        return true;
    }

    /**
     * @inheritDoc
     */
    @Override
    public T poll() {
        long cIndex;
        Cell cell;
        T element;

        while (true) {
            cIndex = UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
            cell = buffer[(int) (cIndex & mask)];

            final long seq = getSequenceVolatile(cell);
            final long diff = seq - (cIndex + 1);

            if (diff == 0) {
                // Slot is available for reading
                if (UNSAFE.compareAndSwapLong(this, C_INDEX_OFFSET, cIndex, cIndex + 1)) {
                    break; // Successfully claimed
                }
            } else if (diff < 0) {
                // Queue is empty
                return null;
            }
            // else diff > 0: slot not ready yet (another consumer is working on it), retry
        }

        // We own this slot, read the element
        element = (T) getElementVolatile(cell);
        putElementVolatile(cell, null); // Clear for GC

        // Publish by updating sequence
        putSequenceVolatile(cell, cIndex + capacity);
        return element;
    }

    /**
     * @inheritDoc
     */
    @Override
    public int drain(Consumer<T> consumer, int limit) {
        int count = 0;
        while (count < limit) {
            T element = poll();
            if (element == null) {
                break;
            }
            consumer.accept(element);
            count++;
        }
        return count;
    }

    /**
     * @inheritDoc
     */
    @Override
    public int fill(final Supplier<T> supplier, final int limit) {
        int count = 0;
        while (count < limit) {
            if (!offer(supplier.get())) {
                break;
            }
            count++;
        }
        return count;
    }

    /**
     * Returns an <strong>approximate</strong> size of the queue based on
     * claimed slots. In an MPMC context:
     * <ul>
     *   <li>This value may be stale immediately after reading</li>
     *   <li>The actual number of retrievable elements may differ due to
     *       in-flight operations by other threads</li>
     *   <li>Use only for monitoring, debugging, or non-critical decisions</li>
     * </ul>
     *
     * <p>For critical logic, use {@link #poll()} directly rather than
     * checking size first.</p>
     *
     * @return approximate number of elements, clamped to [0, capacity]
     */
    @Override
    public int size() {
        final long prod = UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
        final long cons = UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);

        // Handle overflow: use long arithmetic, then clamp
        final long size = prod - cons;

        // Clamp to valid range [0, capacity]
        if (size < 0) {
            return 0;
        }
        if (size > capacity) {
            return capacity;
        }
        return (int) size;
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
        // Not thread-safe - should only be called when no other threads are accessing
        for (Cell cell : buffer) {
            cell.element = null;
        }
        UNSAFE.putLongVolatile(this, P_INDEX_OFFSET, 0L);
        UNSAFE.putLongVolatile(this, C_INDEX_OFFSET, 0L);

        // Reset sequences
        for (int i = 0; i < capacity; i++) {
            buffer[i].sequence = i;
        }
    }

    // Helper methods using Unsafe for volatile access to Cell fields
    private static final long SEQUENCE_OFFSET;
    private static final long ELEMENT_OFFSET;

    static {
        try {
            SEQUENCE_OFFSET = UNSAFE.objectFieldOffset(Cell.class.getDeclaredField("sequence"));
            ELEMENT_OFFSET = UNSAFE.objectFieldOffset(Cell.class.getDeclaredField("element"));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static long getSequenceVolatile(Cell cell) {
        return UNSAFE.getLongVolatile(cell, SEQUENCE_OFFSET);
    }

    private static void putSequenceVolatile(Cell cell, long value) {
        UNSAFE.putLongVolatile(cell, SEQUENCE_OFFSET, value);
    }

    private static Object getElementVolatile(Cell cell) {
        return UNSAFE.getObjectVolatile(cell, ELEMENT_OFFSET);
    }

    private static void putElementVolatile(Cell cell, Object value) {
        UNSAFE.putObjectVolatile(cell, ELEMENT_OFFSET, value);
    }
}
