package dev.aqsar.pcore.collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

/**
 * A List implementation that stores #primitive# values without boxing to achieve high performance and avoid allocations.
 * This class is not thread-safe.
 */
public final class ObjectList<E> extends AbstractList<E> implements List<E>, RandomAccess {

    public static final int DEFAULT_INITIAL_CAPACITY = 8;
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    private static final int ITERATOR_POOL_SIZE = 8;
    private static final int CACHE_LINE_LONGS = 8; // 64 bytes / 8 bytes per long

    // Hot fields - accessed on every operation (grouped for cache locality)
    private E[] elements;
    private int size = 0;

    // Warm fields - accessed frequently but not every operation
    @Nullable
    private final Supplier<E> preAllocationFactory;

    // Cold fields - accessed rarely (iterator pool)
    @Nullable
    private final Object[] iteratorPool;
    private long iteratorAvailableBits; // Bitset for availability (fits in single register/cache line)

    @SuppressWarnings("unchecked")
    private ObjectList(final int initialCapacity,
                       @Nullable final Supplier<E> preAllocationFactory,
                       final boolean enableIteratorPool) {
        this.elements = (E[]) new Object[Math.max(1, initialCapacity)];
        this.preAllocationFactory = preAllocationFactory;

        if (preAllocationFactory != null) {
            for (int i = 0; i < elements.length; i++) {
                elements[i] = preAllocationFactory.get();
            }
        }

        if (enableIteratorPool) {
            this.iteratorPool = new Object[ITERATOR_POOL_SIZE];
            this.iteratorAvailableBits = 0xFFL; // All 8 bits set (all available)
            for (int i = 0; i < ITERATOR_POOL_SIZE; i++) {
                iteratorPool[i] = new IteratorPoolEntry(i);
            }
        } else {
            this.iteratorPool = null;
            this.iteratorAvailableBits = 0L;
        }
    }

    public static <E> Builder<E> builder() {
        return new Builder<>();
    }

    public void addAll(final E[] values) {
        addAll(values, 0, values.length);
    }

    public void addAll(final E[] values, final int offset, final int length) {
        Objects.checkFromIndexSize(offset, length, values.length);
        if (length == 0) {
            return;
        }
        ensureCapacity(size + length);
        System.arraycopy(values, offset, elements, size, length);
        size += length;
    }

    public void ensureCapacity(final int minCapacity) {
        if (minCapacity > elements.length) {
            grow(minCapacity);
        }
    }

    @Nullable
    public ObjectListIterator borrowIterator() {
        return borrowIterator(0);
    }

    @Nullable
    public ObjectListIterator borrowIterator(final int index) {
        Objects.checkIndex(index, size + 1);
        if (iteratorPool == null) {
            return null;
        }

        // Use bitset for O(1) lookup of available iterator
        long bits = iteratorAvailableBits;
        if (bits == 0) {
            return null; // No available iterators
        }

        // Find first set bit (lowest available iterator)
        final int poolIndex = Long.numberOfTrailingZeros(bits);

        // Clear the bit (mark as unavailable)
        iteratorAvailableBits = bits & ~(1L << poolIndex);

        @SuppressWarnings("unchecked") final ObjectListIterator iter =
                ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        iter.reset(index);
        return iter;
    }

    public void returnIterator(@Nullable final ObjectListIterator iterator) {
        if (iteratorPool == null || iterator == null) {
            return;
        }
        final int poolIndex = iterator.poolIndex;
        if (poolIndex < 0 || poolIndex >= ITERATOR_POOL_SIZE) {
            return;
        }

        @SuppressWarnings("unchecked") final ObjectListIterator iterator_ =
                ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        if (iterator_ != iterator) {
            return;
        }

        // Set the bit (mark as available)
        iteratorAvailableBits |= (1L << poolIndex);
    }

    public int availableIteratorCount() {
        return iteratorPool == null ? 0 : Long.bitCount(iteratorAvailableBits);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        size = 0;
        if (preAllocationFactory == null) {
            Arrays.fill(elements, 0, size, null);
        }
        if (iteratorPool != null) {
            iteratorAvailableBits = 0xFFL;
        }
    }

    @Override
    public E get(final int index) {
        Objects.checkIndex(index, size);
        return elements[index];
    }

    @Override
    public boolean add(@Nullable final E element) {
        if (preAllocationFactory != null) {
            throw new UnsupportedOperationException(
                    "Use addPreAllocated() when pre-allocation is enabled");
        }
        if (size == elements.length) {
            grow();
        }
        elements[size++] = element;
        return true;
    }

    public E addPreAllocated() {
        if (preAllocationFactory == null) {
            throw new UnsupportedOperationException("A pre-allocation factory must be provided");
        }
        if (size == elements.length) {
            grow();
        }
        final E element = elements[size];
        size++;
        return element;
    }

    @Override
    public E set(final int index, final E element) {
        Objects.checkIndex(index, size);
        final E oldValue = elements[index];
        elements[index] = element;
        return oldValue;
    }

