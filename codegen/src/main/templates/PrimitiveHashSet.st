package dev.aqsar.pcore.collections;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * A high-performance set implementation for #primitive# values that avoids boxing and allocations.
 * Uses open addressing with linear probing for collision resolution.
 * <p>
 * For floating point types (float/double):
 * - Uses bitwise equality (Double.doubleToLongBits for doubles, Float.floatToIntBits for floats)
 * - NaN values are supported and treated as equal to each other
 * - +0.0 and -0.0 are treated as the same value
 * <p>
 * This class is <strong>not thread-safe</strong>. Concurrent modifications must be externally synchronized.
 * <p>
 * Usage example:
 * <pre>{@code
 * #upper#HashSet set = #upper#HashSet.builder()
 *      .initialCapacity(16)
 *      .build();
 *
 * set.add(42);
 * set.add(7);
 *
 * // Check containment
 * boolean contains = set.contains(42);
 *
 * // Using pooled iterator for repeated iteration
 * #upper#HashSet.#upper#HashSetIterator iter = set.borrowIterator();
 * try {
 *     while (iter.hasNext()) {
 *         #primitive# v = iter.next#upper#();
 *         // process
 *     }
 * } finally {
 *     iter.close(); // returns iterator to pool
 * }
 * }</pre>
 *
 * @see java.util.Set
 */
