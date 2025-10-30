package dev.aqsar.pcore.collections;

import java.util.*;

/**
 * An allocation-free Red-Black Tree implementation that stores #primitiveKey#-to-#primitiveValue# mappings without boxing.
 * Uses array-based node storage with a free list to eliminate allocations after initial capacity.
 * Provides O(log n) operations for put, get, remove and maintains sorted order by keys.
 * <p>
 * For floating point keys (float/double):
 * - Uses bitwise comparison (Double.compare/Float.compare)
 * - NaN values are supported and treated consistently
 * - +0.0 and -0.0 are treated as equal
 * <p>
 * This class is not thread-safe.
 */
public final class #upperKey#2#upperValue#TreeMap implements NavigableMap<#boxedKey#, #boxedValue#> {

    public static final #primitiveValue# DEFAULT_NULL_VALUE = #nullValue#;
    public static final int DEFAULT_INITIAL_CAPACITY = 16;

    private static final boolean RED = true;
    private static final boolean BLACK = false;
    private static final int NULL_NODE = -1;
    private static final int ITERATOR_POOL_SIZE = 8;

    // Node storage arrays (structure of arrays for cache efficiency)
    private #primitiveKey#[] keys;
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
    private final Object[] iteratorPool; // Really IteratorPoolEntry[]
    private long iteratorAvailableBits;

    private #upperKey#2#upperValue#TreeMap(
            final int initialCapacity,
            final #primitiveValue# nullValue,
            final boolean enableIteratorPool
    ) {
        this.capacity = Math.max(initialCapacity, DEFAULT_INITIAL_CAPACITY);
        this.nullValue = nullValue;

        this.keys = new #primitiveKey#[capacity];
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

    public static Builder builder() { return new Builder(); }

    private void initializeFreeList() {
        for (int i = 0; i < capacity - 1; i++) {
            rightChild[i] = i + 1;
        }
        rightChild[capacity - 1] = NULL_NODE;
        freeListHead = 0;
    }

    // ------------------- Node Management -------------------

    private int allocateNode(final #primitiveKey# key, final #primitiveValue# value, final int parentNode) {
        int nodeIndex;

        if (freeListHead != NULL_NODE) {
            // Reuse from free list
            nodeIndex = freeListHead;
            freeListHead = rightChild[freeListHead];
        } else {
            // Need to grow capacity
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

        // Add to free list
        rightChild[nodeIndex] = freeListHead;
        freeListHead = nodeIndex;
    }

    private void grow() {
        final int oldCapacity = capacity;
        final int newCapacity = capacity * 2;

        keys = Arrays.copyOf(keys, newCapacity);
        values = Arrays.copyOf(values, newCapacity);
        leftChild = Arrays.copyOf(leftChild, newCapacity);
        rightChild = Arrays.copyOf(rightChild, newCapacity);
        parent = Arrays.copyOf(parent, newCapacity);
        color = Arrays.copyOf(color, newCapacity);

        // Extend free list with new nodes
        for (int i = oldCapacity; i < newCapacity - 1; i++) {
            rightChild[i] = i + 1;
        }
        rightChild[newCapacity - 1] = freeListHead;
        freeListHead = oldCapacity;

        capacity = newCapacity;
    }

    // ------------------- Primitive Operations -------------------

    /**
     * Associates a primitive key with a primitive value.
     *
     * @param key the primitive key
     * @param value the primitive value
     */
    public void put(final #primitiveKey# key, final #primitiveValue# value) {
        #null_key_check#

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
     * Retrieves the primitive value for a primitive key.
     *
     * @param key the key to lookup
     * @return the associated value, or {@code nullValue} if the key is not present
     */
    public #primitiveValue# get(final #primitiveKey# key) {
        #null_key_check#
        final int node = getNode(key);
        return node != NULL_NODE ? values[node] : nullValue;
    }

    /**
     * Retrieves the primitive value for a key, or returns the default if not found.
     *
     * @param key the key to lookup
     * @param defaultValue the default value if key is absent
     * @return the value associated with the key, or {@code defaultValue}
     */
    public #primitiveValue# getOrDefault(final #primitiveKey# key, final #primitiveValue# defaultValue) {
        #null_key_check#
        final int node = getNode(key);
        return node != NULL_NODE ? values[node] : defaultValue;
    }

    /**
     * Checks if a primitive key exists in the map.
     *
     * @param key the key to check
     * @return {@code true} if the key is present, {@code false} otherwise
     */
    public boolean containsKey(final #primitiveKey# key) {
        #null_key_check#
        return getNode(key) != NULL_NODE;
    }

    /**
     * Checks if a primitive value exists in the map.
     *
     * @param value the value to check
     * @return {@code true} if the value is present, {@code false} otherwise
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
     * Removes a key-value mapping by primitive key.
     *
     * @param key the key to remove
     * @return the previous value associated with the key, or {@code nullValue} if not present
     */
    public #primitiveValue# remove(final #primitiveKey# key) {
        #null_key_check#
        final int node = getNode(key);
        if (node == NULL_NODE) return nullValue;

        final #primitiveValue# oldValue = values[node];
        deleteNode(node);
        return oldValue;
    }

    /**
     * Removes the entry for the primitive key only if it maps to the primitive value.
     *
     * @param key the primitive key
     * @param value the expected primitive value
     * @return true if the entry was removed
     */
    public boolean remove(final #primitiveKey# key, final #primitiveValue# value) {
        #null_key_check#
        final int node = getNode(key);
        if (node == NULL_NODE) return false;
        if (!isValueEqual(values[node], value)) return false;
        deleteNode(node);
        return true;
    }

    /**
     * Puts the primitive key-value pair only if the key is not already present.
     *
     * @param key the primitive key
     * @param value the primitive value
     * @return true if the value was inserted, false if key already existed
     */
    public boolean putIfAbsent(final #primitiveKey# key, final #primitiveValue# value) {
        #null_key_check#
        final int node = getNode(key);
        if (node != NULL_NODE) {
            return false;
        }
        put(key, value);
        return true;
    }

    /**
     * Replaces the entry for the primitive key only if currently mapped to the old value.
     *
     * @param key the primitive key
     * @param oldValue the expected current primitive value
     * @param newValue the new primitive value
     * @return true if the value was replaced
     */
    public boolean replace(final #primitiveKey# key, final #primitiveValue# oldValue, final #primitiveValue# newValue) {
        #null_key_check#
        final int node = getNode(key);
        if (node == NULL_NODE) return false;
        if (!isValueEqual(values[node], oldValue)) return false;
        values[node] = newValue;
        modCount++;
        return true;
    }

    /**
     * Replaces the entry for the primitive key only if currently mapped to some value.
     *
     * @param key the primitive key
     * @param value the new primitive value
     * @return the previous primitive value, or nullValue if absent
     */
    public #primitiveValue# replace(final #primitiveKey# key, final #primitiveValue# value) {
        #null_key_check#
        final int node = getNode(key);
        if (node == NULL_NODE) return nullValue;
        final #primitiveValue# oldValue = values[node];
        values[node] = value;
        modCount++;
        return oldValue;
    }

    /**
     * Returns the smallest key in the map.
     *
     * @return the minimum key, or throws NoSuchElementException if empty
     */
    public #primitiveKey# first#upperKey#Key() {
        if (root == NULL_NODE) throw new NoSuchElementException();
        return keys[minimum(root)];
    }

    /**
     * Returns the largest key in the map.
     *
     * @return the maximum key, or throws NoSuchElementException if empty
     */
    public #primitiveKey# last#upperKey#Key() {
        if (root == NULL_NODE) throw new NoSuchElementException();
        return keys[maximum(root)];
    }

    /**
     * Returns the value associated with the first (lowest) key.
     *
     * @return the first value, or nullValue if the map is empty
     */
    public #primitiveValue# first#upperKey#Value() {
        if (root == NULL_NODE) return nullValue;
        return values[minimum(root)];
    }

    /**
     * Returns the value associated with the last (highest) key.
     *
     * @return the last value, or nullValue if the map is empty
     */
    public #primitiveValue# last#upperKey#Value() {
        if (root == NULL_NODE) return nullValue;
        return values[maximum(root)];
    }

    /**
     * Returns the first (lowest) entry in the map.
     *
     * @return the first entry, or null if the map is empty
     */
    public Entry<#boxedKey#, #boxedValue#> first#upperKey#Entry() {
        if (root == NULL_NODE) return null;
        final int node = minimum(root);
        return new SimpleEntry<>(keys[node], values[node]);
    }

    /**
     * Returns the last (highest) entry in the map.
     *
     * @return the last entry, or null if the map is empty
     */
    public Entry<#boxedKey#, #boxedValue#> last#upperKey#Entry() {
        if (root == NULL_NODE) return null;
        final int node = maximum(root);
        return new SimpleEntry<>(keys[node], values[node]);
    }

    /**
     * Removes and returns the first (lowest) entry in the map.
     *
     * @return the removed first entry, or null if the map is empty
     */
    public Entry<#boxedKey#, #boxedValue#> pollFirst#upperKey#Entry() {
        if (root == NULL_NODE) return null;
        final int node = minimum(root);
        final Entry<#boxedKey#, #boxedValue#> entry = new SimpleEntry<>(keys[node], values[node]);
        deleteNode(node);
        return entry;
    }

    /**
     * Removes and returns the last (highest) entry in the map.
     *
     * @return the removed last entry, or null if the map is empty
     */
    public Entry<#boxedKey#, #boxedValue#> pollLast#upperKey#Entry() {
        if (root == NULL_NODE) return null;
        final int node = maximum(root);
        final Entry<#boxedKey#, #boxedValue#> entry = new SimpleEntry<>(keys[node], values[node]);
        deleteNode(node);
        return entry;
    }

    /**
     * Returns the greatest key less than or equal to the given key.
     *
     * @param key the key
     * @return the floor key, or null if no such key exists
     */
    public #boxedKey# floorKey(final #primitiveKey# key) {
        #null_key_check#
        final int node = floorNode(key);
        return node != NULL_NODE ? keys[node] : null;
    }

    /**
     * Returns the least key greater than or equal to the given key.
     *
     * @param key the key
     * @return the ceiling key, or null if no such key exists
     */
    public #boxedKey# ceilingKey(final #primitiveKey# key) {
        #null_key_check#
        final int node = ceilingNode(key);
        return node != NULL_NODE ? keys[node] : null;
    }

    /**
     * Returns the greatest key strictly less than the given key.
     *
     * @param key the key
     * @return the lower key, or null if no such key exists
     */
    public #boxedKey# lowerKey(final #primitiveKey# key) {
        #null_key_check#
        final int node = lowerNode(key);
        return node != NULL_NODE ? keys[node] : null;
    }

    /**
     * Returns the least key strictly greater than the given key.
     *
     * @param key the key
     * @return the higher key, or null if no such key exists
     */
    public #boxedKey# higherKey(final #primitiveKey# key) {
        #null_key_check#
        final int node = higherNode(key);
        return node != NULL_NODE ? keys[node] : null;
    }

    /**
     * Returns the value associated with the greatest key less than or equal to the given key.
     *
     * @param key the key
     * @return the floor value, or nullValue if no such key exists
     */
    public #primitiveValue# floorValue(final #primitiveKey# key) {
        #null_key_check#
        final int node = floorNode(key);
        return node != NULL_NODE ? values[node] : nullValue;
    }

    /**
     * Returns the value associated with the least key greater than or equal to the given key.
     *
     * @param key the key
     * @return the ceiling value, or nullValue if no such key exists
     */
    public #primitiveValue# ceilingValue(final #primitiveKey# key) {
        #null_key_check#
        final int node = ceilingNode(key);
        return node != NULL_NODE ? values[node] : nullValue;
    }

    /**
     * Returns the value associated with the greatest key strictly less than the given key.
     *
     * @param key the key
     * @return the lower value, or nullValue if no such key exists
     */
    public #primitiveValue# lowerValue(final #primitiveKey# key) {
        #null_key_check#
        final int node = lowerNode(key);
        return node != NULL_NODE ? values[node] : nullValue;
    }

    /**
     * Returns the value associated with the least key strictly greater than the given key.
     *
     * @param key the key
     * @return the higher value, or nullValue if no such key exists
     */
    public #primitiveValue# higherValue(final #primitiveKey# key) {
        #null_key_check#
        final int node = higherNode(key);
        return node != NULL_NODE ? values[node] : nullValue;
    }

    /**
     * Returns the greatest key-value entry less than or equal to the given key.
     *
     * @param key the key
     * @return the floor entry, or null if no such entry exists
     */
    public Entry<#boxedKey#, #boxedValue#> floor#upperKey#Entry(final #primitiveKey# key) {
        #null_key_check#
        final int node = floorNode(key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    /**
     * Returns the least key-value entry greater than or equal to the given key.
     *
     * @param key the key
     * @return the ceiling entry, or null if no such entry exists
     */
    public Entry<#boxedKey#, #boxedValue#> ceiling#upperKey#Entry(final #primitiveKey# key) {
        #null_key_check#
        final int node = ceilingNode(key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    /**
     * Returns the greatest key-value entry strictly less than the given key.
     *
     * @param key the key
     * @return the lower entry, or null if no such entry exists
     */
    public Entry<#boxedKey#, #boxedValue#> lower#upperKey#Entry(final #primitiveKey# key) {
        #null_key_check#
        final int node = lowerNode(key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    /**
     * Returns the least key-value entry strictly greater than the given key.
     *
     * @param key the key
     * @return the higher entry, or null if no such entry exists
     */
    public Entry<#boxedKey#, #boxedValue#> higher#upperKey#Entry(final #primitiveKey# key) {
        #null_key_check#
        final int node = higherNode(key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    /**
     * Applies a consumer to all key-value pairs in ascending key order.
     *
     * @param action the consumer to apply
     */
    public void forEach#upperKey##upperValue#(final #upperKey##upperValue#Consumer action) {
        Objects.requireNonNull(action);
        forEachInOrder(root, action);
    }

    private void forEachInOrder(final int node, final #upperKey##upperValue#Consumer action) {
        if (node == NULL_NODE) return;
        forEachInOrder(leftChild[node], action);
        action.accept(keys[node], values[node]);
        forEachInOrder(rightChild[node], action);
    }

    @FunctionalInterface
    public interface #upperKey##upperValue#Consumer {
        void accept(#primitiveKey# key, #primitiveValue# value);
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

    private int compareKeys(final #primitiveKey# k1, final #primitiveKey# k2) {
        #key_compare#
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
    public #boxedValue# put(final #boxedKey# key, final #boxedValue# value) {
        if (key == null || value == null) {
            throw new NullPointerException("Null keys and values are not supported");
        }
        final #primitiveValue# oldValue = get((#primitiveKey#) key);
        put((#primitiveKey#) key, (#primitiveValue#) value);
        return isValueEqual(oldValue, nullValue) ? null : oldValue;
    }

    @Override
    public #boxedValue# get(final Object key) {
        if (!(key instanceof #boxedKey#)) return null;
        final #primitiveValue# v = get((#primitiveKey#) key);
        return isValueEqual(v, nullValue) ? null : v;
    }

    @Override
    public #boxedValue# getOrDefault(final Object key, final #boxedValue# defaultValue) {
        if (!(key instanceof #boxedKey#)) return defaultValue;
        final #primitiveValue# v = get((#primitiveKey#) key);
        return isValueEqual(v, nullValue) ? defaultValue : v;
    }

    @Override
    public boolean containsKey(final Object key) {
        if (!(key instanceof #boxedKey#)) return false;
        return containsKey((#primitiveKey#) key);
    }

    @Override
    public boolean containsValue(final Object value) {
        if (!(value instanceof #boxedValue#)) return false;
        return containsValue((#primitiveValue#) value);
    }

    @Override
    public #boxedValue# remove(final Object key) {
        if (!(key instanceof #boxedKey#)) return null;
        final #primitiveValue# v = remove((#primitiveKey#) key);
        return isValueEqual(v, nullValue) ? null : v;
    }

    @Override
    public boolean remove(final Object key, final Object value) {
        if (!(key instanceof #boxedKey#) || !(value instanceof #boxedValue#)) return false;
        return remove((#primitiveKey#) key, (#primitiveValue#) value);
    }

    @Override
    public #boxedValue# putIfAbsent(final #boxedKey# key, final #boxedValue# value) {
        if (key == null || value == null) {
            throw new NullPointerException("Null keys and values are not supported");
        }
        final #primitiveKey# k = (#primitiveKey#) key;
        final int node = getNode(k);
        if (node != NULL_NODE) {
            return values[node];
        }
        put(k, (#primitiveValue#) value);
        return null;
    }

    @Override
    public boolean replace(final #boxedKey# key, final #boxedValue# oldValue, final #boxedValue# newValue) {
        if (key == null || oldValue == null || newValue == null) {
            throw new NullPointerException("Null keys and values are not supported");
        }
        return replace((#primitiveKey#) key, (#primitiveValue#) oldValue, (#primitiveValue#) newValue);
    }

    @Override
    public #boxedValue# replace(final #boxedKey# key, final #boxedValue# value) {
        if (key == null || value == null) {
            throw new NullPointerException("Null keys and values are not supported");
        }
        final #primitiveValue# oldValue = replace((#primitiveKey#) key, (#primitiveValue#) value);
        return isValueEqual(oldValue, nullValue) ? null : oldValue;
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
    public void putAll(final Map<? extends #boxedKey#, ? extends #boxedValue#> m) {
        for (Map.Entry<? extends #boxedKey#, ? extends #boxedValue#> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public int size() { return size; }

    @Override
    public boolean isEmpty() { return size == 0; }

    @Override
    public Set<#boxedKey#> keySet() {
        return new KeySet(this, false);
    }

    @Override
    public Collection<#boxedValue#> values() {
        return new AbstractCollection<#boxedValue#>() {
            @Override public int size() { return #upperKey#2#upperValue#TreeMap.this.size(); }
            @Override public boolean contains(final Object o) { return containsValue(o); }
            @Override public Iterator<#boxedValue#> iterator() { return new ValueIterator(); }
            @Override public void clear() { #upperKey#2#upperValue#TreeMap.this.clear(); }
        };
    }

    @Override
    public Set<Entry<#boxedKey#, #boxedValue#>> entrySet() {
        return new AbstractSet<Entry<#boxedKey#, #boxedValue#>>() {
            @Override public int size() { return #upperKey#2#upperValue#TreeMap.this.size(); }
            @Override public Iterator<Entry<#boxedKey#, #boxedValue#>> iterator() { return new EntryIterator(); }
            @Override public void clear() { #upperKey#2#upperValue#TreeMap.this.clear(); }
        };
    }

    // ------------------- Iterator Pool -------------------

    public #upperKey#2#upperValue#TreeMapIterator borrowIterator() {
        if (iteratorPool == null) return null;

        long bits = iteratorAvailableBits;
        if (bits == 0) return null;

        final int poolIndex = Long.numberOfTrailingZeros(bits);
        iteratorAvailableBits = bits & ~(1L << poolIndex);

        final #upperKey#2#upperValue#TreeMapIterator iter = ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        iter.reset();
        return iter;
    }

    public void returnIterator(final #upperKey#2#upperValue#TreeMapIterator iterator) {
        if (iteratorPool == null || iterator == null) return;
        final int poolIndex = iterator.poolIndex;
        if (poolIndex < 0 || poolIndex >= ITERATOR_POOL_SIZE) return;
        final #upperKey#2#upperValue#TreeMapIterator iter = ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        if (iter != iterator) return;

        iteratorAvailableBits |= (1L << poolIndex);
    }

    public int availableIteratorCount() {
        return iteratorPool == null ? 0 : Long.bitCount(iteratorAvailableBits);
    }

    private final class IteratorPoolEntry {
        final #upperKey#2#upperValue#TreeMapIterator iterator;

        IteratorPoolEntry(final int poolIndex) {
            this.iterator = new #upperKey#2#upperValue#TreeMapIterator(poolIndex);
        }
    }

    public final class #upperKey#2#upperValue#TreeMapIterator implements AutoCloseable {
        private long p0, p1, p2, p3, p4, p5, p6; // Cache line padding

        private final int poolIndex;
        private int next;
        private int lastReturned;
        private int expectedModCount;

        private long p8, p9, p10, p11, p12; // Cache line padding

        private #upperKey#2#upperValue#TreeMapIterator(final int poolIndex) {
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

        public #primitiveKey# peekNextKey() {
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

        public void forEachRemaining(final #upperKey##upperValue#Consumer action) {
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
            #upperKey#2#upperValue#TreeMap.this.returnIterator(this);
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

    private final class KeyIterator extends BaseIterator<#boxedKey#> {
        KeyIterator() { super(root != NULL_NODE ? minimum(root) : NULL_NODE); }
        KeyIterator(int startNode) { super(startNode); }
        @Override public #boxedKey# next() { return keys[nextAscending()]; }
    }

    private final class ValueIterator extends BaseIterator<#boxedValue#> {
        ValueIterator() { super(root != NULL_NODE ? minimum(root) : NULL_NODE); }
        ValueIterator(int startNode) { super(startNode); }
        @Override public #boxedValue# next() { return values[nextAscending()]; }
    }

    private final class EntryIterator extends BaseIterator<Entry<#boxedKey#, #boxedValue#>> {
        EntryIterator() { super(root != NULL_NODE ? minimum(root) : NULL_NODE); }
        EntryIterator(int startNode) { super(startNode); }
        @Override public Entry<#boxedKey#, #boxedValue#> next() {
            final int node = nextAscending();
            return new SimpleEntry<>(keys[node], values[node]);
        }
    }

    private final class DescendingKeyIterator extends BaseIterator<#boxedKey#> {
        DescendingKeyIterator(int startNode) { super(startNode); }
        @Override public #boxedKey# next() { return keys[nextDescending()]; }
    }

    private final class DescendingEntryIterator extends BaseIterator<Entry<#boxedKey#, #boxedValue#>> {
        DescendingEntryIterator(int startNode) { super(startNode); }
        @Override public Entry<#boxedKey#, #boxedValue#> next() {
            final int node = nextDescending();
            return new SimpleEntry<>(keys[node], values[node]);
        }
    }

    // ------------------- SimpleEntry -------------------

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

    // ------------------- NavigableMap Implementation -------------------

    @Override
    public Comparator<? super #boxedKey#> comparator() { return null; }

    @Override
    public #boxedKey# firstKey() {
        return first#upperKey#Key();
    }

    @Override
    public #boxedKey# lastKey() {
        return last#upperKey#Key();
    }

    @Override
    public Entry<#boxedKey#, #boxedValue#> lowerEntry(final #boxedKey# key) {
        if (key == null) throw new NullPointerException();
        final int node = lowerNode((#primitiveKey#) key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    @Override
    public #boxedKey# lowerKey(#boxedKey# key) {
        return lowerKey((#primitiveKey#) key);
    }

    @Override
    public Entry<#boxedKey#, #boxedValue#> floorEntry(final #boxedKey# key) {
        if (key == null) throw new NullPointerException();
        final int node = floorNode((#primitiveKey#) key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    @Override
    public #boxedKey# floorKey(#boxedKey# key) {
        return floorKey((#primitiveKey#) key);
    }

    @Override
    public Entry<#boxedKey#, #boxedValue#> ceilingEntry(final #boxedKey# key) {
        if (key == null) throw new NullPointerException();
        final int node = ceilingNode((#primitiveKey#) key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    @Override
    public #boxedKey# ceilingKey(#boxedKey# key) {
        return ceilingKey((#primitiveKey#) key);
    }

    @Override
    public Entry<#boxedKey#, #boxedValue#> higherEntry(final #boxedKey# key) {
        if (key == null) throw new NullPointerException();
        final int node = higherNode((#primitiveKey#) key);
        return node != NULL_NODE ? new SimpleEntry<>(keys[node], values[node]) : null;
    }

    @Override
    public #boxedKey# higherKey(#boxedKey# key) {
        return higherKey((#primitiveKey#) key);
    }

    @Override
    public Entry<#boxedKey#, #boxedValue#> firstEntry() {
        if (root == NULL_NODE) return null;
        final int node = minimum(root);
        return new SimpleEntry<>(keys[node], values[node]);
    }

    @Override
    public Entry<#boxedKey#, #boxedValue#> lastEntry() {
        if (root == NULL_NODE) return null;
        final int node = maximum(root);
        return new SimpleEntry<>(keys[node], values[node]);
    }

    @Override
    public Entry<#boxedKey#, #boxedValue#> pollFirstEntry() {
        if (root == NULL_NODE) return null;
        final int node = minimum(root);
        final Entry<#boxedKey#, #boxedValue#> entry = new SimpleEntry<>(keys[node], values[node]);
        deleteNode(node);
        return entry;
    }

    @Override
    public Entry<#boxedKey#, #boxedValue#> pollLastEntry() {
        if (root == NULL_NODE) return null;
        final int node = maximum(root);
        final Entry<#boxedKey#, #boxedValue#> entry = new SimpleEntry<>(keys[node], values[node]);
        deleteNode(node);
        return entry;
    }

    @Override
    public NavigableMap<#boxedKey#, #boxedValue#> descendingMap() {
        return new DescendingMap(this);
    }

    @Override
    public NavigableSet<#boxedKey#> navigableKeySet() {
        return new KeySet(this, false);
    }

    @Override
    public NavigableSet<#boxedKey#> descendingKeySet() {
        return new KeySet(this, true);
    }

    @Override
    public NavigableMap<#boxedKey#, #boxedValue#> subMap(#boxedKey# fromKey, boolean fromInclusive, #boxedKey# toKey, boolean toInclusive) {
        return new SubMap(this, fromKey, fromInclusive, toKey, toInclusive);
    }

    @Override
    public NavigableMap<#boxedKey#, #boxedValue#> headMap(#boxedKey# toKey, boolean inclusive) {
        return new SubMap(this, null, true, toKey, inclusive);
    }

    @Override
    public NavigableMap<#boxedKey#, #boxedValue#> tailMap(#boxedKey# fromKey, boolean inclusive) {
        return new SubMap(this, fromKey, inclusive, null, true);
    }

    @Override
    public SortedMap<#boxedKey#, #boxedValue#> subMap(#boxedKey# fromKey, #boxedKey# toKey) {
        return new SubMap(this, fromKey, true, toKey, false);
    }

    @Override
    public SortedMap<#boxedKey#, #boxedValue#> headMap(#boxedKey# toKey) {
        return new SubMap(this, null, true, toKey, false);
    }

    @Override
    public SortedMap<#boxedKey#, #boxedValue#> tailMap(#boxedKey# fromKey) {
        return new SubMap(this, fromKey, true, null, true);
    }

    // ------------------- SubMap Implementation -------------------

    private final class SubMap extends AbstractMap<#boxedKey#, #boxedValue#> implements NavigableMap<#boxedKey#, #boxedValue#> {
        private final #upperKey#2#upperValue#TreeMap map;
        private final #boxedKey# fromKey;
        private final boolean fromInclusive;
        private final #boxedKey# toKey;
        private final boolean toInclusive;
        private final boolean hasLowerBound;
        private final boolean hasUpperBound;

        SubMap(final #upperKey#2#upperValue#TreeMap map,
               final #boxedKey# fromKey, final boolean fromInclusive,
               final #boxedKey# toKey, final boolean toInclusive) {
            this.map = map;
            this.fromKey = fromKey;
            this.fromInclusive = fromInclusive;
            this.toKey = toKey;
            this.toInclusive = toInclusive;
            this.hasLowerBound = fromKey != null;
            this.hasUpperBound = toKey != null;

            if (hasLowerBound && hasUpperBound && map.compareKeys((#primitiveKey#)fromKey, (#primitiveKey#)toKey) > 0) {
                throw new IllegalArgumentException("fromKey > toKey");
            }
        }

        private boolean inRange(final #primitiveKey# key) {
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

        private boolean inLowerRange(final #primitiveKey# key, final boolean inclusive) {
            if (!hasLowerBound) return true;
            final int cmp = map.compareKeys(key, (#primitiveKey#) fromKey);
            return cmp > 0 || (inclusive && cmp == 0);
        }

        private boolean inUpperRange(final #primitiveKey# key, final boolean inclusive) {
            if (!hasUpperBound) return true;
            final int cmp = map.compareKeys(key, (#primitiveKey#) toKey);
            return cmp < 0 || (inclusive && cmp == 0);
        }

        private int lowestNode() {
            int node;
            if (hasLowerBound) {
                node = fromInclusive ? map.ceilingNode((#primitiveKey#) fromKey) : map.higherNode((#primitiveKey#) fromKey);
            } else {
                node = map.root != NULL_NODE ? map.minimum(map.root) : NULL_NODE;
            }
            return (node != NULL_NODE && inRange(map.keys[node])) ? node : NULL_NODE;
        }

        private int highestNode() {
            int node;
            if (hasUpperBound) {
                node = toInclusive ? map.floorNode((#primitiveKey#) toKey) : map.lowerNode((#primitiveKey#) toKey);
            } else {
                node = map.root != NULL_NODE ? map.maximum(map.root) : NULL_NODE;
            }
            return (node != NULL_NODE && inRange(map.keys[node])) ? node : NULL_NODE;
        }

        @Override
        public int size() {
            int count = 0;
            for (Iterator<Entry<#boxedKey#, #boxedValue#>> it = entrySet().iterator(); it.hasNext(); it.next()) {
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
            if (!(key instanceof #boxedKey#)) return false;
            return inRange((#primitiveKey#) key) && map.containsKey(key);
        }

        @Override
        public #boxedValue# get(final Object key) {
            if (!(key instanceof #boxedKey#)) return null;
            if (!inRange((#primitiveKey#) key)) return null;
            return map.get(key);
        }

        @Override
        public #boxedValue# put(final #boxedKey# key, final #boxedValue# value) {
            if (key == null || value == null) throw new NullPointerException();
            if (!inRange((#primitiveKey#) key)) {
                throw new IllegalArgumentException("Key out of range");
            }
            return map.put(key, value);
        }

        @Override
        public #boxedValue# remove(final Object key) {
            if (!(key instanceof #boxedKey#)) return null;
            if (!inRange((#primitiveKey#) key)) return null;
            return map.remove(key);
        }

        @Override
        public void clear() {
            for (Iterator<Entry<#boxedKey#, #boxedValue#>> it = entrySet().iterator(); it.hasNext(); ) {
                it.next();
                it.remove();
            }
        }

        @Override
        public Entry<#boxedKey#, #boxedValue#> firstEntry() {
            final int node = lowestNode();
            return node != NULL_NODE ? new SimpleEntry<>(map.keys[node], map.values[node]) : null;
        }

        @Override
        public #boxedKey# firstKey() {
            final int node = lowestNode();
            if (node == NULL_NODE) throw new NoSuchElementException();
            return map.keys[node];
        }

        @Override
        public Entry<#boxedKey#, #boxedValue#> lastEntry() {
            final int node = highestNode();
            return node != NULL_NODE ? new SimpleEntry<>(map.keys[node], map.values[node]) : null;
        }

        @Override
        public #boxedKey# lastKey() {
            final int node = highestNode();
            if (node == NULL_NODE) throw new NoSuchElementException();
            return map.keys[node];
        }

        @Override
        public Entry<#boxedKey#, #boxedValue#> pollFirstEntry() {
            final int node = lowestNode();
            if (node == NULL_NODE) return null;
            final Entry<#boxedKey#, #boxedValue#> entry = new SimpleEntry<>(map.keys[node], map.values[node]);
            map.deleteNode(node);
            return entry;
        }

        @Override
        public Entry<#boxedKey#, #boxedValue#> pollLastEntry() {
            final int node = highestNode();
            if (node == NULL_NODE) return null;
            final Entry<#boxedKey#, #boxedValue#> entry = new SimpleEntry<>(map.keys[node], map.values[node]);
            map.deleteNode(node);
            return entry;
        }

        @Override
        public Entry<#boxedKey#, #boxedValue#> lowerEntry(#boxedKey# key) {
            final int node = lowerNode(key);
            return node != NULL_NODE ? new SimpleEntry<>(map.keys[node], map.values[node]) : null;
        }

        @Override
        public #boxedKey# lowerKey(#boxedKey# key) {
            final int node = lowerNode(key);
            return node != NULL_NODE ? map.keys[node] : null;
        }

        private int lowerNode(#boxedKey# key) {
            if (key == null) throw new NullPointerException();
            #primitiveKey# k = (#primitiveKey#) key;
            int node;
            if (hasUpperBound && !inUpperRange(k, false)) {
                node = highestNode();
            } else {
                node = map.lowerNode(k);
            }
            return (node != NULL_NODE && inRange(map.keys[node])) ? node : NULL_NODE;
        }

        @Override
        public Entry<#boxedKey#, #boxedValue#> floorEntry(#boxedKey# key) {
            final int node = floorNode(key);
            return node != NULL_NODE ? new SimpleEntry<>(map.keys[node], map.values[node]) : null;
        }

        @Override
        public #boxedKey# floorKey(#boxedKey# key) {
            final int node = floorNode(key);
            return node != NULL_NODE ? map.keys[node] : null;
        }

        private int floorNode(#boxedKey# key) {
            if (key == null) throw new NullPointerException();
            #primitiveKey# k = (#primitiveKey#) key;
            int node;
            if (hasUpperBound && !inUpperRange(k, true)) {
                node = highestNode();
            } else {
                node = map.floorNode(k);
            }
            return (node != NULL_NODE && inRange(map.keys[node])) ? node : NULL_NODE;
        }

        @Override
        public Entry<#boxedKey#, #boxedValue#> ceilingEntry(#boxedKey# key) {
            final int node = ceilingNode(key);
            return node != NULL_NODE ? new SimpleEntry<>(map.keys[node], map.values[node]) : null;
        }

        @Override
        public #boxedKey# ceilingKey(#boxedKey# key) {
            final int node = ceilingNode(key);
            return node != NULL_NODE ? map.keys[node] : null;
        }

        private int ceilingNode(#boxedKey# key) {
            if (key == null) throw new NullPointerException();
            #primitiveKey# k = (#primitiveKey#) key;
            int node;
            if (hasLowerBound && !inLowerRange(k, true)) {
                node = lowestNode();
            } else {
                node = map.ceilingNode(k);
            }
            return (node != NULL_NODE && inRange(map.keys[node])) ? node : NULL_NODE;
        }

        @Override
        public Entry<#boxedKey#, #boxedValue#> higherEntry(#boxedKey# key) {
            final int node = higherNode(key);
            return node != NULL_NODE ? new SimpleEntry<>(map.keys[node], map.values[node]) : null;
        }

        @Override
        public #boxedKey# higherKey(#boxedKey# key) {
            final int node = higherNode(key);
            return node != NULL_NODE ? map.keys[node] : null;
        }

        private int higherNode(#boxedKey# key) {
            if (key == null) throw new NullPointerException();
            #primitiveKey# k = (#primitiveKey#) key;
            int node;
            if (hasLowerBound && !inLowerRange(k, false)) {
                node = lowestNode();
            } else {
                node = map.higherNode(k);
            }
            return (node != NULL_NODE && inRange(map.keys[node])) ? node : NULL_NODE;
        }

        @Override
        public Comparator<? super #boxedKey#> comparator() { return null; }

        @Override
        public NavigableMap<#boxedKey#, #boxedValue#> descendingMap() {
            return new SubMap(map, fromKey, fromInclusive, toKey, toInclusive).new DescendingSubMap();
        }

        @Override
        public NavigableSet<#boxedKey#> navigableKeySet() {
            return new KeySet(this, false);
        }

        @Override
        public NavigableSet<#boxedKey#> descendingKeySet() {
            return new KeySet(this, true);
        }

        @Override
        public NavigableMap<#boxedKey#, #boxedValue#> subMap(#boxedKey# fromKey, boolean fromInclusive, #boxedKey# toKey, boolean toInclusive) {
            if (fromKey == null || toKey == null) throw new NullPointerException();
            #primitiveKey# from = (#primitiveKey#)fromKey;
            #primitiveKey# to = (#primitiveKey#)toKey;
            if (!inRange(from) || !inRange(to)) throw new IllegalArgumentException("Key out of range");
            return new SubMap(map, fromKey, fromInclusive, toKey, toInclusive);
        }

        @Override
        public NavigableMap<#boxedKey#, #boxedValue#> headMap(#boxedKey# toKey, boolean inclusive) {
            if (toKey == null) throw new NullPointerException();
            if (!inRange((#primitiveKey#)toKey)) throw new IllegalArgumentException("Key out of range");
            return new SubMap(map, fromKey, fromInclusive, toKey, inclusive);
        }

        @Override
        public NavigableMap<#boxedKey#, #boxedValue#> tailMap(#boxedKey# fromKey, boolean inclusive) {
            if (fromKey == null) throw new NullPointerException();
            if (!inRange((#primitiveKey#)fromKey)) throw new IllegalArgumentException("Key out of range");
            return new SubMap(map, fromKey, inclusive, toKey, toInclusive);
        }

        @Override public SortedMap<#boxedKey#, #boxedValue#> subMap(#boxedKey# fromKey, #boxedKey# toKey) { return subMap(fromKey, true, toKey, false); }
        @Override public SortedMap<#boxedKey#, #boxedValue#> headMap(#boxedKey# toKey) { return headMap(toKey, false); }
        @Override public SortedMap<#boxedKey#, #boxedValue#> tailMap(#boxedKey# fromKey) { return tailMap(fromKey, true); }

        @Override
        public Set<Entry<#boxedKey#, #boxedValue#>> entrySet() {
            return new SubMapEntrySet();
        }

        private final class SubMapEntrySet extends AbstractSet<Entry<#boxedKey#, #boxedValue#>> {
            @Override
            public int size() {
                return SubMap.this.size();
            }
            @Override
            public Iterator<Entry<#boxedKey#, #boxedValue#>> iterator() {
                return new SubMapEntryIterator(lowestNode());
            }
        }

        private final class SubMapEntryIterator extends BaseIterator<Entry<#boxedKey#, #boxedValue#>> {
            SubMapEntryIterator(int startNode) {
                super(startNode);
            }

            @Override
            public boolean hasNext() {
                return next != NULL_NODE && inRange(map.keys[next]);
            }

            @Override
            public Entry<#boxedKey#, #boxedValue#> next() {
                if (next == NULL_NODE || !inRange(map.keys[next])) {
                    throw new NoSuchElementException();
                }
                final int node = nextAscending();
                return new SimpleEntry<>(map.keys[node], map.values[node]);
            }
        }

        private final class DescendingSubMap extends AbstractMap<#boxedKey#, #boxedValue#> implements NavigableMap<#boxedKey#, #boxedValue#> {
            @Override public int size() { return SubMap.this.size(); }
            @Override public boolean isEmpty() { return SubMap.this.isEmpty(); }
            @Override public boolean containsKey(Object key) { return SubMap.this.containsKey(key); }
            @Override public #boxedValue# get(Object key) { return SubMap.this.get(key); }
            @Override public #boxedValue# put(#boxedKey# key, #boxedValue# value) { return SubMap.this.put(key, value); }
            @Override public #boxedValue# remove(Object key) { return SubMap.this.remove(key); }
            @Override public void clear() { SubMap.this.clear(); }
            @Override public Comparator<? super #boxedKey#> comparator() { return null; }

            @Override public Entry<#boxedKey#, #boxedValue#> firstEntry() { return SubMap.this.lastEntry(); }
            @Override public #boxedKey# firstKey() { return SubMap.this.lastKey(); }
            @Override public Entry<#boxedKey#, #boxedValue#> lastEntry() { return SubMap.this.firstEntry(); }
            @Override public #boxedKey# lastKey() { return SubMap.this.firstKey(); }

            @Override public Entry<#boxedKey#, #boxedValue#> pollFirstEntry() { return SubMap.this.pollLastEntry(); }
            @Override public Entry<#boxedKey#, #boxedValue#> pollLastEntry() { return SubMap.this.pollFirstEntry(); }

            @Override public Entry<#boxedKey#, #boxedValue#> lowerEntry(#boxedKey# key) { return SubMap.this.higherEntry(key); }
            @Override public #boxedKey# lowerKey(#boxedKey# key) { return SubMap.this.higherKey(key); }
            @Override public Entry<#boxedKey#, #boxedValue#> floorEntry(#boxedKey# key) { return SubMap.this.ceilingEntry(key); }
            @Override public #boxedKey# floorKey(#boxedKey# key) { return SubMap.this.ceilingKey(key); }
            @Override public Entry<#boxedKey#, #boxedValue#> ceilingEntry(#boxedKey# key) { return SubMap.this.floorEntry(key); }
            @Override public #boxedKey# ceilingKey(#boxedKey# key) { return SubMap.this.floorKey(key); }
            @Override public Entry<#boxedKey#, #boxedValue#> higherEntry(#boxedKey# key) { return SubMap.this.lowerEntry(key); }
            @Override public #boxedKey# higherKey(#boxedKey# key) { return SubMap.this.lowerKey(key); }

            @Override public NavigableMap<#boxedKey#, #boxedValue#> descendingMap() { return SubMap.this; }
            @Override public NavigableSet<#boxedKey#> navigableKeySet() { return new KeySet(this, false); }
            @Override public NavigableSet<#boxedKey#> descendingKeySet() { return SubMap.this.navigableKeySet(); }

            @Override public NavigableMap<#boxedKey#, #boxedValue#> subMap(#boxedKey# fromKey, boolean fromInclusive, #boxedKey# toKey, boolean toInclusive) { return SubMap.this.subMap(toKey, toInclusive, fromKey, fromInclusive).descendingMap(); }
            @Override public NavigableMap<#boxedKey#, #boxedValue#> headMap(#boxedKey# toKey, boolean inclusive) { return SubMap.this.tailMap(toKey, inclusive).descendingMap(); }
            @Override public NavigableMap<#boxedKey#, #boxedValue#> tailMap(#boxedKey# fromKey, boolean inclusive) { return SubMap.this.headMap(fromKey, inclusive).descendingMap(); }
            @Override public SortedMap<#boxedKey#, #boxedValue#> subMap(#boxedKey# fromKey, #boxedKey# toKey) { return subMap(fromKey, true, toKey, false); }
            @Override public SortedMap<#boxedKey#, #boxedValue#> headMap(#boxedKey# toKey) { return headMap(toKey, false); }
            @Override public SortedMap<#boxedKey#, #boxedValue#> tailMap(#boxedKey# fromKey) { return tailMap(fromKey, true); }

            @Override public Set<Entry<#boxedKey#, #boxedValue#>> entrySet() { return new DescendingSubMapEntrySet(); }
        }

        private final class DescendingSubMapEntrySet extends AbstractSet<Entry<#boxedKey#, #boxedValue#>> {
            @Override
            public int size() {
                return SubMap.this.size();
            }
            @Override
            public Iterator<Entry<#boxedKey#, #boxedValue#>> iterator() {
                return new DescendingSubMapEntryIterator(highestNode());
            }
        }

        private final class DescendingSubMapEntryIterator extends BaseIterator<Entry<#boxedKey#, #boxedValue#>> {
            DescendingSubMapEntryIterator(int startNode) {
                super(startNode);
            }

            @Override
            public boolean hasNext() {
                return next != NULL_NODE && inRange(map.keys[next]);
            }

            @Override
            public Entry<#boxedKey#, #boxedValue#> next() {
                if (next == NULL_NODE || !inRange(map.keys[next])) {
                    throw new NoSuchElementException();
                }
                final int node = nextDescending();
                return new SimpleEntry<>(map.keys[node], map.values[node]);
            }
        }
    }

    // ------------------- DescendingMap Implementation -------------------

    private final class DescendingMap extends AbstractMap<#boxedKey#, #boxedValue#> implements NavigableMap<#boxedKey#, #boxedValue#> {
        private final #upperKey#2#upperValue#TreeMap map;

        DescendingMap(final #upperKey#2#upperValue#TreeMap map) { this.map = map; }

        @Override public int size() { return map.size(); }
        @Override public boolean isEmpty() { return map.isEmpty(); }
        @Override public boolean containsKey(Object key) { return map.containsKey(key); }
        @Override public #boxedValue# get(Object key) { return map.get(key); }
        @Override public #boxedValue# put(#boxedKey# key, #boxedValue# value) { return map.put(key, value); }
        @Override public #boxedValue# remove(Object key) { return map.remove(key); }
        @Override public void clear() { map.clear(); }
        @Override public Comparator<? super #boxedKey#> comparator() { return null; }

        @Override public Entry<#boxedKey#, #boxedValue#> firstEntry() { return map.lastEntry(); }
        @Override public #boxedKey# firstKey() { return map.lastKey(); }
        @Override public Entry<#boxedKey#, #boxedValue#> lastEntry() { return map.firstEntry(); }
        @Override public #boxedKey# lastKey() { return map.firstKey(); }

        @Override public Entry<#boxedKey#, #boxedValue#> pollFirstEntry() { return map.pollLastEntry(); }
        @Override public Entry<#boxedKey#, #boxedValue#> pollLastEntry() { return map.pollFirstEntry(); }

        @Override public Entry<#boxedKey#, #boxedValue#> lowerEntry(#boxedKey# key) { return map.higherEntry(key); }
        @Override public #boxedKey# lowerKey(#boxedKey# key) { return map.higherKey(key); }
        @Override public Entry<#boxedKey#, #boxedValue#> floorEntry(#boxedKey# key) { return map.ceilingEntry(key); }
        @Override public #boxedKey# floorKey(#boxedKey# key) { return map.ceilingKey(key); }
        @Override public Entry<#boxedKey#, #boxedValue#> ceilingEntry(#boxedKey# key) { return map.floorEntry(key); }
        @Override public #boxedKey# ceilingKey(#boxedKey# key) { return map.floorKey(key); }
        @Override public Entry<#boxedKey#, #boxedValue#> higherEntry(#boxedKey# key) { return map.lowerEntry(key); }
        @Override public #boxedKey# higherKey(#boxedKey# key) { return map.lowerKey(key); }

        @Override public NavigableMap<#boxedKey#, #boxedValue#> descendingMap() { return map; }
        @Override public NavigableSet<#boxedKey#> navigableKeySet() { return new KeySet(this, false); }
        @Override public NavigableSet<#boxedKey#> descendingKeySet() { return map.navigableKeySet(); }

        @Override public NavigableMap<#boxedKey#, #boxedValue#> subMap(#boxedKey# fromKey, boolean fromInclusive, #boxedKey# toKey, boolean toInclusive) { return map.subMap(toKey, toInclusive, fromKey, fromInclusive).descendingMap(); }
        @Override public NavigableMap<#boxedKey#, #boxedValue#> headMap(#boxedKey# toKey, boolean inclusive) { return map.tailMap(toKey, inclusive).descendingMap(); }
        @Override public NavigableMap<#boxedKey#, #boxedValue#> tailMap(#boxedKey# fromKey, boolean inclusive) { return map.headMap(fromKey, inclusive).descendingMap(); }
        @Override public SortedMap<#boxedKey#, #boxedValue#> subMap(#boxedKey# fromKey, #boxedKey# toKey) { return subMap(fromKey, true, toKey, false); }
        @Override public SortedMap<#boxedKey#, #boxedValue#> headMap(#boxedKey# toKey) { return headMap(toKey, false); }
        @Override public SortedMap<#boxedKey#, #boxedValue#> tailMap(#boxedKey# fromKey) { return tailMap(fromKey, true); }

        @Override
        public Set<Entry<#boxedKey#, #boxedValue#>> entrySet() {
            return new AbstractSet<Entry<#boxedKey#, #boxedValue#>>() {
                @Override public int size() { return map.size(); }
                @Override public void clear() { map.clear(); }
                @Override public Iterator<Entry<#boxedKey#, #boxedValue#>> iterator() {
                    return new DescendingEntryIterator(map.root == NULL_NODE ? NULL_NODE : map.maximum(map.root));
                }
            };
        }
    }

    // ------------------- KeySet Implementation -------------------

    private static final class KeySet extends AbstractSet<#boxedKey#> implements NavigableSet<#boxedKey#> {
        private final NavigableMap<#boxedKey#, #boxedValue#> map;
        private final boolean descending;

        KeySet(NavigableMap<#boxedKey#, #boxedValue#> map, boolean descending) {
            this.map = map;
            this.descending = descending;
        }

        @Override
        public int size() { return map.size(); }
        @Override
        public boolean isEmpty() { return map.isEmpty(); }
        @Override
        public boolean contains(Object o) { return map.containsKey((#boxedKey#) o); }
        @Override
        public void clear() { map.clear(); }

        @Override
        public #boxedKey# lower(#boxedKey# k) { return descending ? map.higherKey(k) : map.lowerKey(k); }
        @Override
        public #boxedKey# floor(#boxedKey# k) { return descending ? map.ceilingKey(k) : map.floorKey(k); }
        @Override
        public #boxedKey# ceiling(#boxedKey# k) { return descending ? map.floorKey(k) : map.ceilingKey(k); }
        @Override
        public #boxedKey# higher(#boxedKey# k) { return descending ? map.lowerKey(k) : map.higherKey(k); }

        @Override
        public #boxedKey# first() { return descending ? map.lastKey() : map.firstKey(); }
        @Override
        public #boxedKey# last() { return descending ? map.firstKey() : map.lastKey(); }

        @Override
        public #boxedKey# pollFirst() {
            Entry<#boxedKey#, #boxedValue#> e = descending ? map.pollLastEntry() : map.pollFirstEntry();
            return e != null ? e.getKey() : null;
        }

        @Override
        public #boxedKey# pollLast() {
            Entry<#boxedKey#, #boxedValue#> e = descending ? map.pollFirstEntry() : map.pollLastEntry();
            return e != null ? e.getKey() : null;
        }

        @Override
        public Comparator<? super #boxedKey#> comparator() { return map.comparator(); }

        @Override
        public Iterator<#boxedKey#> iterator() {
            if (map instanceof #upperKey#2#upperValue#TreeMap m) {
                int startNode = m.root == NULL_NODE ? NULL_NODE : (descending ? m.maximum(m.root) : m.minimum(m.root));
                return descending ? m.new DescendingKeyIterator(startNode) : m.new KeyIterator(startNode);
            }
            if (map instanceof SubMap m) {
                int startNode = descending ? m.highestNode() : m.lowestNode();
                return new Iterator<#boxedKey#>() {
                    final Iterator<Entry<#boxedKey#, #boxedValue#>> entryIt = m.entrySet().iterator();
                    @Override public boolean hasNext() { return entryIt.hasNext(); }
                    @Override public #boxedKey# next() { return entryIt.next().getKey(); }
                    @Override public void remove() { entryIt.remove(); }
                };
            }
            return (descending ? map.descendingMap() : map).keySet().iterator();
        }

        @Override
        public Iterator<#boxedKey#> descendingIterator() {
            return new KeySet(map, !descending).iterator();
        }

        @Override
        public NavigableSet<#boxedKey#> descendingSet() {
            return new KeySet(map, !descending);
        }

        @Override
        public NavigableSet<#boxedKey#> subSet(#boxedKey# from, boolean fromInc, #boxedKey# to, boolean toInc) {
            return new KeySet(map.subMap(from, fromInc, to, toInc), descending);
        }
        @Override
        public NavigableSet<#boxedKey#> headSet(#boxedKey# to, boolean toInc) {
            return new KeySet(map.headMap(to, toInc), descending);
        }
        @Override
        public NavigableSet<#boxedKey#> tailSet(#boxedKey# from, boolean fromInc) {
            return new KeySet(map.tailMap(from, fromInc), descending);
        }
        @Override public SortedSet<#boxedKey#> subSet(#boxedKey# from, #boxedKey# to) { return subSet(from, true, to, false); }
        @Override public SortedSet<#boxedKey#> headSet(#boxedKey# to) { return headSet(to, false); }
        @Override public SortedSet<#boxedKey#> tailSet(#boxedKey# from) { return tailSet(from, true); }
    }

    // ------------------- Builder -------------------

    public static final class Builder {
        private int initialCapacity = DEFAULT_INITIAL_CAPACITY;
        private #primitiveValue# nullValue = DEFAULT_NULL_VALUE;
        private boolean enableIteratorPool = true;

        public Builder initialCapacity(final int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        public Builder nullValue(final #primitiveValue# nullValue) {
            this.nullValue = nullValue;
            return this;
        }

        public Builder disableIteratorPool() {
            this.enableIteratorPool = false;
            return this;
        }

        public #upperKey#2#upperValue#TreeMap build() {
            return new #upperKey#2#upperValue#TreeMap(initialCapacity, nullValue, enableIteratorPool);
        }
    }
}
