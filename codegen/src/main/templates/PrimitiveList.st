package dev.aqsar.pcore.collections;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * A List implementation that stores #primitive# values without boxing to achieve high performance and avoid allocations.
 * This class is not thread-safe.
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

    public #primitive# get#upper#(final int index) {
        Objects.checkIndex(index, size);
        return elements[index];
    }

    public void add#upper#(final #primitive# value) {
        if (size == elements.length) grow();
        elements[size++] = value;
    }

    public void addAll#upper#(final #primitive#[] values) {
        addAll#upper#(values, 0, values.length);
    }

    public void addAll#upper#(final #primitive#[] values, final int offset, final int length) {
        Objects.checkFromIndexSize(offset, length, values.length);
        if (length == 0) return;
        ensureCapacity(size + length);
        System.arraycopy(values, offset, elements, size, length);
        size += length;
    }

    public #primitive# set#upper#(final int index, final #primitive# value) {
        Objects.checkIndex(index, size);
        final #primitive# oldValue = elements[index];
        elements[index] = value;
        return oldValue;
    }

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

    public boolean contains#upper#(final #primitive# value) {
        return indexOf#upper#(value) >= 0;
    }

    public #primitive#[] to#upper#Array() {
        return Arrays.copyOf(elements, size);
    }

    public void ensureCapacity(final int minCapacity) {
        if (minCapacity > elements.length) {
            grow(minCapacity);
        }
    }

    public #upper#ListIterator borrowIterator() { return borrowIterator(0); }

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

    public void returnIterator(final #upper#ListIterator iterator) {
        if (iteratorPool == null || iterator == null) return;
        final int poolIndex = iterator.poolIndex;
        if (poolIndex < 0 || poolIndex >= ITERATOR_POOL_SIZE) return;
        if (iteratorPool[poolIndex].iterator != iterator) return;

        // Set the bit (mark as available)
        iteratorAvailableBits |= (1L << poolIndex);
    }

    public int availableIteratorCount() {
        return iteratorPool == null ? 0 : Long.bitCount(iteratorAvailableBits);
    }

    @Override
    public int size() { return size; }

    @Override
    public void clear() { size = 0; }

    @Override
    public #boxed# get(final int index) {
        final #primitive# value = get#upper#(index);
        return useNullValue && value == nullValue ? null : value;
    }

    @Override
    public boolean add(final #boxed# element) {
        add#upper#(useNullValue && element == null ? nullValue : element);
        return true;
    }

    @Override
    public #boxed# set(final int index, final #boxed# element) {
        final #primitive# oldValue = set#upper#(index, useNullValue && element == null ? nullValue : element);
        return useNullValue && oldValue == nullValue ? null : oldValue;
    }

    @Override
    public #boxed# remove(final int index) {
        Objects.checkIndex(index, size);
        final #primitive# oldValue = elements[index];
        final int numMoved = size - index - 1;
        if (numMoved > 0) System.arraycopy(elements, index + 1, elements, index, numMoved);
        size--;
        return useNullValue && oldValue == nullValue ? null : oldValue;
    }

    @Override
    public int indexOf(final Object o) {
        if (useNullValue && o == null) return indexOf#upper#(nullValue);
        if (o instanceof #boxed#) return indexOf#upper#((#boxed#) o);
        return -1;
    }

    @Override
    public boolean contains(final Object o) { return indexOf(o) >= 0; }

    @Override
    public ListIterator<#boxed#> listIterator(final int index) { return new BoxedIteratorWrapper(index); }

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
