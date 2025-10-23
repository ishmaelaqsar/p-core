package dev.aqsar.pcore.collections;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

/**
 * A List implementation that stores objects efficiently with optional pre-allocation.
 * <p>
 * Supports a pool of reusable iterators to reduce allocations in tight loops.
 * Not thread-safe: concurrent modifications must be externally synchronized.
 *
 * @param <E> the type of elements stored in this list
 */
public final class ObjectList<E> extends AbstractList<E> implements List<E>, RandomAccess {

    /**
     * Default initial capacity for new lists.
     */
    public static final int DEFAULT_INITIAL_CAPACITY = 8;

    /**
     * Maximum array size to avoid exceeding {@link Integer#MAX_VALUE}.
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * Number of pooled iterators.
     */
    private static final int ITERATOR_POOL_SIZE = 8;

    /**
     * Array of elements stored in this list.
     */
    private E[] elements;

    /**
     * Number of elements currently in the list.
     */
    private int size = 0;

    /**
     * Optional factory for pre-allocating elements.
     */
    @Nullable
    private final Supplier<E> preAllocationFactory;

    /**
     * Pool of iterators for reuse.
     */
    @Nullable
    private final Object[] iteratorPool;

    /**
     * Bitset tracking available iterators in the pool.
     */
    private long iteratorAvailableBits;

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

    /**
     * Returns a builder for creating a customized ObjectList.
     *
     * @param <E> the element type
     * @return a new Builder instance
     */
    public static <E> Builder<E> builder() {
        return new Builder<>();
    }

    /**
     * Adds all elements from the specified array.
     *
     * @param values the array of elements to add
     */
    public void addAll(final E[] values) {
        addAll(values, 0, values.length);
    }

    /**
     * Adds a range of elements from the specified array.
     *
     * @param values the source array
     * @param offset starting index in the array
     * @param length number of elements to add
     */
    public void addAll(final E[] values, final int offset, final int length) {
        Objects.checkFromIndexSize(offset, length, values.length);
        if (length == 0) {
            return;
        }
        ensureCapacity(size + length);
        System.arraycopy(values, offset, elements, size, length);
        size += length;
    }

    /**
     * Ensures that this list has at least the specified capacity.
     *
     * @param minCapacity minimum required capacity
     */
    public void ensureCapacity(final int minCapacity) {
        if (minCapacity > elements.length) {
            grow(minCapacity);
        }
    }

    /**
     * Borrows a pooled iterator starting at index 0.
     * <p>
     * Returns null if the pool is disabled or exhausted.
     *
     * @return a pooled iterator or null
     */
    @Nullable
    public ObjectListIterator borrowIterator() {
        return borrowIterator(0);
    }

    /**
     * Borrows a pooled iterator starting at the specified index.
     *
     * @param index starting index
     * @return a pooled iterator or null if pool is disabled or exhausted
     */
    @Nullable
    public ObjectListIterator borrowIterator(final int index) {
        Objects.checkIndex(index, size + 1);
        if (iteratorPool == null) {
            return null;
        }

        long bits = iteratorAvailableBits;
        if (bits == 0) {
            return null;
        }

        final int poolIndex = Long.numberOfTrailingZeros(bits);
        iteratorAvailableBits = bits & ~(1L << poolIndex);

        @SuppressWarnings("unchecked") final ObjectListIterator iter =
                ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        iter.reset(index);
        return iter;
    }

    /**
     * Returns a previously borrowed iterator to the pool.
     *
     * @param iterator the iterator to return
     */
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

