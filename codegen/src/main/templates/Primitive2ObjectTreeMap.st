package dev.aqsar.pcore.collections;

import java.util.*;

/**
 * An allocation-free Red-Black Tree implementation that stores #primitiveKey#-to-Object mappings without boxing keys.
 * Uses array-based node storage with a free list to eliminate allocations after initial capacity.
 * Provides O(log n) operations for put, get, remove and maintains sorted order by keys.
 * <p>
 * For floating point keys (float/double):
 * - Uses bitwise comparison (Double.compare/Float.compare)
 * - NaN values are supported and treated consistently
 * - +0.0 and -0.0 are treated as equal
 * <p>
 * Null keys and null values are not supported.
 * <p>
 * This class is not thread-safe.
 *
 * @param <V> the type of values stored in this map
 */
public final class #upperKey#2ObjectTreeMap<V> implements NavigableMap<#boxedKey#, V> {

    public static final int DEFAULT_INITIAL_CAPACITY = 16;

    private static final boolean RED = true;
    private static final boolean BLACK = false;
    private static final int NULL_NODE = -1;
    private static final int ITERATOR_POOL_SIZE = 8;

    // Node storage arrays (structure of arrays for cache efficiency)
    private #primitiveKey#[] keys;
    private V[] values;
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
    private int nextNodeIndex = 0;

    // Iterator pool
    private final Object[] iteratorPool;
    private long iteratorAvailableBits;

    @SuppressWarnings("unchecked")
    private #upperKey#2ObjectTreeMap(
            final int initialCapacity,
            final boolean enableIteratorPool
    ) {
        this.capacity = Math.max(initialCapacity, DEFAULT_INITIAL_CAPACITY);

        this.keys = new #primitiveKey#[capacity];
        this.values = (V[]) new Object[capacity];
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

    public static <V> Builder<V> builder() { return new Builder<>(); }

    private void initializeFreeList() {
        for (int i = 0; i < capacity - 1; i++) {
            rightChild[i] = i + 1;
        }
        rightChild[capacity - 1] = NULL_NODE;
        freeListHead = 0;
        nextNodeIndex = 0;
    }

    // ------------------- Node Management -------------------

    private int allocateNode(final #primitiveKey# key, final V value, final int parentNode) {
        int nodeIndex;

        if (freeListHead != NULL_NODE) {
            nodeIndex = freeListHead;
            freeListHead = rightChild[freeListHead];
        } else if (nextNodeIndex < capacity) {
            nodeIndex = nextNodeIndex++;
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

        values[nodeIndex] = null;
        rightChild[nodeIndex] = freeListHead;
        freeListHead = nodeIndex;
    }

    private void grow() {
        final int newCapacity = capacity * 2;

        keys = Arrays.copyOf(keys, newCapacity);
        values = Arrays.copyOf(values, newCapacity);
        leftChild = Arrays.copyOf(leftChild, newCapacity);
        rightChild = Arrays.copyOf(rightChild, newCapacity);
        parent = Arrays.copyOf(parent, newCapacity);
        color = Arrays.copyOf(color, newCapacity);

        for (int i = capacity; i < newCapacity - 1; i++) {
            rightChild[i] = i + 1;
        }
        rightChild[newCapacity - 1] = freeListHead;
        freeListHead = capacity;

        capacity = newCapacity;
    }

    // ------------------- Primitive Operations -------------------

    public void put(final #primitiveKey# key, final V value) {
        #null_key_check#
        if (value == null) throw new IllegalArgumentException("Null values are not permitted");

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

    public V get(final #primitiveKey# key) {
        #null_key_check#
        final int node = getNode(key);
        return node != NULL_NODE ? values[node] : null;
    }

    public V getOrDefault(final #primitiveKey# key, final V defaultValue) {
        #null_key_check#
        final int node = getNode(key);
        return node != NULL_NODE ? values[node] : defaultValue;
    }

    public boolean containsKey(final #primitiveKey# key) {
        #null_key_check#
        return getNode(key) != NULL_NODE;
    }

    @Override
    public boolean containsValue(final Object value) {
        if (value == null) return false;
        return containsValueInSubtree(root, value);
    }

    private boolean containsValueInSubtree(final int node, final Object value) {
        if (node == NULL_NODE) return false;
        if (value.equals(values[node])) return true;
        return containsValueInSubtree(leftChild[node], value) || containsValueInSubtree(rightChild[node], value);
    }

    public V remove(final #primitiveKey# key) {
        #null_key_check#
        final int node = getNode(key);
        if (node == NULL_NODE) return null;

        final V oldValue = values[node];
        deleteNode(node);
        return oldValue;
    }

    public #primitiveKey# first#upperKey#Key() {
        if (root == NULL_NODE) throw new NoSuchElementException();
        return keys[minimum(root)];
    }

    public #primitiveKey# last#upperKey#Key() {
        if (root == NULL_NODE) throw new NoSuchElementException();
        return keys[maximum(root)];
    }

    public #boxedKey# floorKey(final #primitiveKey# key) {
        #null_key_check#
        final int node = floorNode(key);
        return node != NULL_NODE ? keys[node] : null;
    }

    public #boxedKey# ceilingKey(final #primitiveKey# key) {
        #null_key_check#
        final int node = ceilingNode(key);
        return node != NULL_NODE ? keys[node] : null;
    }

    public #boxedKey# lowerKey(final #primitiveKey# key) {
        #null_key_check#
        final int node = lowerNode(key);
        return node != NULL_NODE ? keys[node] : null;
    }

    public #boxedKey# higherKey(final #primitiveKey# key) {
        #null_key_check#
        final int node = higherNode(key);
        return node != NULL_NODE ? keys[node] : null;
    }

    public void forEach#upperKey#Object(final #upperKey#ObjectConsumer<V> action) {
        Objects.requireNonNull(action);
        forEachInOrder(root, action);
    }

    private void forEachInOrder(final int node, final #upperKey#ObjectConsumer<V> action) {
        if (node == NULL_NODE) return;
        forEachInOrder(leftChild[node], action);
        action.accept(keys[node], values[node]);
        forEachInOrder(rightChild[node], action);
    }

    @FunctionalInterface
    public interface #upperKey#ObjectConsumer<V> {
        void accept(#primitiveKey# key, V value);
    }

    // ------------------- Helper Methods -------------------

    private int getNode(final #primitiveKey# key) {
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

    private int floorNode(final #primitiveKey# key) {
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

    private int ceilingNode(final #primitiveKey# key) {
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

    private int lowerNode(final #primitiveKey# key) {
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

    private int higherNode(final #primitiveKey# key) {
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

    private int compareKeys(final #primitiveKey# k1, final #primitiveKey# k2) {
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
            keys[node] = keys[toDelete];
            values[node] = values[toDelete];
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

    // ------------------- Map Interface -------------------

    @Override
    public V put(final #boxedKey# key, final V value) {
        if (key == null || value == null) {
            throw new NullPointerException("Null keys and values are not supported");
        }
        final V oldValue = get((#primitiveKey#) key);
        put((#primitiveKey#) key, value);
        return oldValue;
    }

    @Override
    public V get(final Object key) {
        if (!(key instanceof #boxedKey#)) return null;
        return get((#primitiveKey#) key);
    }

    @Override
    public boolean containsKey(final Object key) {
        if (!(key instanceof #boxedKey#)) return false;
        return containsKey((#primitiveKey#) key);
    }

    @Override
    public V remove(final Object key) {
        if (!(key instanceof #boxedKey#)) return null;
        return remove((#primitiveKey#) key);
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
    public void putAll(final Map<? extends #boxedKey#, ? extends V> m) {
        for (Map.Entry<? extends #boxedKey#, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public int size() { return size; }

    @Override
    public boolean isEmpty() { return size == 0; }

    @Override
    public Set<#boxedKey#> keySet() {
        return new AbstractSet<#boxedKey#>() {
            @Override public int size() { return #upperKey#2ObjectTreeMap.this.size(); }
            @Override public boolean contains(final Object o) { return containsKey(o); }
            @Override public Iterator<#boxedKey#> iterator() { return new KeyIterator(); }
            @Override public void clear() { #upperKey#2ObjectTreeMap.this.clear(); }
        };
    }

    @Override
    public Collection<V> values() {
        return new AbstractCollection<V>() {
            @Override public int size() { return #upperKey#2ObjectTreeMap.this.size(); }
            @Override public boolean contains(final Object o) { return containsValue(o); }
            @Override public Iterator<V> iterator() { return new ValueIterator(); }
            @Override public void clear() { #upperKey#2ObjectTreeMap.this.clear(); }
        };
    }

    @Override
    public Set<Entry<#boxedKey#, V>> entrySet() {
        return new AbstractSet<Entry<#boxedKey#, V>>() {
            @Override public int size() { return #upperKey#2ObjectTreeMap.this.size(); }
            @Override public Iterator<Entry<#boxedKey#, V>> iterator() { return new EntryIterator(); }
            @Override public void clear() { #upperKey#2ObjectTreeMap.this.clear(); }
        };
    }

    // ------------------- Iterator Pool -------------------

    public #upperKey#ObjectTreeMapIterator borrowIterator() {
        if (iteratorPool == null) return null;

        long bits = iteratorAvailableBits;
        if (bits == 0) return null;

        final int poolIndex = Long.numberOfTrailingZeros(bits);
        iteratorAvailableBits = bits & ~(1L << poolIndex);

        @SuppressWarnings("unchecked")
        final #upperKey#ObjectTreeMapIterator iter = ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        iter.reset();
        return iter;
    }

    public void returnIterator(final #upperKey#ObjectTreeMapIterator iterator) {
        if (iteratorPool == null || iterator == null) return;
        final int poolIndex = iterator.poolIndex;
        if (poolIndex < 0 || poolIndex >= ITERATOR_POOL_SIZE) return;
        @SuppressWarnings("unchecked")
        final #upperKey#ObjectTreeMapIterator iter = ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        if (iter != iterator) return;

        iteratorAvailableBits |= (1L << poolIndex);
    }

    public int availableIteratorCount() {
        return iteratorPool == null ? 0 : Long.bitCount(iteratorAvailableBits);
    }

    private final class IteratorPoolEntry {
        final #upperKey#ObjectTreeMapIterator iterator;

        IteratorPoolEntry(final int poolIndex) {
            this.iterator = new #upperKey#ObjectTreeMapIterator(poolIndex);
        }
    }

    public final class #upperKey#ObjectTreeMapIterator implements AutoCloseable {
        private long p0, p1, p2, p3, p4, p5, p6;

        private final int poolIndex;
        private int next;
        private int lastReturned;
        private int expectedModCount;

        private long p8, p9, p10, p11, p12;

        private #upperKey#ObjectTreeMapIterator(final int poolIndex) {
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

        public #primitiveKey# nextKey() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            if (next == NULL_NODE) {
                throw new NoSuchElementException();
            }

            lastReturned = next;
            final #primitiveKey# key = keys[next];
            next = successor(next);
            return key;
        }

        public V nextValue() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            if (next == NULL_NODE) {
                throw new NoSuchElementException();
            }

            lastReturned = next;
            final V value = values[next];
            next = successor(next);
            return value;
        }

        public #primitiveKey# peekNextKey() {
            if (next == NULL_NODE) {
                throw new NoSuchElementException();
            }
            return keys[next];
        }

        public V peekNextValue() {
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

        public void forEachRemaining(final #upperKey#ObjectConsumer<V> action) {
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
            #upperKey#2ObjectTreeMap.this.returnIterator(this);
        }
    }

    // ------------------- Boxed Iterators -------------------

    private abstract class BaseIterator<T> implements Iterator<T> {
        int next;
        int expectedModCount = modCount;

        BaseIterator() {
            next = root != NULL_NODE ? minimum(root) : NULL_NODE;
        }

        @Override
        public boolean hasNext() {
            return next != NULL_NODE;
        }

        int nextNode() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            if (next == NULL_NODE) {
                throw new NoSuchElementException();
            }
            final int current = next;
            next = successor(next);
            return current;
        }
    }

    private final class KeyIterator extends BaseIterator<#boxedKey#> {
        @Override
        public #boxedKey# next() {
            return keys[nextNode()];
        }
    }

    private final class ValueIterator extends BaseIterator<V> {
        @Override
        public V next() {
            return values[nextNode()];
        }
    }

    private final class EntryIterator extends BaseIterator<Entry<#boxedKey#, V>> {
        @Override
        public Entry<#boxedKey#, V> next() {
            final int node = nextNode();
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

        @Override public K getKey() { return key; }
        @Override public V getValue() { return value; }
        @Override public V setValue(final V value) {
            final V old = this.value;
            this.value = value;
            return old;
        }
    }

    // ------------------- NavigableMap Implementation (Stub Methods) -------------------

    @Override public Comparator<? super #boxedKey#> comparator() { return null; }
    @Override public #boxedKey# firstKey() { return first#upperKey#Key(); }
    @Override public #boxedKey# lastKey() { return last#upperKey#Key(); }
    @Override public #boxedKey# lowerKey(#boxedKey# key) { return lowerKey((#primitiveKey#) key); }
    @Override public #boxedKey# floorKey(#boxedKey# key) { return floorKey((#primitiveKey#) key); }
    @Override public #boxedKey# ceilingKey(#boxedKey# key) { return ceilingKey((#primitiveKey#) key); }
    @Override public #boxedKey# higherKey(#boxedKey# key) { return higherKey((#primitiveKey#) key); }

    @Override
    public Entry<#boxedKey#, V> lowerEntry(final #boxedKey# key) {
        if (key == null) return null;
        final int node = lowerNode((#primitiveKey#) key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    @Override
    public Entry<#boxedKey#, V> floorEntry(final #boxedKey# key) {
        if (key == null) return null;
        final int node = floorNode((#primitiveKey#) key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    @Override
    public Entry<#boxedKey#, V> ceilingEntry(final #boxedKey# key) {
        if (key == null) return null;
        final int node = ceilingNode((#primitiveKey#) key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    @Override
    public Entry<#boxedKey#, V> higherEntry(final #boxedKey# key) {
        if (key == null) return null;
        final int node = higherNode((#primitiveKey#) key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    @Override
    public Entry<#boxedKey#, V> firstEntry() {
        if (root == NULL_NODE) return null;
        final int node = minimum(root);
        return new SimpleEntry<>(keys[node], values[node]);
    }

    @Override
    public Entry<#boxedKey#, V> lastEntry() {
        if (root == NULL_NODE) return null;
        final int node = maximum(root);
        return new SimpleEntry<>(keys[node], values[node]);
    }

    @Override
    public Entry<#boxedKey#, V> pollFirstEntry() {
        if (root == NULL_NODE) return null;
        final int node = minimum(root);
        final Entry<#boxedKey#, V> entry = new SimpleEntry<>(keys[node], values[node]);
        deleteNode(node);
        return entry;
    }

    @Override
    public Entry<#boxedKey#, V> pollLastEntry() {
        if (root == NULL_NODE) return null;
        final int node = maximum(root);
        final Entry<#boxedKey#, V> entry = new SimpleEntry<>(keys[node], values[node]);
        deleteNode(node);
        return entry;
    }

    @Override
    public NavigableMap<#boxedKey#, V> descendingMap() {
        throw new UnsupportedOperationException("descendingMap not implemented");
    }

    @Override
    public NavigableSet<#boxedKey#> navigableKeySet() {
        throw new UnsupportedOperationException("navigableKeySet not implemented");
    }

    @Override
    public NavigableSet<#boxedKey#> descendingKeySet() {
        throw new UnsupportedOperationException("descendingKeySet not implemented");
    }

    @Override
    public NavigableMap<#boxedKey#, V> subMap(#boxedKey# fromKey, boolean fromInclusive, #boxedKey# toKey, boolean toInclusive) {
        throw new UnsupportedOperationException("subMap not implemented");
    }

    @Override
    public NavigableMap<#boxedKey#, V> headMap(#boxedKey# toKey, boolean inclusive) {
        throw new UnsupportedOperationException("headMap not implemented");
    }

    @Override
    public NavigableMap<#boxedKey#, V> tailMap(#boxedKey# fromKey, boolean inclusive) {
        throw new UnsupportedOperationException("tailMap not implemented");
    }

    @Override
    public SortedMap<#boxedKey#, V> subMap(#boxedKey# fromKey, #boxedKey# toKey) {
        throw new UnsupportedOperationException("subMap not implemented");
    }

    @Override
    public SortedMap<#boxedKey#, V> headMap(#boxedKey# toKey) {
        throw new UnsupportedOperationException("headMap not implemented");
    }

    @Override
    public SortedMap<#boxedKey#, V> tailMap(#boxedKey# fromKey) {
        throw new UnsupportedOperationException("tailMap not implemented");
    }

    // ------------------- Builder -------------------

    public static class Builder<V> {
        private int initialCapacity = DEFAULT_INITIAL_CAPACITY;
        private boolean enableIteratorPool = true;

        public Builder<V> initialCapacity(final int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        public Builder<V> disableIteratorPool() {
            this.enableIteratorPool = false;
            return this;
        }

        public #upperKey#2ObjectTreeMap<V> build() {
            return new #upperKey#2ObjectTreeMap<>(initialCapacity, enableIteratorPool);
        }
    }
}