public final class #upper#HashSet extends AbstractSet<#boxed#> implements Set<#boxed#> {

    public static final int DEFAULT_INITIAL_CAPACITY = 16;
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private static final int MAX_CAPACITY = 1 << 30;
    private static final int ITERATOR_POOL_SIZE = 8;

    // Sentinel values for internal state tracking
    // For floating point types, these use specific bit patterns that won't occur naturally
    #sentinel_declarations#

    // Hot fields - accessed on every operation (grouped for cache locality)
    private #primitive#[] elements;
    #state_array_declaration# // For float/double: byte[] to track empty/tombstone state
    private int size = 0;
    private int mask;
    private int threshold;

    // Cold fields - accessed rarely
    private final float loadFactor;
    private int modCount = 0;
    private final IteratorPoolEntry[] iteratorPool;
    private long iteratorAvailableBits;

    private #upper#HashSet(
            final int initialCapacity,
            final float loadFactor,
            final boolean enableIteratorPool
    ) {
        int capacity = 1;
        while (capacity < initialCapacity) capacity <<= 1;

        this.elements = new #primitive#[capacity];
        #state_array_init#; // Initialize state array for float/double

        this.mask = capacity - 1;
        this.loadFactor = loadFactor;
        this.threshold = (int) (capacity * loadFactor);

        if (enableIteratorPool) {
            this.iteratorPool = new IteratorPoolEntry[ITERATOR_POOL_SIZE];
            this.iteratorAvailableBits = (1L << ITERATOR_POOL_SIZE) - 1;
            for (int i = 0; i < ITERATOR_POOL_SIZE; i++) {
                iteratorPool[i] = new IteratorPoolEntry(i);
            }
        } else {
            this.iteratorPool = null;
            this.iteratorAvailableBits = 0L;
        }
    }

    public static Builder builder() { return new Builder(); }

    // ------------------- Primitive Operations -------------------

    /**
     * Adds a primitive value to the set.
     *
     * @param value the value to add
     * @return {@code true} if the value was added (wasn't already present), {@code false} otherwise
     */
    public boolean add(final #primitive# value) {
        if (size >= threshold) resize();

        final int slot = findSlotForPut(value);
        final boolean isNew = isSlotEmptyOrTombstone(slot);

        elements[slot] = value;
        markSlotOccupied(slot);

        if (isNew) {
            size++;
            modCount++;
        }
        return isNew;
    }

    /**
     * Checks if the set contains a primitive value.
     *
     * @param value the value to check
     * @return {@code true} if the value is present, {@code false} otherwise
     */
    public boolean contains(final #primitive# value) {
        return findSlotForGet(value) >= 0;
    }

    /**
     * Removes a primitive value from the set.
     *
     * @param value the value to remove
     * @return {@code true} if the value was present and removed, {@code false} otherwise
     */
    public boolean remove(final #primitive# value) {
        final int slot = findSlotForGet(value);
        if (slot >= 0) {
            markSlotTombstone(slot);
            size--;
            modCount++;
            return true;
        }
        return false;
    }

    /**
     * Applies a consumer to all values in the set.
     *
     * @param action the consumer to apply
     * @throws NullPointerException if the action is null
     */
    public void forEach#upper#(final #upper#Consumer action) {
        Objects.requireNonNull(action);

        final #primitive#[] els = elements;
        for (int i = 0; i < els.length; i++) {
            if (isSlotOccupied(i)) {
                action.accept(els[i]);
            }
        }
    }

    /**
     * Returns a new array containing all elements of the set.
     *
     * @return an array copy of the set elements
     */
    public #primitive#[] to#upper#Array() {
        final #primitive#[] result = new #primitive#[size];
        int idx = 0;
        for (int i = 0; i < elements.length; i++) {
            if (isSlotOccupied(i)) {
                result[idx++] = elements[i];
            }
        }
        return result;
    }

    /**
     * Ensures the set has at least the specified capacity, growing internal storage if needed.
     *
     * @param minCapacity the minimum capacity to ensure
     */
    public void ensureCapacity(final int minCapacity) {
        if (minCapacity > elements.length) {
            resize(minCapacity);
        }
    }

    /**
     * Borrows a pooled iterator.
     * <p>
     * Example:
     * <pre>{@code
     * #upper#HashSet.#upper#HashSetIterator iter = set.borrowIterator();
     * try {
     *     while (iter.hasNext()) {
     *         #primitive# v = iter.next#upper#();
     *     }
     * } finally {
     *     iter.close();
     * }
     * }</pre>
     *
     * @return a pooled iterator or {@code null} if pool is disabled or exhausted
     */
    public #upper#HashSetIterator borrowIterator() {
        if (iteratorPool == null) return null;

        long bits = iteratorAvailableBits;
        if (bits == 0) return null;

        final int poolIndex = Long.numberOfTrailingZeros(bits);
        iteratorAvailableBits = bits & ~(1L << poolIndex);

        final #upper#HashSetIterator iter = iteratorPool[poolIndex].iterator;
        iter.reset();
        return iter;
    }

    /**
     * Returns a previously borrowed iterator to the pool.
     *
     * @param iterator the iterator to return
     */
    public void returnIterator(final #upper#HashSetIterator iterator) {
        if (iteratorPool == null || iterator == null) return;
        final int poolIndex = iterator.poolIndex;
        if (poolIndex < 0 || poolIndex >= ITERATOR_POOL_SIZE) return;
        if (iteratorPool[poolIndex].iterator != iterator) return;

        iteratorAvailableBits |= (1L << poolIndex);
    }

    /**
     * Returns the number of available pooled iterators.
     *
     * @return the count of free iterators in the pool
     */
    public int availableIteratorCount() {
        return iteratorPool == null ? 0 : Long.bitCount(iteratorAvailableBits);
    }

    @FunctionalInterface
    public interface #upper#Consumer {
        void accept(#primitive# value);
    }

    // ------------------- Helper Methods for State Management -------------------

    private boolean isValueEqual(final #primitive# v1, final #primitive# v2) {
        #value_equals#
    }

    private boolean isSlotEmpty(final int slot) {
        return #is_empty#;
    }

    private boolean isSlotOccupied(final int slot) {
        return #is_occupied#;
    }

    private boolean isSlotEmptyOrTombstone(final int slot) {
        return #is_empty_or_tombstone#;
    }

    private void markSlotOccupied(final int slot) {
        #mark_occupied#;
    }

    private void markSlotTombstone(final int slot) {
        #mark_tombstone#;
    }

    // ------------------- Set Interface -------------------

    /**
     * Returns the number of elements in the set.
     *
     * @return the set size
     */
    @Override
    public int size() { return size; }

    /**
     * Returns whether the set is empty.
     *
     * @return {@code true} if empty, {@code false} otherwise
     */
    @Override
    public boolean isEmpty() { return size == 0; }

    /**
     * Removes all elements from the set.
     */
    @Override
    public void clear() {
        if (size > 0) {
            #clear_state#;
            size = 0;
            modCount++;
        }
    }

    /**
     * Adds a boxed element to the set.
     *
     * @param element the element to add
     * @return {@code true} if added, {@code false} if already present
     */
    @Override
    public boolean add(final #boxed# element) {
        if (element == null) {
            throw new NullPointerException("Null values are not supported");
        }
        return add((#primitive#) element);
    }

    /**
     * Checks if the set contains the specified object.
     *
     * @param o the object to check
     * @return {@code true} if present, {@code false} otherwise
     */
    @Override
    public boolean contains(final Object o) {
        if (!(o instanceof #boxed#)) return false;
        return contains((#primitive#) o);
    }

    /**
     * Removes the specified object from the set.
     *
     * @param o the object to remove
     * @return {@code true} if removed, {@code false} if not present
     */
    @Override
    public boolean remove(final Object o) {
        if (!(o instanceof #boxed#)) return false;
        return remove((#primitive#) o);
    }

    /**
     * Returns an iterator over the set elements.
     * <p>
     * This iterator is <strong>not pooled</strong> and allocates a new instance.
     *
     * @return a boxed iterator
     */
    @Override
    public Iterator<#boxed#> iterator() {
        return new BoxedIterator();
    }

    // ------------------- Iterator Pool -------------------

    private final class IteratorPoolEntry {
        final #upper#HashSetIterator iterator;

        IteratorPoolEntry(final int poolIndex) {
            this.iterator = new #upper#HashSetIterator(poolIndex);
        }
    }

    public final class #upper#HashSetIterator implements AutoCloseable {
        // Pad to avoid false sharing between pooled iterators (56 bytes = 7 longs)
        private long p0, p1, p2, p3, p4, p5, p6;

        // Hot fields accessed every iteration
        private final int poolIndex;
        private int cursor;
        private int nextOccupiedSlot;
        private int expectedModCount;
        private int lastReturnedIndex;

        // Trailing padding to complete cache line
        private long p8, p9, p10, p11, p12;

        private #upper#HashSetIterator(final int poolIndex) {
            this.poolIndex = poolIndex;
            reset();
        }

        void reset() {
            this.cursor = 0;
            this.nextOccupiedSlot = -1;
            this.expectedModCount = modCount;
            this.lastReturnedIndex = -1;
            advanceToNextOccupied();
        }

        private void advanceToNextOccupied() {
            while (cursor < elements.length) {
                if (isSlotOccupied(cursor)) {
                    nextOccupiedSlot = cursor;
                    return;
                }
                cursor++;
            }
            nextOccupiedSlot = -1;
        }

        public boolean hasNext() {
            return nextOccupiedSlot >= 0;
        }

        public #primitive# next() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            if (nextOccupiedSlot < 0) {
                throw new NoSuchElementException();
            }

            lastReturnedIndex = nextOccupiedSlot;
            final #primitive# value = elements[nextOccupiedSlot];
            cursor = nextOccupiedSlot + 1;
            advanceToNextOccupied();
            return value;
        }

        public #primitive# peekNext() {
            if (nextOccupiedSlot < 0) {
                throw new NoSuchElementException();
            }
            return elements[nextOccupiedSlot];
        }

        public void remove() {
            if (lastReturnedIndex < 0) {
                throw new IllegalStateException("next() has not been called or remove() already called");
            }

            markSlotTombstone(lastReturnedIndex);
            size--;
            modCount++;
            expectedModCount = modCount;
            lastReturnedIndex = -1;
        }

        public void forEachRemaining(final #upper#Consumer action) {
            Objects.requireNonNull(action);

            final #primitive#[] els = elements;

            // Process pre-computed next slot if available
            if (nextOccupiedSlot >= 0) {
                action.accept(els[nextOccupiedSlot]);
                cursor = nextOccupiedSlot + 1;
            }

            // Continue from cursor
            while (cursor < els.length) {
                if (isSlotOccupied(cursor)) {
                    action.accept(els[cursor]);
                }
                cursor++;
            }
            nextOccupiedSlot = -1;
        }

        @Override
        public void close() {
            #upper#HashSet.this.returnIterator(this);
        }
    }

    // ------------------- Boxed Iterator -------------------

    private final class BoxedIterator implements Iterator<#boxed#> {
        int cursor = 0;
        int lastReturnedIndex = -1;
        int expectedModCount = modCount;

        @Override
        public boolean hasNext() {
            while (cursor < elements.length) {
                if (isSlotOccupied(cursor)) {
                    return true;
                }
                cursor++;
            }
            return false;
        }

        @Override
        public #boxed# next() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }

            while (cursor < elements.length) {
                if (isSlotOccupied(cursor)) {
                    lastReturnedIndex = cursor;
                    return elements[cursor++];
                }
                cursor++;
            }
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            if (lastReturnedIndex < 0) {
                throw new IllegalStateException();
            }
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }

            markSlotTombstone(lastReturnedIndex);
            size--;
            modCount++;
            expectedModCount = modCount;
            lastReturnedIndex = -1;
        }
    }

    // ------------------- Hashing and Probing -------------------

    private int findSlotForPut(final #primitive# value) {
        int slot = hash(value) & mask;

        // Linear probing
        while (true) {
            if (isSlotEmptyOrTombstone(slot)) {
                return slot;
            }
            if (isValueEqual(elements[slot], value)) {
                return slot;
            }
            slot = (slot + 1) & mask;
        }
    }

    private int findSlotForGet(final #primitive# value) {
        int slot = hash(value) & mask;

        // Linear probing
        while (true) {
            if (isSlotEmpty(slot)) {
                return -1;
            }
            if (isSlotOccupied(slot) && isValueEqual(elements[slot], value)) {
                return slot;
            }
            slot = (slot + 1) & mask;
        }
    }

    private int hash(final #primitive# value) {
        #hash_implementation#
    }

    private void resize() {
        resize(elements.length << 1);
    }

    private void resize(int newCapacity) {
        // Ensure capacity is power of 2
        int capacity = 1;
        while (capacity < newCapacity) capacity <<= 1;
        newCapacity = capacity;

        if (newCapacity > MAX_CAPACITY) {
            throw new OutOfMemoryError("#upper#HashSet capacity exceeded");
        }

        final #primitive#[] oldElements = elements;
        #save_old_state#;

        elements = new #primitive#[newCapacity];
        #state_array_init_resize#;

        mask = newCapacity - 1;
        threshold = (int) (newCapacity * loadFactor);
        size = 0;

        // Rehash all entries
        for (int i = 0; i < oldElements.length; i++) {
            if (#was_occupied#) {
                final int slot = findSlotForPut(oldElements[i]);
                elements[slot] = oldElements[i];
                markSlotOccupied(slot);
                size++;
            }
        }
    }

    // ------------------- Builder -------------------

    public static class Builder {
        private int initialCapacity = DEFAULT_INITIAL_CAPACITY;
        private float loadFactor = DEFAULT_LOAD_FACTOR;
        private boolean enableIteratorPool = true;

        public Builder initialCapacity(final int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        public Builder loadFactor(final float loadFactor) {
            this.loadFactor = loadFactor;
            return this;
        }

        public Builder disableIteratorPool() {
            this.enableIteratorPool = false;
            return this;
        }

        public #upper#HashSet build() {
            return new #upper#HashSet(initialCapacity, loadFactor, enableIteratorPool);
        }
    }
}