    @Override
    public E remove(final int index) {
        Objects.checkIndex(index, size);
        final E oldValue = elements[index];
        final int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(elements, index + 1, elements, index, numMoved);
        }
        size--;
        return oldValue;
    }

    @Override
    public int indexOf(@Nullable final Object o) {
        final E[] els = elements;
        final int s = size;
        int i = 0;

        // Process in chunks of 8 for better cache line utilization
        // 8 longs/doubles (64 bytes) or 16 ints (64 bytes) align with cache line boundaries
        final int limit = s - (CACHE_LINE_LONGS - 1);
        for (; i < limit; i += 8) {
            // Manual unrolling helps branch predictor and keeps data in L1 cache
            if (Objects.equals(els[i], o)) {
                return i;
            }
            if (Objects.equals(els[i + 1], o)) {
                return i + 1;
            }
            if (Objects.equals(els[i + 2], o)) {
                return i + 2;
            }
            if (Objects.equals(els[i + 3], o)) {
                return i + 3;
            }
            if (Objects.equals(els[i + 4], o)) {
                return i + 4;
            }
            if (Objects.equals(els[i + 5], o)) {
                return i + 5;
            }
            if (Objects.equals(els[i + 6], o)) {
                return i + 6;
            }
            if (Objects.equals(els[i + 7], o)) {
                return i + 7;
            }
        }

        // Handle remaining elements
        for (; i < s; i++) {
            if (Objects.equals(els[i], o)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean contains(@Nullable final Object o) {
        return indexOf(o) >= 0;
    }

    public int indexOfIdentity(@Nullable final Object o) {
        final E[] els = elements;
        final int s = size;
        int i = 0;

        // Process in chunks of 8 for better cache line utilization
        // 8 longs/doubles (64 bytes) or 16 ints (64 bytes) align with cache line boundaries
        final int limit = s - (CACHE_LINE_LONGS - 1);
        for (; i < limit; i += 8) {
            // Manual unrolling helps branch predictor and keeps data in L1 cache
            if (els[i] == o) {
                return i;
            }
            if (els[i + 1] == o) {
                return i + 1;
            }
            if (els[i + 2] == o) {
                return i + 2;
            }
            if (els[i + 3] == o) {
                return i + 3;
            }
            if (els[i + 4] == o) {
                return i + 4;
            }
            if (els[i + 5] == o) {
                return i + 5;
            }
            if (els[i + 6] == o) {
                return i + 6;
            }
            if (els[i + 7] == o) {
                return i + 7;
            }
        }

        // Handle remaining elements
        for (; i < s; i++) {
            if (els[i] == o) {
                return i;
            }
        }
        return -1;
    }

    public boolean containsIdentity(@Nullable final Object o) {
        return indexOfIdentity(o) >= 0;
    }

    @Override
    public ListIterator<E> listIterator(final int index) {
        @Nullable final ObjectListIterator it = borrowIterator(index);
        if (it == null) {
            return new ObjectListIterator(-1); // fallback
        }
        return it;
    }

    @Override
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

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

        if (preAllocationFactory != null) {
            for (int i = oldCapacity; i < newCapacity; i++) {
                elements[i] = preAllocationFactory.get();
            }
        }
    }

    public static class Builder<E> {
        private int initialCapacity = DEFAULT_INITIAL_CAPACITY;
        @Nullable
        private Supplier<E> preAllocationFactory = null;
        private boolean enableIteratorPool = true;

        public Builder<E> initialCapacity(final int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        public Builder<E> preAllocate(final Supplier<E> preAllocationFactory) {
            this.preAllocationFactory = preAllocationFactory;
            return this;
        }

        public Builder<E> disableIteratorPool() {
            this.enableIteratorPool = false;
            return this;
        }

        public ObjectList<E> build() {
            return new ObjectList<E>(initialCapacity, preAllocationFactory, enableIteratorPool);
        }
    }

    // Pool entry wrapper to keep iterator and metadata together in memory
    private final class IteratorPoolEntry {
        final ObjectListIterator iterator;

        IteratorPoolEntry(final int poolIndex) {
            this.iterator = new ObjectListIterator(poolIndex);
        }
    }

    public final class ObjectListIterator implements ListIterator<E>, AutoCloseable {
        // Pad to avoid false sharing between pooled iterators
        private long p0, p1, p2, p3, p4, p5, p6;

        private final int poolIndex;
        private int cursor;
        private int lastRet;

        // More padding to ensure this iterator doesn't share cache lines
        private long p8, p9, p10, p11, p12, p13, p14;

        private ObjectListIterator(final int poolIndex) {
            this.poolIndex = poolIndex;
            reset(0);
        }

        void reset(final int index) {
            this.cursor = index;
            this.lastRet = -1;
        }

        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        @Override
        public boolean hasPrevious() {
            return cursor > 0;
        }

        @Override
        public E next() {
            if (cursor >= size) {
                throw new NoSuchElementException();
            }
            lastRet = cursor;
            return elements[cursor++];
        }

        @Override
        public E previous() {
            if (cursor <= 0) {
                throw new NoSuchElementException();
            }
            lastRet = --cursor;
            return elements[cursor];
        }

        @Override
        public void set(final E value) {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }
            elements[lastRet] = value;
        }

        @Override
        public int nextIndex() {
            return cursor;
        }

        @Override
        public int previousIndex() {
            return cursor - 1;
        }

        @Override
        public void close() {
            ObjectList.this.returnIterator(this);
        }

        @Override
        public void add(final E element) {
            throw new UnsupportedOperationException("add not supported");
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove not supported");
        }
    }
}
