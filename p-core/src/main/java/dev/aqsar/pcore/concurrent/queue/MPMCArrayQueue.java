package dev.aqsar.pcore.concurrent.queue;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.aqsar.pcore.concurrent.queue.UnsafeQueueUtil.*;

/**
 * A Multi-Producer, Multi-Consumer (MPMC) lock-free, bounded,
 * array-backed queue.
 * <p>
 * This implementation uses cache-line padding and Unsafe CAS operations
 * to achieve lock-free performance. It implements a two-sided check
 * on the buffer slots to prevent race conditions between producers
 * and consumers wrapping around the array.
 */
@SuppressWarnings({"restriction", "unchecked"})
public final class MPMCArrayQueue<T> extends PaddedProducerIndex implements ConcurrentArrayQueue<T> {

    // --- Consumer Fields (on a separate cache line) ---
    private final PaddedConsumerIndex consumerIndex = new PaddedConsumerIndex(0L);

    // --- Shared Fields ---
    private final int capacity;
    private final long mask;
    private final Object[] buffer;

    public MPMCArrayQueue(int requestedCapacity) {
        super(0L); // PaddedProducerIndex
        this.capacity = ceilingNextPowerOfTwo(requestedCapacity);
        this.mask = capacity - 1;
        this.buffer = new Object[capacity];
    }

    @Override
    public boolean offer(T element) {
        if (element == null) {
            throw new NullPointerException("Element cannot be null");
        }

        long pIndex;
        long offset;

        while (true) {
            pIndex = get(); // Volatile read
            final long cIndex = consumerIndex.get(); // Volatile read

            if (pIndex - cIndex == capacity) {
                return false; // Full
            }

            // Calculate offset *before* CAS
            offset = calcOffset(pIndex, mask);

            // Spin-wait until the consumer has 'nulled' this slot
            // This prevents the "flyby" race condition.
            if (UNSAFE.getObjectVolatile(buffer, offset) != null) {
                // We are full, but the cIndex hasn't caught up yet.
                // A 'return false' here is also valid, but spinning
                // is more common for MPMC.
                Thread.onSpinWait();
                continue;
            }

            // CAS to claim the producer slot
            if (compareAndSet(pIndex, pIndex + 1)) {
                break; // Slot claimed
            }
            // CAS failed, another producer beat us, retry
        }

        // This is the "publish" write.
        UNSAFE.putObjectVolatile(buffer, offset, element);
        return true;
    }

    @Override
    public T poll() {
        long cIndex;
        long offset;
        T element;

        while (true) {
            cIndex = consumerIndex.get(); // Volatile read
            final long pIndex = get(); // Volatile read

            if (cIndex == pIndex) {
                return null; // Empty
            }

            // Calculate offset *before* CAS
            offset = calcOffset(cIndex, mask);

            // Spin-wait until the producer has published the element.
            // This prevents the "lost update" deadlock.
            element = (T) UNSAFE.getObjectVolatile(buffer, offset);
            if (element == null) {
                Thread.onSpinWait();
                continue; // Data not ready, retry
            }

            // CAS to claim the consumer slot
            if (consumerIndex.compareAndSet(cIndex, cIndex + 1)) {
                break; // Slot claimed
            }
            // CAS failed, another consumer beat us, retry
        }

        // This is the "release" write, making the slot available to producers.
        UNSAFE.putObjectVolatile(buffer, offset, null);
        return element;
    }

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

    @Override
    public int fill(Supplier<T> supplier, int limit) {
        int count = 0;
        while (count < limit) {
            if (!offer(supplier.get())) {
                break;
            }
            count++;
        }
        return count;
    }

    @Override
    public int size() {
        return (int) (get() - consumerIndex.get());
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public boolean isEmpty() {
        return get() == consumerIndex.get();
    }

    @Override
    public void clear() {
        // Not thread-safe!
        Arrays.fill(buffer, null);
        set(0L);
        consumerIndex.set(0L);
    }
}