        iteratorAvailableBits |= (1L << poolIndex);
    }

    /**
     * Returns the number of available pooled iterators.
     */
    public int availableIteratorCount() {
        return iteratorPool == null ? 0 : Long.bitCount(iteratorAvailableBits);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        final int oldSize = size;
        size = 0;
        // Clear references to allow GC (only if not pre-allocated)
        if (preAllocationFactory == null) {
            Arrays.fill(elements, 0, oldSize, null);
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
            throw new UnsupportedOperationException("Use addPreAllocated() when pre-allocation is enabled");
        }
        if (size == elements.length) {
            grow();
        }
        elements[size++] = element;
        return true;
    }

    /**
     * Adds a pre-allocated element from the factory.
     *
     * @return the pre-allocated element
     */
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
        // Clear reference to allow GC (only if not pre-allocated)
        if (preAllocationFactory == null) {
            elements[size] = null;
        }
        return oldValue;
    }

    @Override
    public int indexOf(@Nullable final Object o) {
        final E[] els = elements;
        final int s = size;

        if (o == null) {
            for (int i = 0; i < s; i++) {
                if (els[i] == null) {
                    return i;
                }
            }
        } else {
            for (int i = 0; i < s; i++) {
                if (o.equals(els[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public boolean contains(@Nullable final Object o) {
        return indexOf(o) >= 0;
    }

    /**
     * Identity-based index lookup (reference equality).
     * This is faster than indexOf() because it uses == instead of equals().
     */
    public int indexOfIdentity(@Nullable final Object o) {
        final E[] els = elements;
        final int s = size;

        // Simple linear search with identity comparison
        for (int i = 0; i < s; i++) {
            if (els[i] == o) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Checks identity-based containment.
     */
    public boolean containsIdentity(@Nullable final Object o) {
        return indexOfIdentity(o) >= 0;
    }

    @Override
    public ListIterator<E> listIterator(final int index) {
        @Nullable final ObjectListIterator it = borrowIterator(index);
        if (it == null) {
            return new ObjectListIterator(-1);
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
        if (oldCapacity > 1024 * 1024) {
            newCapacity = oldCapacity + (oldCapacity >> 1);
        }
        if (newCapacity < minCapacity) {
            newCapacity = minCapacity;
        }
        if (newCapacity < 0 || newCapacity > MAX_ARRAY_SIZE) {
            if (oldCapacity == MAX_ARRAY_SIZE) {
                throw new OutOfMemoryError("ObjectList size limit exceeded");
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

    /**
     * Builder for creating ObjectList instances.
     */
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
            return new ObjectList<>(initialCapacity, preAllocationFactory, enableIteratorPool);
        }
    }

    /**
     * Pool entry wrapper for storing iterators.
     */
    private final class IteratorPoolEntry {
        final ObjectListIterator iterator;

        IteratorPoolEntry(final int poolIndex) {
            this.iterator = new ObjectListIterator(poolIndex);
        }
    }

    /**
     * Iterator over ObjectList elements.
     * <p>
     * Supports pooling to reduce allocation overhead.
     */
    public final class ObjectListIterator implements ListIterator<E>, AutoCloseable {
        // Pad to avoid false sharing between pooled iterators (56 bytes = 7 longs)
        private long p0, p1, p2, p3, p4, p5, p6;

        // Hot fields accessed during iteration (12 bytes = 3 ints)
        private final int poolIndex;
        private int cursor;
        private int lastRet;

        // Trailing padding to complete cache line (52 bytes to reach 120 bytes total)
        private long p8, p9, p10, p11, p12, p13;

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
                throw new IllegalStateException("Cannot set before calling next() or previous()");
            }
            if (preAllocationFactory != null) {
                throw new UnsupportedOperationException("Cannot set when pre-allocation is enabled");
            }
            elements[lastRet] = value;
        }

        @Override
        public void add(final E element) {
            if (preAllocationFactory != null) {
                throw new UnsupportedOperationException("Cannot add when pre-allocation is enabled");
            }

            final int i = cursor;
            if (size == elements.length) {
                grow();
            }

            // Shift elements to make room
            if (i < size) {
                System.arraycopy(elements, i, elements, i + 1, size - i);
            }

            elements[i] = element;
            size++;
            cursor = i + 1;
            lastRet = -1; // Invalidate for set/remove
        }

        @Override
        public void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException("Cannot remove before calling next() or previous()");
            }

            // Shift elements left
            final int numMoved = size - lastRet - 1;
            if (numMoved > 0) {
                System.arraycopy(elements, lastRet + 1, elements, lastRet, numMoved);
            }

            size--;

            // Clear reference to allow GC (only if not pre-allocated)
            if (preAllocationFactory == null) {
                elements[size] = null;
            }

            // Adjust cursor if we removed before it
            if (lastRet < cursor) {
                cursor--;
            }

            lastRet = -1; // Invalidate for set/remove
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
    }
}