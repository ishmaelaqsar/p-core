package dev.aqsar.pcore.collections;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * A high-performance list implementation for #primitive# values that avoids boxing and allocations.
 * <p>
 * Features:
 * <ul>
 *   <li>Stores primitive values directly in a contiguous array.</li>
 *   <li>Supports optional {@code nullValue} semantics for boxed access.</li>
 *   <li>Provides an optional pool of reusable iterators to reduce allocation pressure in tight loops.</li>
 *   <li>Random access is O(1).</li>
 * </ul>
 * <p>
 * This class is <strong>not thread-safe</strong>. Concurrent modifications must be externally synchronized.
 * <p>
 * Usage example:
 * <pre>{@code
 * #upper#List list = #upper#List.builder()
 *      .initialCapacity(16)
 *      .nullValue(DEFAULT_NULL_VALUE)
 *      .build();
 *
 * list.add#upper#(42);
 * list.add#upper#(7);
 *
 * // Access primitive values
 * int val = list.get#upper#(0);
 *
 * // Access boxed values (null-safe if nullValue is used)
 * Integer boxed = list.get(0);
 *
 * // Using pooled iterator for repeated iteration
 * #upper#List.#upper#ListIterator iter = list.borrowIterator();
 * try {
 *     while (iter.hasNext()) {
 *         int v = iter.next#upper#();
 *         // modify or process
 *     }
 * } finally {
 *     iter.close(); // returns iterator to pool
 * }
 * }</pre>
 * <p>
 * Builder options:
 * <ul>
 *   <li>{@link Builder\#initialCapacity(int)}: sets initial internal array size.</li>
 *   <li>{@link Builder\#nullValue(#primitive#)}: sets a sentinel value to represent null in boxed view.</li>
 *   <li>{@link Builder\#disableNullValue()}: disables nullValue semantics.</li>
 *   <li>{@link Builder\#disableIteratorPool()}: disables the internal iterator pool.</li>
 * </ul>
 * <p>
 *
 * @see java.util.List
 * @see java.util.RandomAccess
 */
public final class #upper#List extends AbstractList<#boxed#> implements List<#boxed#>, RandomAccess {

    public static final #primitive# DEFAULT_NULL_VALUE = #nullValue#;
    public static final int DEFAULT_INITIAL_CAPACITY = 8;
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    private static final int ITERATOR_POOL_SIZE = 8;
    private static final int CACHE_LINE_LONGS = 8; // 64 bytes / 8 bytes per long

    // Hot fields - accessed on every operation (grouped for cache locality)
    private #primitive#[] elements;
    private int size = 0;

    // Warm fields - accessed frequently but not every operation
    private final boolean useNullValue;
    private final #primitive# nullValue;

    // Cold fields - accessed rarely (iterator pool)
    private final IteratorPoolEntry[] iteratorPool;
    private long iteratorAvailableBits; // Bitset for availability (fits in single register/cache line)

    private #upper#List(final int initialCapacity, final #primitive# nullValue, final boolean useNullValue, final boolean enableIteratorPool) {
        this.elements = new #primitive#[Math.max(1, initialCapacity)];
        this.useNullValue = useNullValue;
        this.nullValue = nullValue;

        if (enableIteratorPool) {
            this.iteratorPool = new IteratorPoolEntry[ITERATOR_POOL_SIZE];
            this.iteratorAvailableBits = 0xFFL; // All 8 bits set (all available)
            for (int i = 0; i < ITERATOR_POOL_SIZE; i++) {
                iteratorPool[i] = new IteratorPoolEntry(i);
            }
        } else {
            this.iteratorPool = null;
            this.iteratorAvailableBits = 0L;
        }
    }

    public static Builder builder() { return new Builder(); }

    /**
     * Returns the primitive value at the specified index.
     *
     * @param index the index of the element to return
     * @return the primitive value at the specified position
     * @throws IndexOutOfBoundsException if the index is out of range
     * @see \#get(int) for boxed access
     */
    public #primitive# get#upper#(final int index) {
        Objects.checkIndex(index, size);
        return elements[index];
    }

    /**
     * Adds a primitive value to the end of the list.
     *
     * @param value the value to add
     */
    public void add#upper#(final #primitive# value) {
        if (size == elements.length) grow();
        elements[size++] = value;
    }

    /**
     * Adds all values from the given array to the end of the list.
     *
     * @param values the array of values to add
     */
    public void addAll#upper#s(final #primitive#[] values) {
        addAll#upper#s(values, 0, values.length);
    }

    /**
     * Adds a range of values from the given array to the end of the list.
     *
     * @param values the source array
     * @param offset starting index in the array
     * @param length number of elements to add
     * @throws IndexOutOfBoundsException if the range is invalid
     */
    public void addAll#upper#s(final #primitive#[] values, final int offset, final int length) {
        Objects.checkFromIndexSize(offset, length, values.length);
        if (length == 0) return;
        ensureCapacity(size + length);
        System.arraycopy(values, offset, elements, size, length);
        size += length;
    }

    /**
     * Replaces the element at the specified index with the given value.
     *
     * @param index the index of the element to replace
     * @param value the new value
     * @return the previous value at the index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public #primitive# set#upper#(final int index, final #primitive# value) {
        Objects.checkIndex(index, size);
        final #primitive# oldValue = elements[index];
        elements[index] = value;
        return oldValue;
    }

    /**
     * Returns the first index of the given value in the list, or -1 if not present.
     *
     * @param value the value to search for
     * @return index of the first occurrence, or -1 if not found
     */
    public int indexOf#upper#(final #primitive# value) {
        final #primitive#[] els = elements;
        final int s = size;
        int i = 0;

        // Process in chunks of 8 for better cache line utilization
        // 8 longs/doubles (64 bytes) or 16 ints (64 bytes) align with cache line boundaries
        final int limit = s - (CACHE_LINE_LONGS - 1);
        for (; i < limit; i += 8) {
            // Manual unrolling helps branch predictor and keeps data in L1 cache
            if (els[i] == value) return i;
            if (els[i + 1] == value) return i + 1;
            if (els[i + 2] == value) return i + 2;
            if (els[i + 3] == value) return i + 3;
            if (els[i + 4] == value) return i + 4;
            if (els[i + 5] == value) return i + 5;
            if (els[i + 6] == value) return i + 6;
            if (els[i + 7] == value) return i + 7;
        }

        // Handle remaining elements
        for (; i < s; i++) {
            if (els[i] == value) return i;
        }
        return -1;
    }

    /**
     * Checks if the list contains the given value.
     *
     * @param value the value to check
     * @return {@code true} if the value is present, {@code false} otherwise
     */
    public boolean contains#upper#(final #primitive# value) {
        return indexOf#upper#(value) >= 0;
    }

    /**
     * Returns a new array containing all elements of the list.
     *
     * @return an array copy of the list elements
     */
    public #primitive#[] to#upper#Array() {
        return Arrays.copyOf(elements, size);
    }

    /**
     * Ensures the list has at least the specified capacity, growing internal storage if needed.
     *
     * @param minCapacity the minimum capacity to ensure
     */
    public void ensureCapacity(final int minCapacity) {
        if (minCapacity > elements.length) {
            grow(minCapacity);
        }
    }

    /**
     * Borrows a pooled iterator starting at index 0.
     * <p>
     * Example:
     * <pre>{@code
     * #upper#List.#upper#ListIterator iter = list.borrowIterator();
     * try {
     *     while (iter.hasNext()) {
     *         int v = iter.next#upper#();
     *     }
     * } finally {
     *     iter.close();
     * }
     * }</pre>
     *
     * @return a pooled iterator or {@code null} if pool is disabled or exhausted
     */
    public #upper#ListIterator borrowIterator() { return borrowIterator(0); }

    /**
     * Borrows a pooled iterator starting at a specific index.
     *
     * @param index starting position for iteration
     * @return a pooled iterator or {@code null} if pool is disabled or exhausted
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public #upper#ListIterator borrowIterator(final int index) {
        Objects.checkIndex(index, size + 1);
        if (iteratorPool == null) return null;

        // Use bitset for O(1) lookup of available iterator
        long bits = iteratorAvailableBits;
        if (bits == 0) return null; // No available iterators

        // Find first set bit (lowest available iterator)
        final int poolIndex = Long.numberOfTrailingZeros(bits);

        // Clear the bit (mark as unavailable)
        iteratorAvailableBits = bits & ~(1L << poolIndex);

        final #upper#ListIterator iter = iteratorPool[poolIndex].iterator;
        iter.reset(index);
        return iter;
    }

    /**
     * Returns a previously borrowed iterator to the pool.
     *
     * @param iterator the iterator to return
     */
    public void returnIterator(final #upper#ListIterator iterator) {
        if (iteratorPool == null || iterator == null) return;
        final int poolIndex = iterator.poolIndex;
        if (poolIndex < 0 || poolIndex >= ITERATOR_POOL_SIZE) return;
        if (iteratorPool[poolIndex].iterator != iterator) return;

        // Set the bit (mark as available)
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

    /**
     * Returns the current size of the list.
     *
     * @return the number of elements
     */
    @Override
    public int size() { return size; }

    /**
     * Clears all elements from the list.
     */
    @Override
    public void clear() { size = 0; }

    /**
     * Returns the boxed element at the specified index.
     * <p>
     * If {@code useNullValue} is enabled, the sentinel {@code nullValue} is converted to {@code null}.
     *
     * @param index the index to access
     * @return the boxed value
     * @throws IndexOutOfBoundsException if index is invalid
     */
    @Override
    public #boxed# get(final int index) {
        final #primitive# value = get#upper#(index);
        return useNullValue && value == nullValue ? null : value;
    }

    /**
     * Adds a boxed element to the list.
     * <p>
     * Converts {@code null} to {@code nullValue} if null semantics are enabled.
     *
     * @param element the element to add
     * @return always {@code true}
     */
    @Override
    public boolean add(final #boxed# element) {
        add#upper#(useNullValue && element == null ? nullValue : element);
        return true;
    }

    /**
     * Replaces the element at the specified index with a boxed value.
     * <p>
     * Converts {@code null} to {@code nullValue} if null semantics are enabled.
     *
     * @param index the index to set
     * @param element the new element
     * @return the previous element (boxed)
     * @throws IndexOutOfBoundsException if index is invalid
     */
    @Override
    public #boxed# set(final int index, final #boxed# element) {
        final #primitive# oldValue = set#upper#(index, useNullValue && element == null ? nullValue : element);
        return useNullValue && oldValue == nullValue ? null : oldValue;
    }

    /**
     * Removes the element at the specified index.
     *
     * @param index the index to remove
     * @return the removed element (boxed)
     * @throws IndexOutOfBoundsException if index is invalid
     */
    @Override
    public #boxed# remove(final int index) {
        Objects.checkIndex(index, size);
        final #primitive# oldValue = elements[index];
        final int numMoved = size - index - 1;
        if (numMoved > 0) System.arraycopy(elements, index + 1, elements, index, numMoved);
        size--;
        return useNullValue && oldValue == nullValue ? null : oldValue;
    }

    /**
     * Returns the index of the first occurrence of the specified object.
     *
     * @param o the object to search for
     * @return the index of the first occurrence, or -1 if not found
     */
    @Override
    public int indexOf(final Object o) {
        if (useNullValue && o == null) return indexOf#upper#(nullValue);
        if (o instanceof #boxed#) return indexOf#upper#((#boxed#) o);
        return -1;
    }

    /**
     * Checks if the list contains the specified object.
     *
     * @param o the object to check
     * @return {@code true} if the object is present, {@code false} otherwise
     */
    @Override
    public boolean contains(final Object o) { return indexOf(o) >= 0; }

    /**
     * Returns a boxed {@link ListIterator} starting at a given index.
     * <p>
     * This iterator is <strong>not pooled</strong> and allocates a new instance.
     *
     * @param index starting index
     * @return a boxed {@link ListIterator}
     * @throws IndexOutOfBoundsException if index is invalid
     */
    @Override
    public ListIterator<#boxed#> listIterator(final int index) { return new BoxedIteratorWrapper(index); }

    /**
     * Returns a boxed {@link ListIterator} starting at index 0.
     * <p>
     * This iterator is <strong>not pooled</strong> and allocates a new instance.
     *
     * @return a boxed {@link ListIterator}
     */
    @Override
    public ListIterator<#boxed#> listIterator() { return listIterator(0); }

    private void grow() {
        grow(elements.length + 1);
    }

    private void grow(final int minCapacity) {
        final int oldCapacity = elements.length;
        int newCapacity = oldCapacity << 1;

        // Switch to 1.5x growth for large arrays to reduce memory waste
        // and improve cache utilization
        if (oldCapacity > 1024 * 1024) {
            newCapacity = oldCapacity + (oldCapacity >> 1);
        }

        if (newCapacity < minCapacity) {
            newCapacity = minCapacity;
        }

        if (newCapacity < 0 || newCapacity > MAX_ARRAY_SIZE) {
            if (oldCapacity == MAX_ARRAY_SIZE) {
                throw new OutOfMemoryError("#upper#List size limit exceeded");
            }
            newCapacity = minCapacity > MAX_ARRAY_SIZE ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
        }
        elements = Arrays.copyOf(elements, newCapacity);
    }

    public static class Builder {
        private #primitive# nullValue = DEFAULT_NULL_VALUE;
        private int initialCapacity = DEFAULT_INITIAL_CAPACITY;
        private boolean useNullValue = true;
        private boolean enableIteratorPool = true;

        public Builder nullValue(final #primitive# nullValue) {
            this.nullValue = nullValue;
            this.useNullValue = true;
            return this;
        }

        public Builder disableNullValue() {
            this.useNullValue = false;
            return this;
        }

        public Builder initialCapacity(final int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        public Builder disableIteratorPool() {
            this.enableIteratorPool = false;
            return this;
        }

        public #upper#List build() {
            return new #upper#List(initialCapacity, nullValue, useNullValue, enableIteratorPool);
        }
    }

    // Pool entry wrapper to keep iterator and metadata together in memory
    private final class IteratorPoolEntry {
        final #upper#ListIterator iterator;

        IteratorPoolEntry(final int poolIndex) {
            this.iterator = new #upper#ListIterator(poolIndex);
        }
    }

    public final class #upper#ListIterator implements AutoCloseable {
        // Pad to avoid false sharing between pooled iterators
        private long p0, p1, p2, p3, p4, p5, p6;

        private final int poolIndex;
        private int cursor;
        private int lastRet;

        // More padding to ensure this iterator doesn't share cache lines
        private long p8, p9, p10, p11, p12, p13, p14;

        private #upper#ListIterator(final int poolIndex) {
            this.poolIndex = poolIndex;
            reset(0);
        }

        void reset(final int index) {
            this.cursor = index;
            this.lastRet = -1;
        }

        public boolean hasNext() { return cursor < size; }
        public boolean hasPrevious() { return cursor > 0; }

        public #primitive# next#upper#() {
            if (cursor >= size) throw new NoSuchElementException();
            lastRet = cursor;
            return elements[cursor++];
        }

        public #primitive# previous#upper#() {
            if (cursor <= 0) throw new NoSuchElementException();
            lastRet = --cursor;
            return elements[cursor];
        }

        public void set#upper#(final #primitive# value) {
            if (lastRet < 0) throw new IllegalStateException();
            elements[lastRet] = value;
        }

        public int nextIndex() { return cursor; }
        public int previousIndex() { return cursor - 1; }

        @Override
        public void close() {
            #upper#List.this.returnIterator(this);
        }
    }

    private final class BoxedIteratorWrapper implements ListIterator<#boxed#> {
        private int cursor;
        private int lastRet = -1;

        private BoxedIteratorWrapper(final int index) { this.cursor = index; }

        @Override public boolean hasNext() { return cursor < size; }
        @Override public boolean hasPrevious() { return cursor > 0; }

        @Override
        public #boxed# next() {
            if (cursor >= size) throw new NoSuchElementException();
            lastRet = cursor;
            final #primitive# value = elements[cursor++];
            return useNullValue && value == nullValue ? null : value;
        }

        @Override
        public #boxed# previous() {
            if (cursor <= 0) throw new NoSuchElementException();
            lastRet = --cursor;
            final #primitive# value = elements[cursor];
            return useNullValue && value == nullValue ? null : value;
        }

        @Override public int nextIndex() { return cursor; }
        @Override public int previousIndex() { return cursor - 1; }

        @Override
        public void set(final #boxed# element) {
            if (lastRet < 0) throw new IllegalStateException();
            #upper#List.this.set(lastRet, element);
        }

        @Override
        public void add(final #boxed# element) {
            throw new UnsupportedOperationException("add not supported");
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove not supported");
        }
    }
}
