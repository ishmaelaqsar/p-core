package dev.aqsar.pcore.collections;

import java.util.*;
import java.util.ConcurrentModificationException;

/**
 * An allocation-free Red-Black Tree implementation for #primitive# values that avoids boxing.
 * Uses array-based node storage with a free list to eliminate allocations after initial capacity.
 * Provides O(log n) operations for add, contains, remove and maintains sorted order.
 * <p>
 * For floating point keys (float/double):
 * - Uses bitwise comparison (Double.compare/Float.compare)
 * - NaN values are supported and treated consistently
 * - +0.0 and -0.0 are treated as equal
 * <p>
 * This class is not thread-safe.
 */
public final class #upper#TreeSet extends AbstractSet<#boxed#> implements NavigableSet<#boxed#> {

    public static final int DEFAULT_INITIAL_CAPACITY = 16;

    private static final boolean RED = true;
    private static final boolean BLACK = false;
    private static final int NULL_NODE = -1;
    private static final int ITERATOR_POOL_SIZE = 8;

    // Node storage arrays (structure of arrays for cache efficiency)
    private #primitive#[] keys;
    private int[] leftChild;
    private int[] rightChild;
    private int[] parent;
    private boolean[] color;

    // Tree state
    private int root = NULL_NODE;
    private int size = 0;
    private int capacity;
    private int modCount = 0;

    // Free list for recycling nodes
    private int freeListHead = NULL_NODE;

    // Iterator pool
    private final Object[] iteratorPool;
    private long iteratorAvailableBits;

    @SuppressWarnings("unchecked")
    private #upper#TreeSet(
            final int initialCapacity,
            final boolean enableIteratorPool
    ) {
        this.capacity = Math.max(initialCapacity, DEFAULT_INITIAL_CAPACITY);

        this.keys = new #primitive#[capacity];
        this.leftChild = new int[capacity];
        this.rightChild = new int[capacity];
        this.parent = new int[capacity];
        this.color = new boolean[capacity];

        initializeFreeList();

        if (enableIteratorPool) {
            this.iteratorPool = new Object[ITERATOR_POOL_SIZE];
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

    private void initializeFreeList() {
        for (int i = 0; i < capacity - 1; i++) {
            rightChild[i] = i + 1;
        }
        rightChild[capacity - 1] = NULL_NODE;
        freeListHead = 0;
    }

    // ------------------- Node Management -------------------

    private int allocateNode(final #primitive# key, final int parentNode) {
        int nodeIndex;

        if (freeListHead != NULL_NODE) {
            nodeIndex = freeListHead;
            freeListHead = rightChild[freeListHead];
        } else {
            grow();
            return allocateNode(key, parentNode);
        }

        keys[nodeIndex] = key;
        leftChild[nodeIndex] = NULL_NODE;
        rightChild[nodeIndex] = NULL_NODE;
        parent[nodeIndex] = parentNode;
        color[nodeIndex] = RED;

        return nodeIndex;
    }

    private void freeNode(final int nodeIndex) {
        if (nodeIndex == NULL_NODE) return;

        rightChild[nodeIndex] = freeListHead;
        freeListHead = nodeIndex;
    }

    private void grow() {
        final int oldCapacity = capacity;
        final int newCapacity = capacity * 2;

        keys = Arrays.copyOf(keys, newCapacity);
        leftChild = Arrays.copyOf(leftChild, newCapacity);
        rightChild = Arrays.copyOf(rightChild, newCapacity);
        parent = Arrays.copyOf(parent, newCapacity);
        color = Arrays.copyOf(color, newCapacity);

        for (int i = oldCapacity; i < newCapacity - 1; i++) {
            rightChild[i] = i + 1;
        }
        rightChild[newCapacity - 1] = freeListHead;
        freeListHead = oldCapacity;

        capacity = newCapacity;
    }

    // ------------------- Primitive Operations -------------------

    /**
     * Adds a primitive value to the set.
     *
     * @param value the value to add
     * @return {@code true} if the value was added (wasn't already present), {@code false} otherwise
     */
    public boolean add(final #primitive# value) {
        #null_check#
        if (root == NULL_NODE) {
            root = allocateNode(value, NULL_NODE);
            color[root] = BLACK;
            size = 1;
            modCount++;
            return true;
        }

        int parentNode = NULL_NODE;
        int current = root;
        int cmp = 0;

        while (current != NULL_NODE) {
            parentNode = current;
            cmp = compareKeys(value, keys[current]);
            if (cmp < 0) {
                current = leftChild[current];
            } else if (cmp > 0) {
                current = rightChild[current];
            } else {
                return false; // Already exists
            }
        }

        final int newNode = allocateNode(value, parentNode);
        if (cmp < 0) {
            leftChild[parentNode] = newNode;
        } else {
            rightChild[parentNode] = newNode;
        }

        fixAfterInsertion(newNode);
        size++;
        modCount++;
        return true;
    }

    /**
     * Checks if the set contains a primitive value.
     *
     * @param value the value to check
     * @return {@code true} if the value is present, {@code false} otherwise
     */
    public boolean contains(final #primitive# value) {
        #null_check#
        return getNode(value) != NULL_NODE;
    }

    /**
     * Removes a primitive value from the set.
     *
     * @param value the value to remove
     * @return {@code true} if the value was present and removed, {@code false} otherwise
     */
    public boolean remove(final #primitive# value) {
        #null_check#
        final int node = getNode(value);
        if (node == NULL_NODE) return false;

        deleteNode(node);
        return true;
    }

    /**
     * Returns the smallest value in the set.
     *
     * @return the minimum value, or throws NoSuchElementException if empty
     */
    public #primitive# first#upper#() {
        if (root == NULL_NODE) throw new NoSuchElementException();
        return keys[minimum(root)];
    }

    /**
     * Returns the largest value in the set.
     *
     * @return the maximum value, or throws NoSuchElementException if empty
     */
    public #primitive# last#upper#() {
        if (root == NULL_NODE) throw new NoSuchElementException();
        return keys[maximum(root)];
    }

    /**
     * Removes and returns the first (lowest) value in the set.
     *
     * @return the removed first value
     * @throws NoSuchElementException if the set is empty
     */
    public #primitive# pollFirst#upper#() {
        if (root == NULL_NODE) throw new NoSuchElementException();
        final int node = minimum(root);
        final #primitive# key = keys[node];
        deleteNode(node);
        return key;
    }

    /**
     * Removes and returns the last (highest) value in the set.
     *
     * @return the removed last value
     * @throws NoSuchElementException if the set is empty
     */
    public #primitive# pollLast#upper#() {
        if (root == NULL_NODE) throw new NoSuchElementException();
        final int node = maximum(root);
        final #primitive# key = keys[node];
        deleteNode(node);
        return key;
    }

    /**
     * Returns the greatest value less than or equal to the given value.
     *
     * @param value the value
     * @return the floor value
     * @throws NoSuchElementException if no such value exists
     */
    public #primitive# floor#upper#(final #primitive# value) {
        #null_check#
        final int node = floorNode(value);
        if (node == NULL_NODE) throw new NoSuchElementException();
        return keys[node];
    }

    /**
     * Returns the least value greater than or equal to the given value.
     *
     * @param value the value
     * @return the ceiling value
     * @throws NoSuchElementException if no such value exists
     */
    public #primitive# ceiling#upper#(final #primitive# value) {
        #null_check#
        final int node = ceilingNode(value);
        if (node == NULL_NODE) throw new NoSuchElementException();
        return keys[node];
    }

    /**
     * Returns the greatest value strictly less than the given value.
     *
     * @param value the value
     * @return the lower value
     * @throws NoSuchElementException if no such value exists
     */
    public #primitive# lower#upper#(final #primitive# value) {
        #null_check#
        final int node = lowerNode(value);
        if (node == NULL_NODE) throw new NoSuchElementException();
        return keys[node];
    }

    /**
     * Returns the least value strictly greater than the given value.
     *
     * @param value the value
     * @return the higher value
     * @throws NoSuchElementException if no such value exists
     */
    public #primitive# higher#upper#(final #primitive# value) {
        #null_check#
        final int node = higherNode(value);
        if (node == NULL_NODE) throw new NoSuchElementException();
        return keys[node];
    }

    /**
     * Applies a consumer to all values in ascending order.
     *
     * @param action the consumer to apply
     */
    public void forEach#upper#(final #upper#Consumer action) {
        Objects.requireNonNull(action);
        forEachInOrder(root, action);
    }

    private void forEachInOrder(final int node, final #upper#Consumer action) {
        if (node == NULL_NODE) return;
        forEachInOrder(leftChild[node], action);
        action.accept(keys[node]);
        forEachInOrder(rightChild[node], action);
    }

    /**
     * Returns a new array containing all elements of the set.
     *
     * @return an array copy of the set elements
     */
    public #primitive#[] to#upper#Array() {
        #primitive#[] result = new #primitive#[size];
        int i = 0;
        try (var it = new #upper#TreeSetIterator(-1)) {
            while (it.hasNext()) {
                 result[i++] = it.next#upper#();
            }
        }
        return result;
    }

    @FunctionalInterface
    public interface #upper#Consumer {
        void accept(#primitive# value);
    }

    // ------------------- Helper Methods -------------------

    private int getNode(final #primitive# key) {
        int current = root;
        while (current != NULL_NODE) {
            final int cmp = compareKeys(key, keys[current]);
            if (cmp < 0) {
                current = leftChild[current];
            } else if (cmp > 0) {
                current = rightChild[current];
            } else {
                return current;
            }
        }
        return NULL_NODE;
    }

    private int floorNode(final #primitive# key) {
        int current = root;
        int result = NULL_NODE;
        while (current != NULL_NODE) {
            final int cmp = compareKeys(key, keys[current]);
            if (cmp == 0) {
                return current;
            } else if (cmp > 0) {
                result = current;
                current = rightChild[current];
            } else {
                current = leftChild[current];
            }
        }
        return result;
    }

    private int ceilingNode(final #primitive# key) {
        int current = root;
        int result = NULL_NODE;
        while (current != NULL_NODE) {
            final int cmp = compareKeys(key, keys[current]);
            if (cmp == 0) {
                return current;
            } else if (cmp < 0) {
                result = current;
                current = leftChild[current];
            } else {
                current = rightChild[current];
            }
        }
        return result;
    }

    private int lowerNode(final #primitive# key) {
        int current = root;
        int result = NULL_NODE;
        while (current != NULL_NODE) {
            final int cmp = compareKeys(key, keys[current]);
            if (cmp > 0) {
                result = current;
                current = rightChild[current];
            } else {
                current = leftChild[current];
            }
        }
        return result;
    }

    private int higherNode(final #primitive# key) {
        int current = root;
        int result = NULL_NODE;
        while (current != NULL_NODE) {
            final int cmp = compareKeys(key, keys[current]);
            if (cmp < 0) {
                result = current;
                current = leftChild[current];
            } else {
                current = rightChild[current];
            }
        }
        return result;
    }

    private int minimum(final int node) {
        int current = node;
        while (leftChild[current] != NULL_NODE) {
            current = leftChild[current];
        }
        return current;
    }

    private int maximum(final int node) {
        int current = node;
        while (rightChild[current] != NULL_NODE) {
            current = rightChild[current];
        }
        return current;
    }

    private int successor(final int node) {
        if (rightChild[node] != NULL_NODE) {
            return minimum(rightChild[node]);
        }
        int current = node;
        int p = parent[node];
        while (p != NULL_NODE && current == rightChild[p]) {
            current = p;
            p = parent[p];
        }
        return p;
    }

    private int predecessor(final int node) {
        if (leftChild[node] != NULL_NODE) {
            return maximum(leftChild[node]);
        }
        int current = node;
        int p = parent[node];
        while (p != NULL_NODE && current == leftChild[p]) {
            current = p;
            p = parent[p];
        }
        return p;
    }

    private int compareKeys(final #primitive# k1, final #primitive# k2) {
        #key_compare#
    }

    // ------------------- Red-Black Tree Balancing -------------------

    private void fixAfterInsertion(int node) {
        while (node != NULL_NODE && node != root && color[parent[node]] == RED) {
            if (parent[node] == leftChild[parent[parent[node]]]) {
                final int uncle = rightChild[parent[parent[node]]];
                if (uncle != NULL_NODE && color[uncle] == RED) {
                    color[parent[node]] = BLACK;
                    color[uncle] = BLACK;
                    color[parent[parent[node]]] = RED;
                    node = parent[parent[node]];
                } else {
                    if (node == rightChild[parent[node]]) {
                        node = parent[node];
                        rotateLeft(node);
                    }
                    color[parent[node]] = BLACK;
                    color[parent[parent[node]]] = RED;
                    rotateRight(parent[parent[node]]);
                }
            } else {
                final int uncle = leftChild[parent[parent[node]]];
                if (uncle != NULL_NODE && color[uncle] == RED) {
                    color[parent[node]] = BLACK;
                    color[uncle] = BLACK;
                    color[parent[parent[node]]] = RED;
                    node = parent[parent[node]];
                } else {
                    if (node == leftChild[parent[node]]) {
                        node = parent[node];
                        rotateRight(node);
                    }
                    color[parent[node]] = BLACK;
                    color[parent[parent[node]]] = RED;
                    rotateLeft(parent[parent[node]]);
                }
            }
        }
        color[root] = BLACK;
    }

    private void deleteNode(final int node) {
        size--;
        modCount++;

        int toDelete = node;
        int replacement;

        if (leftChild[node] != NULL_NODE && rightChild[node] != NULL_NODE) {
            toDelete = successor(node);
            keys[node] = keys[toDelete]; // Copy successor's key to node
        }

        replacement = (leftChild[toDelete] != NULL_NODE) ? leftChild[toDelete] : rightChild[toDelete];

        if (replacement != NULL_NODE) {
            parent[replacement] = parent[toDelete];
            if (parent[toDelete] == NULL_NODE) {
                root = replacement;
            } else if (toDelete == leftChild[parent[toDelete]]) {
                leftChild[parent[toDelete]] = replacement;
            } else {
                rightChild[parent[toDelete]] = replacement;
            }

            if (color[toDelete] == BLACK) {
                fixAfterDeletion(replacement);
            }

            freeNode(toDelete);
        } else if (parent[toDelete] == NULL_NODE) {
            root = NULL_NODE;
            freeNode(toDelete);
        } else {
            if (color[toDelete] == BLACK) {
                fixAfterDeletion(toDelete);
            }

            if (parent[toDelete] != NULL_NODE) {
                if (toDelete == leftChild[parent[toDelete]]) {
                    leftChild[parent[toDelete]] = NULL_NODE;
                } else if (toDelete == rightChild[parent[toDelete]]) {
                    rightChild[parent[toDelete]] = NULL_NODE;
                }
            }

            freeNode(toDelete);
        }
    }

    private void fixAfterDeletion(int node) {
        while (node != root && colorOf(node) == BLACK) {
            if (node == leftOf(parentOf(node))) {
                int sibling = rightOf(parentOf(node));

                if (colorOf(sibling) == RED) {
                    setColor(sibling, BLACK);
                    setColor(parentOf(node), RED);
                    rotateLeft(parentOf(node));
                    sibling = rightOf(parentOf(node));
                }

                if (colorOf(leftOf(sibling)) == BLACK && colorOf(rightOf(sibling)) == BLACK) {
                    setColor(sibling, RED);
                    node = parentOf(node);
                } else {
                    if (colorOf(rightOf(sibling)) == BLACK) {
                        setColor(leftOf(sibling), BLACK);
                        setColor(sibling, RED);
                        rotateRight(sibling);
                        sibling = rightOf(parentOf(node));
                    }
                    setColor(sibling, colorOf(parentOf(node)));
                    setColor(parentOf(node), BLACK);
                    setColor(rightOf(sibling), BLACK);
                    rotateLeft(parentOf(node));
                    node = root;
                }
            } else {
                int sibling = leftOf(parentOf(node));

                if (colorOf(sibling) == RED) {
                    setColor(sibling, BLACK);
                    setColor(parentOf(node), RED);
                    rotateRight(parentOf(node));
                    sibling = leftOf(parentOf(node));
                }

                if (colorOf(rightOf(sibling)) == BLACK && colorOf(leftOf(sibling)) == BLACK) {
                    setColor(sibling, RED);
                    node = parentOf(node);
                } else {
                    if (colorOf(leftOf(sibling)) == BLACK) {
                        setColor(rightOf(sibling), BLACK);
                        setColor(sibling, RED);
                        rotateLeft(sibling);
                        sibling = leftOf(parentOf(node));
                    }
                    setColor(sibling, colorOf(parentOf(node)));
                    setColor(parentOf(node), BLACK);
                    setColor(leftOf(sibling), BLACK);
                    rotateRight(parentOf(node));
                    node = root;
                }
            }
        }
        setColor(node, BLACK);
    }

    private void rotateLeft(final int node) {
        if (node == NULL_NODE) return;
        final int right = rightChild[node];
        rightChild[node] = leftChild[right];
        if (leftChild[right] != NULL_NODE) {
            parent[leftChild[right]] = node;
        }
        parent[right] = parent[node];
        if (parent[node] == NULL_NODE) {
            root = right;
        } else if (leftChild[parent[node]] == node) {
            leftChild[parent[node]] = right;
        } else {
            rightChild[parent[node]] = right;
        }
        leftChild[right] = node;
        parent[node] = right;
    }

    private void rotateRight(final int node) {
        if (node == NULL_NODE) return;
        final int left = leftChild[node];
        leftChild[node] = rightChild[left];
        if (rightChild[left] != NULL_NODE) {
            parent[rightChild[left]] = node;
        }
        parent[left] = parent[node];
        if (parent[node] == NULL_NODE) {
            root = left;
        } else if (rightChild[parent[node]] == node) {
            rightChild[parent[node]] = left;
        } else {
            leftChild[parent[node]] = left;
        }
        rightChild[left] = node;
        parent[node] = left;
    }

    private boolean colorOf(final int node) {
        return node == NULL_NODE ? BLACK : color[node];
    }

    private int parentOf(final int node) {
        return node == NULL_NODE ? NULL_NODE : parent[node];
    }

    private int leftOf(final int node) {
        return node == NULL_NODE ? NULL_NODE : leftChild[node];
    }

    private int rightOf(final int node) {
        return node == NULL_NODE ? NULL_NODE : rightChild[node];
    }

    private void setColor(final int node, final boolean c) {
        if (node != NULL_NODE) color[node] = c;
    }

    // ------------------- Set Interface -------------------

    @Override
    public boolean add(final #boxed# value) {
        if (value == null) {
            throw new NullPointerException("Null values are not supported");
        }
        return add((#primitive#) value);
    }

    @Override
    public boolean contains(final Object o) {
        if (!(o instanceof #boxed#)) return false;
        return contains((#primitive#) o);
    }

    @Override
    public boolean remove(final Object o) {
        if (!(o instanceof #boxed#)) return false;
        return remove((#primitive#) o);
    }

    @Override
    public void clear() {
        if (size > 0) {
            root = NULL_NODE;
            size = 0;
            modCount++;
            initializeFreeList();
        }
    }

    @Override
    public int size() { return size; }

    @Override
    public boolean isEmpty() { return size == 0; }

    @Override
    public Iterator<#boxed#> iterator() {
        return new SetIterator(root != NULL_NODE ? minimum(root) : NULL_NODE);
    }

    // ------------------- Iterator Pool -------------------

    public #upper#TreeSetIterator borrowIterator() {
        if (iteratorPool == null) return null;

        long bits = iteratorAvailableBits;
        if (bits == 0) return null;

        final int poolIndex = Long.numberOfTrailingZeros(bits);
        iteratorAvailableBits = bits & ~(1L << poolIndex);

        @SuppressWarnings("unchecked")
        final #upper#TreeSetIterator iter = ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        iter.reset();
        return iter;
    }

    public void returnIterator(final #upper#TreeSetIterator iterator) {
        if (iteratorPool == null || iterator == null) return;
        final int poolIndex = iterator.poolIndex;
        if (poolIndex < 0 || poolIndex >= ITERATOR_POOL_SIZE) return;
        @SuppressWarnings("unchecked")
        final #upper#TreeSetIterator iter = ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        if (iter != iterator) return;

        iteratorAvailableBits |= (1L << poolIndex);
    }

    public int availableIteratorCount() {
        return iteratorPool == null ? 0 : Long.bitCount(iteratorAvailableBits);
    }

    private final class IteratorPoolEntry {
        final #upper#TreeSetIterator iterator;

        IteratorPoolEntry(final int poolIndex) {
            this.iterator = new #upper#TreeSetIterator(poolIndex);
        }
    }

    public final class #upper#TreeSetIterator implements AutoCloseable {
        private long p0, p1, p2, p3, p4, p5, p6; // Cache line padding

        private final int poolIndex;
        private int next;
        private int lastReturned;
        private int expectedModCount;

        private long p8, p9, p10, p11, p12; // Cache line padding

        private #upper#TreeSetIterator(final int poolIndex) {
            this.poolIndex = poolIndex;
            reset();
        }

        void reset() {
            this.next = root != NULL_NODE ? minimum(root) : NULL_NODE;
            this.lastReturned = NULL_NODE;
            this.expectedModCount = modCount;
        }

        public boolean hasNext() {
            return next != NULL_NODE;
        }

        public #primitive# next#upper#() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            if (next == NULL_NODE) {
                throw new NoSuchElementException();
            }

            lastReturned = next;
            final #primitive# key = keys[next];
            next = successor(next);
            return key;
        }

        public #primitive# peekNext#upper#() {
            if (next == NULL_NODE) {
                throw new NoSuchElementException();
            }
            return keys[next];
        }

        public void remove() {
            if (lastReturned == NULL_NODE) {
                throw new IllegalStateException("next() has not been called or remove() already called");
            }

            if (next == lastReturned) {
                next = successor(lastReturned);
            }

            deleteNode(lastReturned);
            expectedModCount = modCount;
            lastReturned = NULL_NODE;
        }

        public void forEachRemaining(final #upper#Consumer action) {
            Objects.requireNonNull(action);

            while (next != NULL_NODE) {
                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException();
                }
                action.accept(keys[next]);
                next = successor(next);
            }
        }

        @Override
        public void close() {
            #upper#TreeSet.this.returnIterator(this);
        }
    }

    // ------------------- Boxed Iterators -------------------

    private abstract class BaseIterator<T> implements Iterator<T> {
        int next;
        int lastReturned;
        int expectedModCount;

        BaseIterator(int startNode) {
            this.expectedModCount = modCount;
            this.next = startNode;
            this.lastReturned = NULL_NODE;
        }

        public boolean hasNext() {
            return next != NULL_NODE;
        }

        final int nextAscending() {
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            if (next == NULL_NODE) throw new NoSuchElementException();
            lastReturned = next;
            next = successor(next);
            return lastReturned;
        }

        final int nextDescending() {
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            if (next == NULL_NODE) throw new NoSuchElementException();
            lastReturned = next;
            next = predecessor(next);
            return lastReturned;
        }

        public final void remove() {
            if (lastReturned == NULL_NODE) throw new IllegalStateException();
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            deleteNode(lastReturned);
            expectedModCount = modCount;
            lastReturned = NULL_NODE;
        }
    }

    private final class SetIterator extends BaseIterator<#boxed#> {
        SetIterator(int startNode) { super(startNode); }
        @Override public #boxed# next() { return keys[nextAscending()]; }
    }

    private final class DescendingSetIterator extends BaseIterator<#boxed#> {
        DescendingSetIterator(int startNode) { super(startNode); }
        @Override public #boxed# next() { return keys[nextDescending()]; }
    }

    // ------------------- NavigableSet Implementation -------------------

    @Override
    public Comparator<? super #boxed#> comparator() { return null; }

    @Override
    public #boxed# first() {
        return first#upper#(); // Autobox
    }

    @Override
    public #boxed# last() {
        return last#upper#(); // Autobox
    }

    /**
     * Helper for boxed `lower` implementation.
     */
    private #boxed# lowerKey(final #primitive# value) {
        #null_check#
        final int node = lowerNode(value);
        return node != NULL_NODE ? keys[node] : null;
    }

    @Override
    public #boxed# lower(#boxed# value) {
        if (value == null) throw new NullPointerException();
        return lowerKey((#primitive#) value);
    }

    /**
     * Helper for boxed `floor` implementation.
     */
    private #boxed# floorKey(final #primitive# value) {
        #null_check#
        final int node = floorNode(value);
        return node != NULL_NODE ? keys[node] : null;
    }

    @Override
    public #boxed# floor(#boxed# value) {
        if (value == null) throw new NullPointerException();
        return floorKey((#primitive#) value);
    }

    /**
     * Helper for boxed `ceiling` implementation.
     */
    private #boxed# ceilingKey(final #primitive# value) {
        #null_check#
        final int node = ceilingNode(value);
        return node != NULL_NODE ? keys[node] : null;
    }

    @Override
    public #boxed# ceiling(#boxed# value) {
        if (value == null) throw new NullPointerException();
        return ceilingKey((#primitive#) value);
    }

    /**
     * Helper for boxed `higher` implementation.
     */
    private #boxed# higherKey(final #primitive# value) {
        #null_check#
        final int node = higherNode(value);
        return node != NULL_NODE ? keys[node] : null;
    }

    @Override
    public #boxed# higher(#boxed# value) {
        if (value == null) throw new NullPointerException();
        return higherKey((#primitive#) value);
    }

    @Override
    public #boxed# pollFirst() {
        if (root == NULL_NODE) return null;
        final int node = minimum(root);
        final #primitive# key = keys[node];
        deleteNode(node);
        return key; // Autobox
    }

    @Override
    public #boxed# pollLast() {
        if (root == NULL_NODE) return null;
        final int node = maximum(root);
        final #primitive# key = keys[node];
        deleteNode(node);
        return key; // Autobox
    }

    @Override
    public NavigableSet<#boxed#> descendingSet() {
        return new DescendingSetView(this);
    }

    @Override
    public Iterator<#boxed#> descendingIterator() {
        return new DescendingSetIterator(root != NULL_NODE ? maximum(root) : NULL_NODE);
    }

    @Override
    public NavigableSet<#boxed#> subSet(#boxed# fromElement, boolean fromInclusive, #boxed# toElement, boolean toInclusive) {
        return new SubSet(this, fromElement, fromInclusive, toElement, toInclusive);
    }

    @Override
    public NavigableSet<#boxed#> headSet(#boxed# toElement, boolean inclusive) {
        return new SubSet(this, null, true, toElement, inclusive);
    }

    @Override
    public NavigableSet<#boxed#> tailSet(#boxed# fromElement, boolean inclusive) {
        return new SubSet(this, fromElement, inclusive, null, true);
    }

    @Override
    public SortedSet<#boxed#> subSet(#boxed# fromElement, #boxed# toElement) {
        return new SubSet(this, fromElement, true, toElement, false);
    }

    @Override
    public SortedSet<#boxed#> headSet(#boxed# toElement) {
        return new SubSet(this, null, true, toElement, false);
    }

    @Override
    public SortedSet<#boxed#> tailSet(#boxed# fromElement) {
        return new SubSet(this, fromElement, true, null, true);
    }

    @Override
    public String toString() {
        if (size == 0) {
            return "[]";
        }

        final StringBuilder sb = new StringBuilder(size * 16); // Estimate ~16 chars per element
        sb.append('[');

        boolean first = true;
        int current = root != NULL_NODE ? minimum(root) : NULL_NODE;

        while (current != NULL_NODE) {
            if (!first) {
                sb.append(", ");
            }
            first = false;

            sb.append(keys[current]);
            current = successor(current);
        }

        sb.append(']');
        return sb.toString();
    }

    /**
     * Returns a detailed debug representation showing internal tree structure.
     * Includes node indices, colors, and parent/child relationships.
     */
    public String toDebugString() {
        if (size == 0) {
            return "Empty tree (size=0, root=" + root + ")";
        }

        final StringBuilder sb = new StringBuilder(size * 128);
        sb.append("Tree (size=").append(size)
          .append(", root=").append(root)
          .append(", capacity=").append(capacity)
          .append(", freeListHead=").append(freeListHead)
          .append("):\n");

        appendNodeDebug(sb, root, 0);

        return sb.toString();
    }

    private void appendNodeDebug(final StringBuilder sb, final int node, final int depth) {
        if (node == NULL_NODE) {
            return;
        }

        // Right subtree first (for visual layout)
        appendNodeDebug(sb, rightChild[node], depth + 1);

        // Current node
        for (int i = 0; i < depth; i++) {
            sb.append("    ");
        }
        sb.append("[").append(node).append("] ");
        sb.append(keys[node]);
        sb.append(" (").append(color[node] ? "R" : "B").append(")");
        sb.append(" p=").append(parent[node]);
        sb.append(" L=").append(leftChild[node]);
        sb.append(" R=").append(rightChild[node]);
        sb.append('\n');

        // Left subtree
        appendNodeDebug(sb, leftChild[node], depth + 1);
    }

    // ------------------- SubSet Implementation -------------------

    private final class SubSet extends AbstractSet<#boxed#> implements NavigableSet<#boxed#> {
        private final #upper#TreeSet set;
        private final #boxed# fromElement;
        private final boolean fromInclusive;
        private final #boxed# toElement;
        private final boolean toInclusive;
        private final boolean hasLowerBound;
        private final boolean hasUpperBound;

        SubSet(final #upper#TreeSet set,
               final #boxed# fromElement, final boolean fromInclusive,
               final #boxed# toElement, final boolean toInclusive) {
            this.set = set;
            this.fromElement = fromElement;
            this.fromInclusive = fromInclusive;
            this.toElement = toElement;
            this.toInclusive = toInclusive;
            this.hasLowerBound = fromElement != null;
            this.hasUpperBound = toElement != null;

            if (hasLowerBound && hasUpperBound && set.compareKeys((#primitive#)fromElement, (#primitive#)toElement) > 0) {
                throw new IllegalArgumentException("fromElement > toElement");
            }
        }

        private boolean inRange(final #primitive# key) {
            if (hasLowerBound) {
                final int cmpLow = set.compareKeys(key, fromElement);
                if (cmpLow < 0 || (!fromInclusive && cmpLow == 0)) return false;
            }
            if (hasUpperBound) {
                final int cmpHigh = set.compareKeys(key, toElement);
                return cmpHigh <= 0 && (toInclusive || cmpHigh != 0);
            }
            return true;
        }

        private boolean inLowerRange(final #primitive# key, final boolean inclusive) {
            if (!hasLowerBound) return true;
            final int cmp = set.compareKeys(key, (#primitive#) fromElement);
            return cmp > 0 || (inclusive && cmp == 0);
        }

        private boolean inUpperRange(final #primitive# key, final boolean inclusive) {
            if (!hasUpperBound) return true;
            final int cmp = set.compareKeys(key, (#primitive#) toElement);
            return cmp < 0 || (inclusive && cmp == 0);
        }

        private int lowestNode() {
            int node;
            if (hasLowerBound) {
                node = fromInclusive ? set.ceilingNode((#primitive#) fromElement) : set.higherNode((#primitive#) fromElement);
            } else {
                node = set.root != NULL_NODE ? set.minimum(set.root) : NULL_NODE;
            }
            return (node != NULL_NODE && inRange(set.keys[node])) ? node : NULL_NODE;
        }

        private int highestNode() {
            int node;
            if (hasUpperBound) {
                node = toInclusive ? set.floorNode((#primitive#) toElement) : set.lowerNode((#primitive#) toElement);
            } else {
                node = set.root != NULL_NODE ? set.maximum(set.root) : NULL_NODE;
            }
            return (node != NULL_NODE && inRange(set.keys[node])) ? node : NULL_NODE;
        }

        @Override
        public int size() {
            int count = 0;
            for (Iterator<#boxed#> it = iterator(); it.hasNext(); it.next()) {
                count++;
            }
            return count;
        }

        @Override
        public boolean isEmpty() {
            return lowestNode() == NULL_NODE;
        }

        @Override
        public boolean contains(final Object o) {
            if (!(o instanceof #boxed#)) return false;
            return inRange((#primitive#) o) && set.contains(o);
        }

        @Override
        public boolean add(final #boxed# value) {
            if (value == null) throw new NullPointerException();
            if (!inRange((#primitive#) value)) {
                throw new IllegalArgumentException("Value out of range");
            }
            return set.add(value);
        }

        @Override
        public boolean remove(final Object o) {
            if (!(o instanceof #boxed#)) return false;
            if (!inRange((#primitive#) o)) return false;
            return set.remove(o);
        }

        @Override
        public void clear() {
            for (Iterator<#boxed#> it = iterator(); it.hasNext(); ) {
                it.next();
                it.remove();
            }
        }

        @Override
        public #boxed# first() {
            final int node = lowestNode();
            if (node == NULL_NODE) throw new NoSuchElementException();
            return set.keys[node];
        }

        @Override
        public #boxed# last() {
            final int node = highestNode();
            if (node == NULL_NODE) throw new NoSuchElementException();
            return set.keys[node];
        }

        @Override
        public #boxed# pollFirst() {
            final int node = lowestNode();
            if (node == NULL_NODE) return null;
            final #primitive# key = set.keys[node];
            set.deleteNode(node);
            return key;
        }

        @Override
        public #boxed# pollLast() {
            final int node = highestNode();
            if (node == NULL_NODE) return null;
            final #primitive# key = set.keys[node];
            set.deleteNode(node);
            return key;
        }

        @Override
        public #boxed# lower(#boxed# value) {
            final int node = lowerNode(value);
            return node != NULL_NODE ? set.keys[node] : null;
        }

        private int lowerNode(#boxed# value) {
            if (value == null) throw new NullPointerException();
            #primitive# k = (#primitive#) value;
            int node;
            if (hasUpperBound && !inUpperRange(k, false)) {
                node = highestNode();
            } else {
                node = set.lowerNode(k);
            }
            return (node != NULL_NODE && inRange(set.keys[node])) ? node : NULL_NODE;
        }

        @Override
        public #boxed# floor(#boxed# value) {
            final int node = floorNode(value);
            return node != NULL_NODE ? set.keys[node] : null;
        }

        private int floorNode(#boxed# value) {
            if (value == null) throw new NullPointerException();
            #primitive# k = (#primitive#) value;
            int node;
            if (hasUpperBound && !inUpperRange(k, true)) {
                node = highestNode();
            } else {
                node = set.floorNode(k);
            }
            return (node != NULL_NODE && inRange(set.keys[node])) ? node : NULL_NODE;
        }

        @Override
        public #boxed# ceiling(#boxed# value) {
            final int node = ceilingNode(value);
            return node != NULL_NODE ? set.keys[node] : null;
        }

        private int ceilingNode(#boxed# value) {
            if (value == null) throw new NullPointerException();
            #primitive# k = (#primitive#) value;
            int node;
            if (hasLowerBound && !inLowerRange(k, true)) {
                node = lowestNode();
            } else {
                node = set.ceilingNode(k);
            }
            return (node != NULL_NODE && inRange(set.keys[node])) ? node : NULL_NODE;
        }

        @Override
        public #boxed# higher(#boxed# value) {
            final int node = higherNode(value);
            return node != NULL_NODE ? set.keys[node] : null;
        }

        private int higherNode(#boxed# value) {
            if (value == null) throw new NullPointerException();
            #primitive# k = (#primitive#) value;
            int node;
            if (hasLowerBound && !inLowerRange(k, false)) {
                node = lowestNode();
            } else {
                node = set.higherNode(k);
            }
            return (node != NULL_NODE && inRange(set.keys[node])) ? node : NULL_NODE;
        }

        @Override
        public Comparator<? super #boxed#> comparator() { return null; }

        @Override
        public NavigableSet<#boxed#> descendingSet() {
            return new SubSet(set, fromElement, fromInclusive, toElement, toInclusive).new DescendingSubSetView();
        }

        @Override
        public Iterator<#boxed#> descendingIterator() {
            return new DescendingSubSetIterator(highestNode());
        }

        @Override
        public NavigableSet<#boxed#> subSet(#boxed# from, boolean fromInc, #boxed# to, boolean toInc) {
            if (from == null || to == null) throw new NullPointerException();
            #primitive# fromP = (#primitive#)from;
            #primitive# toP = (#primitive#)to;
            if (!inRange(fromP) || !inRange(toP)) throw new IllegalArgumentException("Value out of range");
            return new SubSet(set, from, fromInc, to, toInc);
        }

        @Override
        public NavigableSet<#boxed#> headSet(#boxed# to, boolean toInc) {
            if (to == null) throw new NullPointerException();
            if (!inRange((#primitive#)to)) throw new IllegalArgumentException("Value out of range");
            return new SubSet(set, fromElement, fromInclusive, to, toInc);
        }

        @Override
        public NavigableSet<#boxed#> tailSet(#boxed# from, boolean toInc) {
            if (from == null) throw new NullPointerException();
            if (!inRange((#primitive#)from)) throw new IllegalArgumentException("Value out of range");
            return new SubSet(set, from, toInc, toElement, toInclusive);
        }

        @Override public SortedSet<#boxed#> subSet(#boxed# from, #boxed# to) { return subSet(from, true, to, false); }
        @Override public SortedSet<#boxed#> headSet(#boxed# to) { return headSet(to, false); }
        @Override public SortedSet<#boxed#> tailSet(#boxed# from) { return tailSet(from, true); }

        @Override
        public Iterator<#boxed#> iterator() {
            return new SubSetIterator(lowestNode());
        }

        private final class SubSetIterator extends BaseIterator<#boxed#> {
            SubSetIterator(int startNode) {
                super(startNode);
            }

            @Override
            public boolean hasNext() {
                return next != NULL_NODE && inRange(set.keys[next]);
            }

            @Override
            public #boxed# next() {
                if (next == NULL_NODE || !inRange(set.keys[next])) {
                    throw new NoSuchElementException();
                }
                final int node = nextAscending();
                return set.keys[node];
            }
        }

        private final class DescendingSubSetView extends AbstractSet<#boxed#> implements NavigableSet<#boxed#> {
            @Override public int size() { return SubSet.this.size(); }
            @Override public boolean isEmpty() { return SubSet.this.isEmpty(); }
            @Override public boolean contains(Object o) { return SubSet.this.contains(o); }
            @Override public boolean add(#boxed# value) { return SubSet.this.add(value); }
            @Override public boolean remove(Object o) { return SubSet.this.remove(o); }
            @Override public void clear() { SubSet.this.clear(); }
            @Override public Comparator<? super #boxed#> comparator() { return null; }

            @Override public #boxed# first() { return SubSet.this.last(); }
            @Override public #boxed# last() { return SubSet.this.first(); }
            @Override public #boxed# pollFirst() { return SubSet.this.pollLast(); }
            @Override public #boxed# pollLast() { return SubSet.this.pollFirst(); }

            @Override public #boxed# lower(#boxed# value) { return SubSet.this.higher(value); }
            @Override public #boxed# floor(#boxed# value) { return SubSet.this.ceiling(value); }
            @Override public #boxed# ceiling(#boxed# value) { return SubSet.this.floor(value); }
            @Override public #boxed# higher(#boxed# value) { return SubSet.this.lower(value); }

            @Override public NavigableSet<#boxed#> descendingSet() { return SubSet.this; }
            @Override public Iterator<#boxed#> descendingIterator() { return SubSet.this.iterator(); }
            @Override public Iterator<#boxed#> iterator() { return new DescendingSubSetIterator(highestNode()); }

            @Override public NavigableSet<#boxed#> subSet(#boxed# from, boolean fromInc, #boxed# to, boolean toInc) { return SubSet.this.subSet(to, toInc, from, fromInc).descendingSet(); }
            @Override public NavigableSet<#boxed#> headSet(#boxed# to, boolean toInc) { return SubSet.this.tailSet(to, toInc).descendingSet(); }
            @Override public NavigableSet<#boxed#> tailSet(#boxed# from, boolean fromInc) { return SubSet.this.headSet(from, fromInc).descendingSet(); }
            @Override public SortedSet<#boxed#> subSet(#boxed# from, #boxed# to) { return subSet(from, true, to, false); }
            @Override public SortedSet<#boxed#> headSet(#boxed# to) { return headSet(to, false); }
            @Override public SortedSet<#boxed#> tailSet(#boxed# from) { return tailSet(from, true); }
        }

        private final class DescendingSubSetIterator extends BaseIterator<#boxed#> {
            DescendingSubSetIterator(int startNode) {
                super(startNode);
            }

            @Override
            public boolean hasNext() {
                return next != NULL_NODE && inRange(set.keys[next]);
            }

            @Override
            public #boxed# next() {
                if (next == NULL_NODE || !inRange(set.keys[next])) {
                    throw new NoSuchElementException();
                }
                final int node = nextDescending();
                return set.keys[node];
            }
        }
    }

    // ------------------- DescendingSetView Implementation -------------------

    private final class DescendingSetView extends AbstractSet<#boxed#> implements NavigableSet<#boxed#> {
        private final #upper#TreeSet set;

        DescendingSetView(final #upper#TreeSet set) { this.set = set; }

        @Override public int size() { return set.size(); }
        @Override public boolean isEmpty() { return set.isEmpty(); }
        @Override public boolean contains(Object o) { return set.contains(o); }
        @Override public boolean add(#boxed# value) { return set.add(value); }
        @Override public boolean remove(Object o) { return set.remove(o); }
        @Override public void clear() { set.clear(); }
        @Override public Comparator<? super #boxed#> comparator() { return null; }

        @Override public #boxed# first() { return set.last(); }
        @Override public #boxed# last() { return set.first(); }
        @Override public #boxed# pollFirst() { return set.pollLast(); }
        @Override public #boxed# pollLast() { return set.pollFirst(); }

        @Override public #boxed# lower(#boxed# value) { return set.higher(value); }
        @Override public #boxed# floor(#boxed# value) { return set.ceiling(value); }
        @Override public #boxed# ceiling(#boxed# value) { return set.floor(value); }
        @Override public #boxed# higher(#boxed# value) { return set.lower(value); }

        @Override public NavigableSet<#boxed#> descendingSet() { return set; }
        @Override public Iterator<#boxed#> descendingIterator() { return set.iterator(); }
        @Override public Iterator<#boxed#> iterator() {
            return new DescendingSetIterator(set.root == NULL_NODE ? NULL_NODE : set.maximum(set.root));
        }

        @Override public NavigableSet<#boxed#> subSet(#boxed# from, boolean fromInc, #boxed# to, boolean toInc) { return set.subSet(to, toInc, from, fromInc).descendingSet(); }
        @Override public NavigableSet<#boxed#> headSet(#boxed# to, boolean toInc) { return set.tailSet(to, toInc).descendingSet(); }
        @Override public NavigableSet<#boxed#> tailSet(#boxed# from, boolean fromInc) { return set.headSet(from, fromInc).descendingSet(); }
        @Override public SortedSet<#boxed#> subSet(#boxed# from, #boxed# to) { return subSet(from, true, to, false); }
        @Override public SortedSet<#boxed#> headSet(#boxed# to) { return headSet(to, false); }
        @Override public SortedSet<#boxed#> tailSet(#boxed# from) { return tailSet(from, true); }
    }

    // ------------------- Builder -------------------

    public static final class Builder {
        private int initialCapacity = DEFAULT_INITIAL_CAPACITY;
        private boolean enableIteratorPool = true;

        public Builder initialCapacity(final int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        public Builder disableIteratorPool() {
            this.enableIteratorPool = false;
            return this;
        }

        public #upper#TreeSet build() {
            return new #upper#TreeSet(initialCapacity, enableIteratorPool);
        }
    }
}
