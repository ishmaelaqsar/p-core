package dev.aqsar.pcore.collections;

import java.util.*;

/**
 * An allocation-free Red-Black Tree implementation that stores Object-to-#primitiveValue# mappings without boxing values.
 * Uses array-based node storage with a free list to eliminate allocations after initial capacity.
 * Provides O(log n) operations for put, get, remove and maintains sorted order by keys.
 * <p>
 * Null keys are not supported.
 * <p>
 * This class is not thread-safe.
 *
 * @param <K> the type of keys stored in this map
 */
public final class Object2#upperValue#TreeMap<K> implements NavigableMap<K, #boxedValue#> {

    public static final #primitiveValue# DEFAULT_NULL_VALUE = #nullValue#;
    public static final int DEFAULT_INITIAL_CAPACITY = 16;

    private static final boolean RED = true;
    private static final boolean BLACK = false;
    private static final int NULL_NODE = -1;
    private static final int ITERATOR_POOL_SIZE = 8;

    // Node storage arrays (structure of arrays for cache efficiency)
    private K[] keys;
    private #primitiveValue#[] values;
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

    public final #primitiveValue# nullValue;

    // Iterator pool
    private final Object[] iteratorPool;
    private long iteratorAvailableBits;

    @SuppressWarnings("unchecked")
    private Object2#upperValue#TreeMap(
            final int initialCapacity,
            final #primitiveValue# nullValue,
            final boolean enableIteratorPool
    ) {
        this.capacity = Math.max(initialCapacity, DEFAULT_INITIAL_CAPACITY);
        this.nullValue = nullValue;

        this.keys = (K[]) new Object[capacity];
        this.values = new #primitiveValue#[capacity];
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

    public static <K> Builder<K> builder() { return new Builder<>();
    }

    private void initializeFreeList() {
        for (int i = 0; i < capacity - 1; i++) {
            rightChild[i] = i + 1;
        }
        rightChild[capacity - 1] = NULL_NODE;
        freeListHead = 0;
    }

    // ------------------- Node Management -------------------

    private int allocateNode(final K key, final #primitiveValue# value, final int parentNode) {
        int nodeIndex;
        if (freeListHead != NULL_NODE) {
            nodeIndex = freeListHead;
            freeListHead = rightChild[freeListHead];
        } else {
            grow();
            return allocateNode(key, value, parentNode);
        }

        keys[nodeIndex] = key;
        values[nodeIndex] = value;
        leftChild[nodeIndex] = NULL_NODE;
        rightChild[nodeIndex] = NULL_NODE;
        parent[nodeIndex] = parentNode;
        color[nodeIndex] = RED;

        return nodeIndex;
    }

    private void freeNode(final int nodeIndex) {
        if (nodeIndex == NULL_NODE) return;
        keys[nodeIndex] = null;
        rightChild[nodeIndex] = freeListHead;
        freeListHead = nodeIndex;
    }

    @SuppressWarnings("unchecked")
    private void grow() {
        final int oldCapacity = capacity;
        final int newCapacity = capacity * 2;

        keys = Arrays.copyOf(keys, newCapacity);
        values = Arrays.copyOf(values, newCapacity);
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
     * Associates an object key with a primitive value.
     *
     * @param key the object key
     * @param value the primitive value
     */
    public void put(final K key, final #primitiveValue# value) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        if (root == NULL_NODE) {
            root = allocateNode(key, value, NULL_NODE);
            color[root] = BLACK;
            size = 1;
            modCount++;
            return;
        }

        int parentNode = NULL_NODE;
        int current = root;
        int cmp = 0;

        while (current != NULL_NODE) {
            parentNode = current;
            cmp = compareKeys(key, keys[current]);
            if (cmp < 0) {
                current = leftChild[current];
            } else if (cmp > 0) {
                current = rightChild[current];
            } else {
                values[current] = value;
                modCount++;
                return;
            }
        }

        final int newNode = allocateNode(key, value, parentNode);
        if (cmp < 0) {
            leftChild[parentNode] = newNode;
        } else {
            rightChild[parentNode] = newNode;
        }

        fixAfterInsertion(newNode);
        size++;
        modCount++;
    }

    /**
     * Retrieves the primitive value for an object key.
     *
     * @param key the key to lookup
     * @return the associated primitive value, or the configured nullValue if the key is not present
     */
    public #primitiveValue# get#upperValue#(final K key) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        final int node = getNode(key);
        return node != NULL_NODE ? values[node] : nullValue;
    }

    /**
     * Retrieves the value for a key, or returns the default if not found.
     *
     * @param key the key to lookup
     * @param defaultValue the default value if key is absent
     * @return the value associated with the key, or defaultValue
     */
    public #primitiveValue# getOrDefault(final K key, final #primitiveValue# defaultValue) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        final int node = getNode(key);
        return node != NULL_NODE ? values[node] : defaultValue;
    }

    /**
     * Checks if a primitive value exists in the map.
     *
     * @param value the value to check
     * @return true if the value is present, false otherwise
     */
    public boolean containsValue(final #primitiveValue# value) {
        return containsValueInSubtree(root, value);
    }

    private boolean containsValueInSubtree(final int node, final #primitiveValue# value) {
        if (node == NULL_NODE) return false;
        if (isValueEqual(values[node], value)) return true;
        return containsValueInSubtree(leftChild[node], value) || containsValueInSubtree(rightChild[node], value);
    }

    /**
     * Removes a key-value mapping by object key.
     *
     * @param key the key to remove
     * @return the previous primitive value associated with the key, or nullValue if not present
     */
    public #primitiveValue# remove#upperValue#(final K key) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        final int node = getNode(key);
        if (node == NULL_NODE) return nullValue;

        final #primitiveValue# oldValue = values[node];
        deleteNode(node);
        return oldValue;
    }

    /**
     * Returns the smallest key in the map.
     *
     * @return the minimum key, or throws NoSuchElementException if empty
     */
    @Override
    public K firstKey() {
        if (root == NULL_NODE) throw new NoSuchElementException();
        return keys[minimum(root)];
    }

    /**
     * Returns the largest key in the map.
     *
     * @return the maximum key, or throws NoSuchElementException if empty
     */
    @Override
    public K lastKey() {
        if (root == NULL_NODE) throw new NoSuchElementException();
        return keys[maximum(root)];
    }

    /**
     * Returns the greatest key less than or equal to the given key.
     *
     * @param key the key
     * @return the floor key, or null if no such key exists
     */
    @Override
    public K floorKey(final K key) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        final int node = floorNode(key);
        return node != NULL_NODE ? keys[node] : null;
    }

    /**
     * Returns the least key greater than or equal to the given key.
     *
     * @param key the key
     * @return the ceiling key, or null if no such key exists
     */
    @Override
    public K ceilingKey(final K key) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        final int node = ceilingNode(key);
        return node != NULL_NODE ? keys[node] : null;
    }

    /**
     * Returns the greatest key strictly less than the given key.
     *
     * @param key the key
     * @return the lower key, or null if no such key exists
     */
    @Override
    public K lowerKey(final K key) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        final int node = lowerNode(key);
        return node != NULL_NODE ? keys[node] : null;
    }

    /**
     * Returns the least key strictly greater than the given key.
     *
     * @param key the key
     * @return the higher key, or null if no such key exists
     */
    @Override
    public K higherKey(final K key) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        final int node = higherNode(key);
        return node != NULL_NODE ? keys[node] : null;
    }

    /**
     * Applies a consumer to all key-value pairs in ascending key order.
     *
     * @param action the consumer to apply
     */
    public void forEachObject#upperValue#(final Object#upperValue#Consumer<K> action) {
        Objects.requireNonNull(action);
        forEachInOrder(root, action);
    }

    private void forEachInOrder(final int node, final Object#upperValue#Consumer<K> action) {
        if (node == NULL_NODE) return;
        forEachInOrder(leftChild[node], action);
        action.accept(keys[node], values[node]);
        forEachInOrder(rightChild[node], action);
    }

    @FunctionalInterface
    public interface Object#upperValue#Consumer<K> {
        void accept(K key, #primitiveValue# value);
    }

    // ------------------- Helper Methods -------------------

    private int getNode(final K key) {
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

    private int floorNode(final K key) {
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

    private int ceilingNode(final K key) {
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

    private int lowerNode(final K key) {
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

    private int higherNode(final K key) {
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

    @SuppressWarnings("unchecked")
    private int compareKeys(final K k1, final K k2) {
        return ((Comparable<K>) k1).compareTo(k2);
    }

    private boolean isValueEqual(final #primitiveValue# v1, final #primitiveValue# v2) {
        #value_equals#
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
            keys[node] = keys[toDelete];
            values[node] = values[toDelete];
        }

        replacement = (leftChild[toDelete] != NULL_NODE) ?
                leftChild[toDelete] : rightChild[toDelete];

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
        return node == NULL_NODE ?
                BLACK : color[node];
    }

    private int parentOf(final int node) {
        return node == NULL_NODE ?
                NULL_NODE : parent[node];
    }

    private int leftOf(final int node) {
        return node == NULL_NODE ?
                NULL_NODE : leftChild[node];
    }

    private int rightOf(final int node) {
        return node == NULL_NODE ?
                NULL_NODE : rightChild[node];
    }

    private void setColor(final int node, final boolean c) {
        if (node != NULL_NODE) color[node] = c;
    }

    // ------------------- Map Interface -------------------

    @Override
    public #boxedValue# put(final K key, final #boxedValue# value) {
        if (key == null || value == null) {
            throw new NullPointerException("Null keys and values are not supported");
        }
        final #primitiveValue# oldValue = get#upperValue#(key);
        put(key, (#primitiveValue#) value);
        return isValueEqual(oldValue, nullValue) ?
                null : oldValue;
    }

    @Override
    public #boxedValue# get(final Object key) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        @SuppressWarnings("unchecked")
        final #primitiveValue# value = get#upperValue#((K) key);
        return isValueEqual(value, nullValue) ?
                null : value;
    }

    @Override
    public #boxedValue# getOrDefault(final Object key, final #boxedValue# defaultValue) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        @SuppressWarnings("unchecked")
        final #primitiveValue# value = get#upperValue#((K) key);
        return isValueEqual(value, nullValue) ? defaultValue : value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean containsKey(final Object key) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        return getNode((K) key) != NULL_NODE;
    }

    @Override
    public boolean containsValue(final Object value) {
        if (!(value instanceof #boxedValue#)) return false;
        return containsValue((#primitiveValue#) value);
    }

    @Override
    public #boxedValue# remove(final Object key) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        @SuppressWarnings("unchecked")
        final #primitiveValue# value = remove#upperValue#((K) key);
        return isValueEqual(value, nullValue) ?
                null : value;
    }

    @Override
    public #boxedValue# putIfAbsent(final K key, final #boxedValue# value) {
        if (key == null || value == null) {
            throw new NullPointerException("Null keys and values are not supported");
        }
        final #primitiveValue# v = get#upperValue#(key);
        if (!isValueEqual(v, nullValue)) {
            return v;
        }
        put(key, (#primitiveValue#) value);
        return null;
    }

    @Override
    public boolean replace(final K key, final #boxedValue# oldValue, final #boxedValue# newValue) {
        if (key == null || oldValue == null || newValue == null) {
            throw new NullPointerException("Null keys and values are not supported");
        }
        final int node = getNode(key);
        if (node == NULL_NODE) return false;

        if (!isValueEqual(values[node], (#primitiveValue#)oldValue)) {
            return false;
        }
        values[node] = (#primitiveValue#)newValue;
        modCount++;
        return true;
    }

    @Override
    public #boxedValue# replace(final K key, final #boxedValue# value) {
        if (key == null || value == null) {
            throw new NullPointerException("Null keys and values are not supported");
        }
        final int node = getNode(key);
        if (node == NULL_NODE) return null;

        final #primitiveValue# oldValue = values[node];
        values[node] = (#primitiveValue#)value;
        modCount++;
        return oldValue;
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
    public void putAll(final Map<? extends K, ? extends #boxedValue#> m) {
        for (Map.Entry<? extends K, ? extends #boxedValue#> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public int size() { return size;
    }

    @Override
    public boolean isEmpty() { return size == 0;
    }

    @Override
    public Set<K> keySet() {
        return new KeySet(this, false);
    }

    @Override
    public Collection<#boxedValue#> values() {
        return new AbstractCollection<#boxedValue#>() {
            @Override public int size() { return Object2#upperValue#TreeMap.this.size();
            }
            @Override public boolean contains(final Object o) { return containsValue(o);
            }
            @Override public Iterator<#boxedValue#> iterator() { return new ValueIterator();
            }
            @Override public void clear() { Object2#upperValue#TreeMap.this.clear();
            }
        };
    }

    @Override
    public Set<Entry<K, #boxedValue#>> entrySet() {
        return new AbstractSet<Entry<K, #boxedValue#>>() {
            @Override public int size() { return Object2#upperValue#TreeMap.this.size();
            }
            @Override public Iterator<Entry<K, #boxedValue#>> iterator() { return new EntryIterator();
            }
            @Override public void clear() { Object2#upperValue#TreeMap.this.clear();
            }
        };
    }

    // ------------------- Iterator Pool -------------------

    public Object#upperValue#TreeMapIterator borrowIterator() {
        if (iteratorPool == null) return null;
        long bits = iteratorAvailableBits;
        if (bits == 0) return null;

        final int poolIndex = Long.numberOfTrailingZeros(bits);
        iteratorAvailableBits = bits & ~(1L << poolIndex);

        @SuppressWarnings("unchecked")
        final Object#upperValue#TreeMapIterator iter = ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        iter.reset();
        return iter;
    }

    public void returnIterator(final Object#upperValue#TreeMapIterator iterator) {
        if (iteratorPool == null || iterator == null) return;
        final int poolIndex = iterator.poolIndex;
        if (poolIndex < 0 || poolIndex >= ITERATOR_POOL_SIZE) return;
        @SuppressWarnings("unchecked")
        final Object#upperValue#TreeMapIterator iter = ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        if (iter != iterator) return;
        iteratorAvailableBits |= (1L << poolIndex);
    }

    public int availableIteratorCount() {
        return iteratorPool == null ?
                0 : Long.bitCount(iteratorAvailableBits);
    }

    private final class IteratorPoolEntry {
        final Object#upperValue#TreeMapIterator iterator;
        IteratorPoolEntry(final int poolIndex) {
            this.iterator = new Object#upperValue#TreeMapIterator(poolIndex);
        }
    }

    public final class Object#upperValue#TreeMapIterator implements AutoCloseable {
        private long p0, p1, p2, p3, p4, p5, p6;
        private final int poolIndex;
        private int next;
        private int lastReturned;
        private int expectedModCount;

        private long p8, p9, p10, p11, p12;
        private Object#upperValue#TreeMapIterator(final int poolIndex) {
            this.poolIndex = poolIndex;
            reset();
        }

        void reset() {
            this.next = root != NULL_NODE ?
                    minimum(root) : NULL_NODE;
            this.lastReturned = NULL_NODE;
            this.expectedModCount = modCount;
        }

        public boolean hasNext() {
            return next != NULL_NODE;
        }

        public K nextKey() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            if (next == NULL_NODE) {
                throw new NoSuchElementException();
            }

            lastReturned = next;
            final K key = keys[next];
            next = successor(next);
            return key;
        }

        public #primitiveValue# nextValue() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            if (next == NULL_NODE) {
                throw new NoSuchElementException();
            }

            lastReturned = next;
            final #primitiveValue# value = values[next];
            next = successor(next);
            return value;
        }

        public K peekNextKey() {
            if (next == NULL_NODE) {
                throw new NoSuchElementException();
            }
            return keys[next];
        }

        public #primitiveValue# peekNextValue() {
            if (next == NULL_NODE) {
                throw new NoSuchElementException();
            }
            return values[next];
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

        public void forEachRemaining(final Object#upperValue#Consumer<K> action) {
            Objects.requireNonNull(action);
            while (next != NULL_NODE) {
                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException();
                }
                action.accept(keys[next], values[next]);
                next = successor(next);
            }
        }

        @Override
        public void close() {
            Object2#upperValue#TreeMap.this.returnIterator(this);
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

    private final class KeyIterator extends BaseIterator<K> {
        KeyIterator() { super(root != NULL_NODE ? minimum(root) : NULL_NODE);
        }
        KeyIterator(int startNode) { super(startNode);
        }
        @Override public K next() { return keys[nextAscending()];
        }
    }

    private final class ValueIterator extends BaseIterator<#boxedValue#> {
        ValueIterator() { super(root != NULL_NODE ? minimum(root) : NULL_NODE);
        }
        ValueIterator(int startNode) { super(startNode);
        }
        @Override public #boxedValue# next() { return values[nextAscending()];
        }
    }

    private final class EntryIterator extends BaseIterator<Entry<K, #boxedValue#>> {
        EntryIterator() { super(root != NULL_NODE ? minimum(root) : NULL_NODE);
        }
        EntryIterator(int startNode) { super(startNode);
        }
        @Override public Entry<K, #boxedValue#> next() {
            final int node = nextAscending();
            return new SimpleEntry<>(keys[node], values[node]);
        }
    }

    private final class DescendingKeyIterator extends BaseIterator<K> {
        DescendingKeyIterator(int startNode) { super(startNode);
        }
        @Override public K next() { return keys[nextDescending()];
        }
    }

    private final class DescendingEntryIterator extends BaseIterator<Entry<K, #boxedValue#>> {
        DescendingEntryIterator(int startNode) { super(startNode);
        }
        @Override public Entry<K, #boxedValue#> next() {
            final int node = nextDescending();
            return new SimpleEntry<>(keys[node], values[node]);
        }
    }

    private static class SimpleEntry<K, V> implements Entry<K, V> {
        private final K key;
        private V value;

        SimpleEntry(final K key, final V value) {
            this.key = key;
            this.value = value;
        }

        @Override public K getKey() { return key;
        }
        @Override public V getValue() { return value;
        }
        @Override public V setValue(final V value) {
            final V old = this.value;
            this.value = value;
            return old;
        }
    }

    // ------------------- NavigableMap Implementation -------------------

    @Override public Comparator<?
            super K> comparator() { return null; }

    @Override
    public Entry<K, #boxedValue#> lowerEntry(final K key) {
        if (key == null) return null;
        final int node = lowerNode((K) key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    @Override
    public Entry<K, #boxedValue#> floorEntry(final K key) {
        if (key == null) return null;
        final int node = floorNode((K) key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    @Override
    public Entry<K, #boxedValue#> ceilingEntry(final K key) {
        if (key == null) return null;
        final int node = ceilingNode((K) key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    @Override
    public Entry<K, #boxedValue#> higherEntry(final K key) {
        if (key == null) return null;
        final int node = higherNode((K) key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    @Override
    public Entry<K, #boxedValue#> firstEntry() {
        if (root == NULL_NODE) return null;
        final int node = minimum(root);
        return new SimpleEntry<>(keys[node], values[node]);
    }

    @Override
    public Entry<K, #boxedValue#> lastEntry() {
        if (root == NULL_NODE) return null;
        final int node = maximum(root);
        return new SimpleEntry<>(keys[node], values[node]);
    }

    @Override
    public Entry<K, #boxedValue#> pollFirstEntry() {
        if (root == NULL_NODE) return null;
        final int node = minimum(root);
        final Entry<K, #boxedValue#> entry = new SimpleEntry<>(keys[node], values[node]);
        deleteNode(node);
        return entry;
    }

    @Override
    public Entry<K, #boxedValue#> pollLastEntry() {
        if (root == NULL_NODE) return null;
        final int node = maximum(root);
        final Entry<K, #boxedValue#> entry = new SimpleEntry<>(keys[node], values[node]);
        deleteNode(node);
        return entry;
    }

    @Override
    public NavigableMap<K, #boxedValue#> descendingMap() {
        return new DescendingMap(this);
    }

    @Override
    public NavigableSet<K> navigableKeySet() {
        return new KeySet(this, false);
    }

    @Override
    public NavigableSet<K> descendingKeySet() {
        return new KeySet(this, true);
    }

    @Override
    public NavigableMap<K, #boxedValue#> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        return new SubMap(this, fromKey, fromInclusive, toKey, toInclusive);
    }

    @Override
    public NavigableMap<K, #boxedValue#> headMap(K toKey, boolean inclusive) {
        return new SubMap(this, null, true, toKey, inclusive);
    }

    @Override
    public NavigableMap<K, #boxedValue#> tailMap(K fromKey, boolean inclusive) {
        return new SubMap(this, fromKey, inclusive, null, true);
    }

    @Override
    public SortedMap<K, #boxedValue#> subMap(K fromKey, K toKey) {
        return new SubMap(this, fromKey, true, toKey, false);
    }

    @Override
    public SortedMap<K, #boxedValue#> headMap(K toKey) {
        return new SubMap(this, null, true, toKey, false);
    }

    @Override
    public SortedMap<K, #boxedValue#> tailMap(K fromKey) {
        return new SubMap(this, fromKey, true, null, true);
    }

    @Override
    public String toString() {
        if (size == 0) {
            return "{}";
        }

        final StringBuilder sb = new StringBuilder(size * 32);
        // Estimate ~32 chars per entry
        sb.append('{');

        boolean first = true;
        int current = root != NULL_NODE ? minimum(root) : NULL_NODE;
        while (current != NULL_NODE) {
            if (!first) {
                sb.append(", ");
            }
            first = false;

            sb.append(keys[current]);
            sb.append('=');
            sb.append(values[current]);
            current = successor(current);
        }

        sb.append('}');
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
        sb.append(keys[node]).append("=").append(values[node]);
        sb.append(" (").append(color[node] ? "R" : "B").append(")");
        sb.append(" p=").append(parent[node]);
        sb.append(" L=").append(leftChild[node]);
        sb.append(" R=").append(rightChild[node]);
        sb.append('\n');

        // Left subtree
        appendNodeDebug(sb, leftChild[node], depth + 1);
    }

    // ------------------- SubMap Implementation -------------------

    private final class SubMap extends AbstractMap<K, #boxedValue#> implements NavigableMap<K, #boxedValue#> {
        private final Object2#upperValue#TreeMap<K> map;
        private final K fromKey;
        private final boolean fromInclusive;
        private final K toKey;
        private final boolean toInclusive;
        private final boolean hasLowerBound;
        private final boolean hasUpperBound;

        SubMap(final Object2#upperValue#TreeMap<K> map,
               final K fromKey, final boolean fromInclusive,
               final K toKey, final boolean toInclusive) {
            this.map = map;
            this.fromKey = fromKey;
            this.fromInclusive = fromInclusive;
            this.toKey = toKey;
            this.toInclusive = toInclusive;
            this.hasLowerBound = fromKey != null;
            this.hasUpperBound = toKey != null;

            if (hasLowerBound && hasUpperBound && map.compareKeys(fromKey, toKey) > 0) {
                throw new IllegalArgumentException("fromKey > toKey");
            }
        }

        private boolean inRange(final K key) {
            if (hasLowerBound) {
                final int cmpLow = map.compareKeys(key, fromKey);
                if (cmpLow < 0 || (!fromInclusive && cmpLow == 0)) return false;
            }
            if (hasUpperBound) {
                final int cmpHigh = map.compareKeys(key, toKey);
                return cmpHigh <= 0 && (toInclusive || cmpHigh != 0);
            }
            return true;
        }

        private boolean inLowerRange(final K key, final boolean inclusive) {
            if (!hasLowerBound) return true;
            final int cmp = map.compareKeys(key, fromKey);
            return cmp > 0 || (inclusive && cmp == 0);
        }

        private boolean inUpperRange(final K key, final boolean inclusive) {
            if (!hasUpperBound) return true;
            final int cmp = map.compareKeys(key, toKey);
            return cmp < 0 || (inclusive && cmp == 0);
        }

        private int lowestNode() {
            int node;
            if (hasLowerBound) {
                node = fromInclusive ?
                        map.ceilingNode(fromKey) : map.higherNode(fromKey);
            } else {
                node = map.root != NULL_NODE ?
                        map.minimum(map.root) : NULL_NODE;
            }
            return (node != NULL_NODE && inRange(map.keys[node])) ?
                    node : NULL_NODE;
        }

        private int highestNode() {
            int node;
            if (hasUpperBound) {
                node = toInclusive ?
                        map.floorNode(toKey) : map.lowerNode(toKey);
            } else {
                node = map.root != NULL_NODE ?
                        map.maximum(map.root) : NULL_NODE;
            }
            return (node != NULL_NODE && inRange(map.keys[node])) ?
                    node : NULL_NODE;
        }

        @Override
        public int size() {
            int count = 0;
            for (Iterator<Entry<K, #boxedValue#>> it = entrySet().iterator(); it.hasNext(); it.next()) {
                count++;
            }
            return count;
        }

        @Override
        public boolean isEmpty() {
            return lowestNode() == NULL_NODE;
        }

        @Override
        public boolean containsKey(final Object key) {
            if (key == null) return false;
            return inRange((K) key) && map.containsKey(key);
        }

        @Override
        public #boxedValue# get(final Object key) {
            if (key == null) return null;
            if (!inRange((K) key)) return null;
            return map.get(key);
        }

        @Override
        public #boxedValue# put(final K key, final #boxedValue# value) {
            if (key == null || value == null) throw new NullPointerException();
            if (!inRange(key)) {
                throw new IllegalArgumentException("Key out of range");
            }
            return map.put(key, value);
        }

        @Override
        public #boxedValue# remove(final Object key) {
            if (key == null) return null;
            if (!inRange((K) key)) return null;
            return map.remove(key);
        }

        @Override
        public void clear() {
            for (Iterator<Entry<K, #boxedValue#>> it = entrySet().iterator(); it.hasNext(); ) {
                it.next();
                it.remove();
            }
        }

        @Override
        public Entry<K, #boxedValue#> firstEntry() {
            final int node = lowestNode();
            return node != NULL_NODE ? new SimpleEntry<>(map.keys[node], map.values[node]) : null;
        }

        @Override
        public K firstKey() {
            final int node = lowestNode();
            if (node == NULL_NODE) throw new NoSuchElementException();
            return map.keys[node];
        }

        @Override
        public Entry<K, #boxedValue#> lastEntry() {
            final int node = highestNode();
            return node != NULL_NODE ? new SimpleEntry<>(map.keys[node], map.values[node]) : null;
        }

        @Override
        public K lastKey() {
            final int node = highestNode();
            if (node == NULL_NODE) throw new NoSuchElementException();
            return map.keys[node];
        }

        @Override
        public Entry<K, #boxedValue#> pollFirstEntry() {
            final int node = lowestNode();
            if (node == NULL_NODE) return null;
            final Entry<K, #boxedValue#> entry = new SimpleEntry<>(map.keys[node], map.values[node]);
            map.deleteNode(node);
            return entry;
        }

        @Override
        public Entry<K, #boxedValue#> pollLastEntry() {
            final int node = highestNode();
            if (node == NULL_NODE) return null;
            final Entry<K, #boxedValue#> entry = new SimpleEntry<>(map.keys[node], map.values[node]);
            map.deleteNode(node);
            return entry;
        }

        @Override
        public Entry<K, #boxedValue#> lowerEntry(K key) {
            final int node = lowerNode(key);
            return node != NULL_NODE ? new SimpleEntry<>(map.keys[node], map.values[node]) : null;
        }

        @Override
        public K lowerKey(K key) {
            final int node = lowerNode(key);
            return node != NULL_NODE ? map.keys[node] : null;
        }

        private int lowerNode(K key) {
            if (key == null) throw new NullPointerException();
            K k = (K) key;
            int node;
            if (hasUpperBound && !inUpperRange(k, false)) {
                node = highestNode();
            } else {
                node = map.lowerNode(k);
            }
            return (node != NULL_NODE && inRange(map.keys[node])) ?
                    node : NULL_NODE;
        }

        @Override
        public Entry<K, #boxedValue#> floorEntry(K key) {
            final int node = floorNode(key);
            return node != NULL_NODE ? new SimpleEntry<>(map.keys[node], map.values[node]) : null;
        }

        @Override
        public K floorKey(K key) {
            final int node = floorNode(key);
            return node != NULL_NODE ? map.keys[node] : null;
        }

        private int floorNode(K key) {
            if (key == null) throw new NullPointerException();
            K k = (K) key;
            int node;
            if (hasUpperBound && !inUpperRange(k, true)) {
                node = highestNode();
            } else {
                node = map.floorNode(k);
            }
            return (node != NULL_NODE && inRange(map.keys[node])) ?
                    node : NULL_NODE;
        }

        @Override
        public Entry<K, #boxedValue#> ceilingEntry(K key) {
            final int node = ceilingNode(key);
            return node != NULL_NODE ? new SimpleEntry<>(map.keys[node], map.values[node]) : null;
        }

        @Override
        public K ceilingKey(K key) {
            final int node = ceilingNode(key);
            return node != NULL_NODE ? map.keys[node] : null;
        }

        private int ceilingNode(K key) {
            if (key == null) throw new NullPointerException();
            K k = (K) key;
            int node;
            if (hasLowerBound && !inLowerRange(k, true)) {
                node = lowestNode();
            } else {
                node = map.ceilingNode(k);
            }
            return (node != NULL_NODE && inRange(map.keys[node])) ?
                    node : NULL_NODE;
        }

        @Override
        public Entry<K, #boxedValue#> higherEntry(K key) {
            final int node = higherNode(key);
            return node != NULL_NODE ? new SimpleEntry<>(map.keys[node], map.values[node]) : null;
        }

        @Override
        public K higherKey(K key) {
            final int node = higherNode(key);
            return node != NULL_NODE ? map.keys[node] : null;
        }

        private int higherNode(K key) {
            if (key == null) throw new NullPointerException();
            K k = (K) key;
            int node;
            if (hasLowerBound && !inLowerRange(k, false)) {
                node = lowestNode();
            } else {
                node = map.higherNode(k);
            }
            return (node != NULL_NODE && inRange(map.keys[node])) ?
                    node : NULL_NODE;
        }

        @Override
        public Comparator<?
                super K> comparator() { return null; }

        @Override
        public NavigableMap<K, #boxedValue#> descendingMap() {
            return new SubMap(map, fromKey, fromInclusive, toKey, toInclusive).new DescendingSubMap();
        }

        @Override
        public NavigableSet<K> navigableKeySet() {
            return new KeySet(this, false);
        }

        @Override
        public NavigableSet<K> descendingKeySet() {
            return new KeySet(this, true);
        }

        @Override
        public NavigableMap<K, #boxedValue#> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
            if (fromKey == null || toKey == null) throw new NullPointerException();
            K from = (K)fromKey;
            K to = (K)toKey;
            if (!inRange(from) || !inRange(to)) throw new IllegalArgumentException("Key out of range");
            return new SubMap(map, fromKey, fromInclusive, toKey, toInclusive);
        }

        @Override
        public NavigableMap<K, #boxedValue#> headMap(K toKey, boolean inclusive) {
            if (toKey == null) throw new NullPointerException();
            if (!inRange((K)toKey)) throw new IllegalArgumentException("Key out of range");
            return new SubMap(map, fromKey, fromInclusive, toKey, inclusive);
        }

        @Override
        public NavigableMap<K, #boxedValue#> tailMap(K fromKey, boolean inclusive) {
            if (fromKey == null) throw new NullPointerException();
            if (!inRange((K)fromKey)) throw new IllegalArgumentException("Key out of range");
            return new SubMap(map, fromKey, inclusive, toKey, toInclusive);
        }

        @Override public SortedMap<K, #boxedValue#> subMap(K fromKey, K toKey) { return subMap(fromKey, true, toKey, false);
        }
        @Override public SortedMap<K, #boxedValue#> headMap(K toKey) { return headMap(toKey, false);
        }
        @Override public SortedMap<K, #boxedValue#> tailMap(K fromKey) { return tailMap(fromKey, true);
        }

        @Override
        public Set<Entry<K, #boxedValue#>> entrySet() {
            return new SubMapEntrySet();
        }

        private final class SubMapEntrySet extends AbstractSet<Entry<K, #boxedValue#>> {
            @Override
            public int size() {
                return SubMap.this.size();
            }
            @Override
            public Iterator<Entry<K, #boxedValue#>> iterator() {
                return new SubMapEntryIterator(lowestNode());
            }
        }

        private final class SubMapEntryIterator extends BaseIterator<Entry<K, #boxedValue#>> {
            SubMapEntryIterator(int startNode) {
                super(startNode);
            }

            @Override
            public boolean hasNext() {
                return next != NULL_NODE && inRange(map.keys[next]);
            }

            @Override
            public Entry<K, #boxedValue#> next() {
                if (next == NULL_NODE || !inRange(map.keys[next])) {
                    throw new NoSuchElementException();
                }
                final int node = nextAscending();
                return new SimpleEntry<>(map.keys[node], map.values[node]);
            }
        }

        private final class DescendingSubMap extends AbstractMap<K, #boxedValue#> implements NavigableMap<K, #boxedValue#> {
            @Override public int size() { return SubMap.this.size();
            }
            @Override public boolean isEmpty() { return SubMap.this.isEmpty();
            }
            @Override public boolean containsKey(Object key) { return SubMap.this.containsKey(key);
            }
            @Override public #boxedValue# get(Object key) { return SubMap.this.get(key);
            }
            @Override public #boxedValue# put(K key, #boxedValue# value) { return SubMap.this.put(key, value);
            }
            @Override public #boxedValue# remove(Object key) { return SubMap.this.remove(key);
            }
            @Override public void clear() { SubMap.this.clear();
            }
            @Override public Comparator<?
                    super K> comparator() { return null; }

            @Override public Entry<K, #boxedValue#> firstEntry() { return SubMap.this.lastEntry();
            }
            @Override public K firstKey() { return SubMap.this.lastKey();
            }
            @Override public Entry<K, #boxedValue#> lastEntry() { return SubMap.this.firstEntry();
            }
            @Override public K lastKey() { return SubMap.this.firstKey();
            }

            @Override public Entry<K, #boxedValue#> pollFirstEntry() { return SubMap.this.pollLastEntry();
            }
            @Override public Entry<K, #boxedValue#> pollLastEntry() { return SubMap.this.pollFirstEntry();
            }

            @Override public Entry<K, #boxedValue#> lowerEntry(K key) { return SubMap.this.higherEntry(key);
            }
            @Override public K lowerKey(K key) { return SubMap.this.higherKey(key);
            }
            @Override public Entry<K, #boxedValue#> floorEntry(K key) { return SubMap.this.ceilingEntry(key);
            }
            @Override public K floorKey(K key) { return SubMap.this.ceilingKey(key);
            }
            @Override public Entry<K, #boxedValue#> ceilingEntry(K key) { return SubMap.this.floorEntry(key);
            }
            @Override public K ceilingKey(K key) { return SubMap.this.floorKey(key);
            }
            @Override public Entry<K, #boxedValue#> higherEntry(K key) { return SubMap.this.lowerEntry(key);
            }
            @Override public K higherKey(K key) { return SubMap.this.lowerKey(key);
            }

            @Override public NavigableMap<K, #boxedValue#> descendingMap() { return SubMap.this;
            }
            @Override public NavigableSet<K> navigableKeySet() { return new KeySet(this, false);
            }
            @Override public NavigableSet<K> descendingKeySet() { return SubMap.this.navigableKeySet();
            }

            @Override public NavigableMap<K, #boxedValue#> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) { return SubMap.this.subMap(toKey, toInclusive, fromKey, fromInclusive).descendingMap();
            }
            @Override public NavigableMap<K, #boxedValue#> headMap(K toKey, boolean inclusive) { return SubMap.this.tailMap(toKey, inclusive).descendingMap();
            }
            @Override public NavigableMap<K, #boxedValue#> tailMap(K fromKey, boolean inclusive) { return SubMap.this.headMap(fromKey, inclusive).descendingMap();
            }
            @Override public SortedMap<K, #boxedValue#> subMap(K fromKey, K toKey) { return subMap(fromKey, true, toKey, false);
            }
            @Override public SortedMap<K, #boxedValue#> headMap(K toKey) { return headMap(toKey, false);
            }
            @Override public SortedMap<K, #boxedValue#> tailMap(K fromKey) { return tailMap(fromKey, true);
            }

            @Override public Set<Entry<K, #boxedValue#>> entrySet() { return new DescendingSubMapEntrySet();
            }
        }

        private final class DescendingSubMapEntrySet extends AbstractSet<Entry<K, #boxedValue#>> {
            @Override
            public int size() {
                return SubMap.this.size();
            }
            @Override
            public Iterator<Entry<K, #boxedValue#>> iterator() {
                return new DescendingSubMapEntryIterator(highestNode());
            }
        }

        private final class DescendingSubMapEntryIterator extends BaseIterator<Entry<K, #boxedValue#>> {
            DescendingSubMapEntryIterator(int startNode) {
                super(startNode);
            }

            @Override
            public boolean hasNext() {
                return next != NULL_NODE && inRange(map.keys[next]);
            }

            @Override
            public Entry<K, #boxedValue#> next() {
                if (next == NULL_NODE || !inRange(map.keys[next])) {
                    throw new NoSuchElementException();
                }
                final int node = nextDescending();
                return new SimpleEntry<>(map.keys[node], map.values[node]);
            }
        }
    }

    // ------------------- DescendingMap Implementation -------------------

    private final class DescendingMap extends AbstractMap<K, #boxedValue#> implements NavigableMap<K, #boxedValue#> {
        private final Object2#upperValue#TreeMap<K> map;
        DescendingMap(final Object2#upperValue#TreeMap<K> map) { this.map = map; }

        @Override public int size() { return map.size();
        }
        @Override public boolean isEmpty() { return map.isEmpty();
        }
        @Override public boolean containsKey(Object key) { return map.containsKey(key);
        }
        @Override public #boxedValue# get(Object key) { return map.get(key);
        }
        @Override public #boxedValue# put(K key, #boxedValue# value) { return map.put(key, value);
        }
        @Override public #boxedValue# remove(Object key) { return map.remove(key);
        }
        @Override public void clear() { map.clear();
        }
        @Override public Comparator<? super K> comparator() { return null;
        }

        @Override public Entry<K, #boxedValue#> firstEntry() { return map.lastEntry();
        }
        @Override public K firstKey() { return map.lastKey();
        }
        @Override public Entry<K, #boxedValue#> lastEntry() { return map.firstEntry();
        }
        @Override public K lastKey() { return map.firstKey();
        }

        @Override public Entry<K, #boxedValue#> pollFirstEntry() { return map.pollLastEntry();
        }
        @Override public Entry<K, #boxedValue#> pollLastEntry() { return map.pollFirstEntry();
        }

        @Override public Entry<K, #boxedValue#> lowerEntry(K key) { return map.higherEntry(key);
        }
        @Override public K lowerKey(K key) { return map.higherKey(key);
        }
        @Override public Entry<K, #boxedValue#> floorEntry(K key) { return map.ceilingEntry(key);
        }
        @Override public K floorKey(K key) { return map.ceilingKey(key);
        }
        @Override public Entry<K, #boxedValue#> ceilingEntry(K key) { return map.floorEntry(key);
        }
        @Override public K ceilingKey(K key) { return map.floorKey(key);
        }
        @Override public Entry<K, #boxedValue#> higherEntry(K key) { return map.lowerEntry(key);
        }
        @Override public K higherKey(K key) { return map.lowerKey(key);
        }

        @Override public NavigableMap<K, #boxedValue#> descendingMap() { return map;
        }
        @Override public NavigableSet<K> navigableKeySet() { return new KeySet(this, false);
        }
        @Override public NavigableSet<K> descendingKeySet() { return map.navigableKeySet();
        }

        @Override public NavigableMap<K, #boxedValue#> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) { return map.subMap(toKey, toInclusive, fromKey, fromInclusive).descendingMap();
        }
        @Override public NavigableMap<K, #boxedValue#> headMap(K toKey, boolean inclusive) { return map.tailMap(toKey, inclusive).descendingMap();
        }
        @Override public NavigableMap<K, #boxedValue#> tailMap(K fromKey, boolean inclusive) { return map.headMap(fromKey, inclusive).descendingMap();
        }
        @Override public SortedMap<K, #boxedValue#> subMap(K fromKey, K toKey) { return subMap(fromKey, true, toKey, false);
        }
        @Override public SortedMap<K, #boxedValue#> headMap(K toKey) { return headMap(toKey, false);
        }
        @Override public SortedMap<K, #boxedValue#> tailMap(K fromKey) { return tailMap(fromKey, true);
        }

        @Override
        public Set<Entry<K, #boxedValue#>> entrySet() {
            return new AbstractSet<Entry<K, #boxedValue#>>() {
                @Override public int size() { return map.size();
                }
                @Override public void clear() { map.clear();
                }
                @Override public Iterator<Entry<K, #boxedValue#>> iterator() {
                    return new DescendingEntryIterator(map.root == NULL_NODE ? NULL_NODE : map.maximum(map.root));
                }
            };
        }
    }

    // ------------------- KeySet Implementation -------------------

    private final class KeySet extends AbstractSet<K> implements NavigableSet<K> {
        private final NavigableMap<K, #boxedValue#> map;
        private final boolean descending;

        KeySet(NavigableMap<K, #boxedValue#> map, boolean descending) {
            this.map = map;
            this.descending = descending;
        }

        @Override
        public int size() { return map.size();
        }
        @Override
        public boolean isEmpty() { return map.isEmpty();
        }
        @Override
        public boolean contains(Object o) { return map.containsKey((K) o);
        }
        @Override
        public void clear() { map.clear();
        }

        @Override
        public K lower(K k) { return descending ?
                map.higherKey(k) : map.lowerKey(k); }
        @Override
        public K floor(K k) { return descending ?
                map.ceilingKey(k) : map.floorKey(k); }
        @Override
        public K ceiling(K k) { return descending ?
                map.floorKey(k) : map.ceilingKey(k); }
        @Override
        public K higher(K k) { return descending ?
                map.lowerKey(k) : map.higherKey(k); }

        @Override
        public K first() { return descending ?
                map.lastKey() : map.firstKey(); }
        @Override
        public K last() { return descending ?
                map.firstKey() : map.lastKey(); }

        @Override
        public K pollFirst() {
            Entry<K, #boxedValue#> e = descending ?
                    map.pollLastEntry() : map.pollFirstEntry();
            return e != null ? e.getKey() : null;
        }

        @Override
        public K pollLast() {
            Entry<K, #boxedValue#> e = descending ?
                    map.pollFirstEntry() : map.pollLastEntry();
            return e != null ? e.getKey() : null;
        }

        @Override
        public Comparator<?
                super K> comparator() { return map.comparator(); }

        @Override
        public Iterator<K> iterator() {
            if (map instanceof Object2#upperValue#TreeMap<K> m) {
                int startNode = m.root == NULL_NODE ?
                        NULL_NODE : (descending ? m.maximum(m.root) : m.minimum(m.root));
                return descending ? m.new DescendingKeyIterator(startNode) : m.new KeyIterator(startNode);
            }
            if (map instanceof SubMap m) {
                int startNode = descending ?
                        m.highestNode() : m.lowestNode();
                return new Iterator<K>() {
                    final Iterator<Entry<K, #boxedValue#>> entryIt = m.entrySet().iterator();
                    @Override public boolean hasNext() { return entryIt.hasNext(); }
                    @Override public K next() { return entryIt.next().getKey();
                    }
                    @Override public void remove() { entryIt.remove();
                    }
                };
            }
            return (descending ? map.descendingMap() : map).keySet().iterator();
        }

        @Override
        public Iterator<K> descendingIterator() {
            return new KeySet(map, !descending).iterator();
        }

        @Override
        public NavigableSet<K> descendingSet() {
            return new KeySet(map, !descending);
        }

        @Override
        public NavigableSet<K> subSet(K from, boolean fromInc, K to, boolean toInc) {
            return new KeySet(map.subMap(from, fromInc, to, toInc), descending);
        }
        @Override
        public NavigableSet<K> headSet(K to, boolean toInc) {
            return new KeySet(map.headMap(to, toInc), descending);
        }
        @Override
        public NavigableSet<K> tailSet(K from, boolean fromInc) {
            return new KeySet(map.tailMap(from, fromInc), descending);
        }
        @Override public SortedSet<K> subSet(K from, K to) { return subSet(from, true, to, false);
        }
        @Override public SortedSet<K> headSet(K to) { return headSet(to, false);
        }
        @Override public SortedSet<K> tailSet(K from) { return tailSet(from, true);
        }
    }

    // ------------------- Builder -------------------

    public static class Builder<K> {
        private int initialCapacity = DEFAULT_INITIAL_CAPACITY;
        private #primitiveValue# nullValue = DEFAULT_NULL_VALUE;
        private boolean enableIteratorPool = true;

        public Builder<K> initialCapacity(final int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        public Builder<K> nullValue(final #primitiveValue# nullValue) {
            this.nullValue = nullValue;
            return this;
        }

        public Builder<K> disableIteratorPool() {
            this.enableIteratorPool = false;
            return this;
        }

        public Object2#upperValue#TreeMap<K> build() {
            return new Object2#upperValue#TreeMap<>(initialCapacity, nullValue, enableIteratorPool);
        }
    }
}
