package dev.aqsar.pcore.collections;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

/**
 * A high-performance {@link List} implementation optimized for object storage with optional
 * pre-allocation and iterator pooling.
 * <p>
 * This class is designed for performance-critical scenarios where frequent allocations
 * and iterator creation costs must be minimized. It offers:
 * <ul>
 *     <li>Optional pre-allocation of element instances using a {@link Supplier} to avoid object churn.</li>
 *     <li>A small pool of reusable iterators to reduce allocation pressure during tight loops.</li>
 *     <li>Resizable backing array with exponential growth and configurable initial capacity.</li>
 * </ul>
 *
 * <p><strong>Note:</strong> This class is <em>not</em> thread-safe. Concurrent modifications must be
 * externally synchronized.
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
     * Number of pooled iterators to maintain in the internal pool.
     */
    private static final int ITERATOR_POOL_SIZE = 8;

    /**
     * Array storing the list elements.
     */
    private E[] elements;

    /**
     * Number of elements currently stored in the list.
     */
    private int size = 0;

    /**
     * Optional factory for pre-allocating element instances.
     */
    @Nullable
    private final Supplier<E> valueAllocator;

    /**
     * Pool of reusable iterators (if enabled).
     */
    @Nullable
    private final Object[] iteratorPool;

    /**
     * Bitset tracking which iterators in the pool are currently available.
     */
    private long iteratorAvailableBits;

    /**
     * Constructs a new {@code ObjectList} with the specified parameters.
     *
     * @param initialCapacity    the initial size of the internal array
     * @param valueAllocator     optional supplier used for pre-allocating element instances
     * @param enableIteratorPool whether to enable a fixed pool of reusable iterators
     */
    @SuppressWarnings("unchecked")
    private ObjectList(final int initialCapacity,
                       @Nullable final Supplier<E> valueAllocator,
                       final boolean enableIteratorPool) {
        this.elements = (E[]) new Object[Math.max(1, initialCapacity)];
        this.valueAllocator = valueAllocator;

        if (valueAllocator != null) {
            for (int i = 0; i < elements.length; i++) {
                elements[i] = valueAllocator.get();
            }
        }

        if (enableIteratorPool) {
            this.iteratorPool = new Object[ITERATOR_POOL_SIZE];
            this.iteratorAvailableBits = 0xFFL; // all 8 bits set → all available
            for (int i = 0; i < ITERATOR_POOL_SIZE; i++) {
                iteratorPool[i] = new IteratorPoolEntry(i);
            }
        } else {
            this.iteratorPool = null;
            this.iteratorAvailableBits = 0L;
        }
    }

    /**
     * Creates a new {@link Builder} for constructing customized {@code ObjectList} instances.
     *
     * @param <E> the element type
     * @return a new {@link Builder}
     */
    public static <E> Builder<E> builder() {
        return new Builder<>();
    }

    /**
     * Adds all elements from the specified array to this list.
     *
     * @param values the array containing elements to add
     * @throws NullPointerException if {@code values} is {@code null}
     */
    public void addAll(final E[] values) {
        addAll(values, 0, values.length);
    }

    /**
     * Adds a subrange of elements from the specified array to this list.
     *
     * @param values the source array
     * @param offset the starting index in the array
     * @param length the number of elements to add
     * @throws IndexOutOfBoundsException if {@code offset} or {@code length} are invalid
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
     * Ensures the backing array has at least the given capacity.
     * Grows the array if necessary.
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
     * If the iterator pool is disabled or all iterators are in use, returns {@code null}.
     *
     * @return a reusable {@link ObjectListIterator}, or {@code null} if unavailable
     */
    @Nullable
    public ObjectListIterator borrowIterator() {
        return borrowIterator(0);
    }

    /**
     * Borrows a pooled iterator starting at the specified index.
     *
     * @param index the index at which iteration should begin
     * @return a reusable {@link ObjectListIterator}, or {@code null} if unavailable
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
     * Returns a previously borrowed iterator to the pool for reuse.
     *
     * @param iterator the iterator to return, or {@code null} (no-op)
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
     * Returns the number of pooled iterators currently available for borrowing.
     *
     * @return the number of available iterators, or {@code 0} if pooling is disabled
     */
    public int availableIteratorCount() {
        return iteratorPool == null ? 0 : Long.bitCount(iteratorAvailableBits);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return size;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        final int oldSize = size;
        size = 0;
        if (valueAllocator == null) {
            Arrays.fill(elements, 0, oldSize, null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E get(final int index) {
        Objects.checkIndex(index, size);
        return elements[index];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(@Nullable final E element) {
        if (valueAllocator != null) {
            throw new UnsupportedOperationException("Use addPreAllocated() when a value allocator is provided");
        }
        if (size == elements.length) {
            grow();
        }
        elements[size++] = element;
        return true;
    }

    /**
     * Adds a pre-allocated element from the internal factory.
     * <p>
     * Only valid if this list was created with a {@link Supplier} value allocator.
     *
     * @return the pre-allocated element ready for modification
     * @throws UnsupportedOperationException if no allocator is defined
     */
    public E addPreAllocated() {
        if (valueAllocator == null) {
            throw new UnsupportedOperationException("A value allocator must be provided");
        }
        if (size == elements.length) {
            grow();
        }
        final E element = elements[size];
        size++;
        return element;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E set(final int index, final E element) {
        Objects.checkIndex(index, size);
        final E oldValue = elements[index];
        elements[index] = element;
        return oldValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E remove(final int index) {
        Objects.checkIndex(index, size);
        final E oldValue = elements[index];
        final int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(elements, index + 1, elements, index, numMoved);
        }
        size--;
        if (valueAllocator == null) {
            elements[size] = null;
        }
        return oldValue;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(@Nullable final Object o) {
        return indexOf(o) >= 0;
    }

    /**
     * Performs an identity-based index lookup using {@code ==} instead of {@link Object#equals(Object)}.
     *
     * @param o the object to locate
     * @return the index of the object using identity comparison, or {@code -1} if not found
     */
    public int indexOfIdentity(@Nullable final Object o) {
        final E[] els = elements;
        final int s = size;
        for (int i = 0; i < s; i++) {
            if (els[i] == o) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Checks if this list contains the given object by reference (identity-based containment).
     *
     * @param o the object to test
     * @return {@code true} if an element in this list is the same object, otherwise {@code false}
     */
    public boolean containsIdentity(@Nullable final Object o) {
        return indexOfIdentity(o) >= 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ListIterator<E> listIterator(final int index) {
        @Nullable final ObjectListIterator it = borrowIterator(index);
        if (it == null) {
            return new ObjectListIterator(-1);
        }
        return it;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

    /**
     * Grows the internal storage array by one step.
     */
    private void grow() {
        grow(elements.length + 1);
    }

    /**
     * Expands the internal storage array to ensure at least {@code minCapacity} elements.
     */
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
        if (valueAllocator != null) {
            for (int i = oldCapacity; i < newCapacity; i++) {
                elements[i] = valueAllocator.get();
            }
        }
    }

    /**
     * Fluent builder for constructing customized {@link ObjectList} instances.
     *
     * @param <E> the element type
     */
    public static class Builder<E> {
        private int initialCapacity = DEFAULT_INITIAL_CAPACITY;
        @Nullable
        private Supplier<E> preAllocationFactory = null;
        private boolean enableIteratorPool = true;

        /**
         * Sets the initial capacity for the backing array.
         *
         * @param initialCapacity the number of elements the list can initially hold
         * @return this builder
         */
        public Builder<E> initialCapacity(final int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        /**
         * Enables pre-allocation of elements using the provided factory.
         *
         * @param preAllocationFactory a {@link Supplier} creating new element instances
         * @return this builder
         */
        public Builder<E> preAllocate(final Supplier<E> preAllocationFactory) {
            this.preAllocationFactory = preAllocationFactory;
            return this;
        }

        /**
         * Disables iterator pooling, forcing new iterators to be created each time.
         *
         * @return this builder
         */
        public Builder<E> disableIteratorPool() {
            this.enableIteratorPool = false;
            return this;
        }

        /**
         * Builds a new {@link ObjectList} instance using the configured options.
         *
         * @return a new {@code ObjectList}
         */
        public ObjectList<E> build() {
            return new ObjectList<>(initialCapacity, preAllocationFactory, enableIteratorPool);
        }
    }

    /**
     * Internal pool entry used to wrap an {@link ObjectListIterator} with its index.
     */
    private final class IteratorPoolEntry {
        final ObjectListIterator iterator;

        IteratorPoolEntry(final int poolIndex) {
            this.iterator = new ObjectListIterator(poolIndex);
        }
    }

    /**
     * A {@link ListIterator} implementation optimized for use with {@link ObjectList}.
     * <p>
     * Supports pooling to reduce allocation overhead and implements {@link AutoCloseable}
     * so that pooled iterators can be automatically returned to the list when used
     * within a try-with-resources block.
     */
    public final class ObjectListIterator implements ListIterator<E>, AutoCloseable {
        // Cache-line padding to avoid false sharing across pooled iterators
        private long p0, p1, p2, p3, p4, p5, p6;

        /**
         * The pool index for this iterator, or -1 if unpooled.
         */
        private final int poolIndex;

        private int cursor;
        private int lastReturnedIndex;

        // Padding to align memory layout
        private long p8, p9, p10, p11, p12, p13;

        private ObjectListIterator(final int poolIndex) {
            this.poolIndex = poolIndex;
            reset(0);
        }

        /**
         * Resets this iterator to start at the specified index.
         */
        void reset(final int index) {
            this.cursor = index;
            this.lastReturnedIndex = -1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasPrevious() {
            return cursor > 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public E next() {
            if (cursor >= size) {
                throw new NoSuchElementException();
            }
            lastReturnedIndex = cursor;
            return elements[cursor++];
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public E previous() {
            if (cursor <= 0) {
                throw new NoSuchElementException();
            }
            lastReturnedIndex = --cursor;
            return elements[cursor];
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void set(final E value) {
            if (lastReturnedIndex < 0) {
                throw new IllegalStateException("Cannot set before calling next() or previous()");
            }
            if (valueAllocator != null) {
                throw new UnsupportedOperationException("Cannot set when pre-allocation is enabled");
            }
            elements[lastReturnedIndex] = value;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void add(final E element) {
            if (valueAllocator != null) {
                throw new UnsupportedOperationException("Cannot add when pre-allocation is enabled");
            }

            final int i = cursor;
            if (size == elements.length) {
                grow();
            }
            if (i < size) {
                System.arraycopy(elements, i, elements, i + 1, size - i);
            }

            elements[i] = element;
            size++;
            cursor = i + 1;
            lastReturnedIndex = -1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void remove() {
            if (lastReturnedIndex < 0) {
                throw new IllegalStateException("Cannot remove before calling next() or previous()");
            }

            final int numMoved = size - lastReturnedIndex - 1;
            if (numMoved > 0) {
                System.arraycopy(elements, lastReturnedIndex + 1, elements, lastReturnedIndex, numMoved);
            }

            size--;
            if (valueAllocator == null) {
                elements[size] = null;
            }
            if (lastReturnedIndex < cursor) {
                cursor--;
            }
            lastReturnedIndex = -1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int nextIndex() {
            return cursor;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int previousIndex() {
            return cursor - 1;
        }

        /**
         * Returns this iterator to its pool, making it available for reuse.
         * <p>
         * This method is automatically invoked if the iterator is used within a
         * try-with-resources block.
         */
        @Override
        public void close() {
            ObjectList.this.returnIterator(this);
        }
    }
}
