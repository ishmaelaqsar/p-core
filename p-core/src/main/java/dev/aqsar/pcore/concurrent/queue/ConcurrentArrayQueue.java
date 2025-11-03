package dev.aqsar.pcore.concurrent.queue;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A concurrent, array-backed, bounded queue.
 *
 * @param <T> The type of elements held in this queue
 */
public interface ConcurrentArrayQueue<T> {

    /**
     * Inserts the specified element into this queue if it is possible
     * to do so without violating capacity restrictions.
     *
     * @param element the element to add
     * @return {@code true} if the element was added, {@code false} if the queue is full
     */
    boolean offer(T element);

    /**
     * Retrieves and removes the head of this queue.
     *
     * @return the head of this queue, or {@code null} if this queue is empty
     */
    T poll();

    /**
     * Retrieves and removes up to {@code limit} elements from this queue,
     * delivering them to the provided consumer.
     *
     * @param consumer the consumer to accept drained elements
     * @param limit    the maximum number of elements to drain
     * @return the number of elements drained
     */
    int drain(Consumer<T> consumer, int limit);

    /**
     * Drains all available elements from this queue to the consumer.
     *
     * @param consumer the consumer to accept drained elements
     * @return the number of elements drained
     */
    default int drain(Consumer<T> consumer) {
        return drain(consumer, Integer.MAX_VALUE);
    }

    /**
     * Fills the queue with up to {@code limit} elements from the supplier.
     * The operation stops if the queue becomes full or the limit is reached.
     *
     * @param supplier the supplier to provide new elements
     * @param limit    the maximum number of elements to add
     * @return the number of elements successfully added
     */
    int fill(Supplier<T> supplier, int limit);

    /**
     * Returns the number of elements in this queue.
     *
     * @return the number of elements in this queue
     */
    int size();

    /**
     * Returns the total capacity of this queue.
     *
     * @return the capacity
     */
    int capacity();

    /**
     * @return {@code true} if this queue contains no elements
     */
    boolean isEmpty();

    /**
     * Removes all elements from this queue. Not thread-safe.
     */
    void clear();
}
