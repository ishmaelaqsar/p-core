package dev.aqsar.pcore.collections;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * A Map implementation that stores #primitiveKey#-to-#primitiveValue# mappings without boxing to achieve high performance and avoid allocations.
 * Uses open addressing with linear probing for collision resolution.
 * <p>
 * For floating point keys (float/double):
 * - Uses bitwise equality (Double.doubleToLongBits for doubles, Float.floatToIntBits for floats)
 * - NaN values are supported and treated as equal to each other
 * - +0.0 and -0.0 are treated as the same key
 * <p>
 * This class is not thread-safe.
 */
public final class #upperKey#2#upperValue#HashMap implements Map<#boxedKey#, #boxedValue#> {

    public static final #primitiveValue# DEFAULT_NULL_VALUE = #nullValue#;
    public static final int DEFAULT_INITIAL_CAPACITY = 16;
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private static final int MAX_CAPACITY = 1 << 30;
    private static final int ITERATOR_POOL_SIZE = 8;

    // Sentinel values for internal state tracking
    // For floating point types, these use specific bit patterns that won't occur naturally
    #sentinel_declarations#

    // Hot fields - accessed on every operation (grouped for cache locality)
    private #primitiveKey#[] keys;
    private #primitiveValue#[] values;
    #state_array_declaration# // For float/double: byte[] to track empty/tombstone state
    private int size = 0;
    private int mask;
    private int threshold;

    // Warm fields - accessed frequently but not every operation
    public final #primitiveValue# nullValue;

    // Cold fields - accessed rarely
    private final float loadFactor;
    private int modCount = 0;
    private final IteratorPoolEntry[] iteratorPool;
    private long iteratorAvailableBits;

    private #upperKey#2#upperValue#HashMap(
            final int initialCapacity,
            final float loadFactor,
            final #primitiveValue# nullValue,
            final boolean enableIteratorPool
    ) {
        int capacity = 1;
        while (capacity < initialCapacity) capacity <<= 1;

        this.keys = new #primitiveKey#[capacity];
        this.values = new #primitiveValue#[capacity];
        #state_array_init#; // Initialize state array for float/double

        this.mask = capacity - 1;
        this.loadFactor = loadFactor;
        this.threshold = (int) (capacity * loadFactor);

        this.nullValue = nullValue;

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
     * Puts a primitive value for a primitive key into the map.
     *
     * @param key the primitive key
     * @param value the primitive value
     * @throws IllegalArgumentException if the key is null (unsupported)
     */
    public void put(final #primitiveKey# key, final #primitiveValue# value) {
        if (isNullKey(key)) throw new IllegalArgumentException("Null keys are not permitted");
        if (size >= threshold) resize();

        final int slot = findSlotForPut(key);
        final boolean isNewKey = isSlotEmptyOrTombstone(slot);

        keys[slot] = key;
        values[slot] = value;
        markSlotOccupied(slot);

        if (isNewKey) {
            size++;
            modCount++;
        }
    }

    /**
     * Retrieves the primitive value for a primitive key.
     *
     * @param key the key to lookup
     * @return the associated value, or {@code nullValue} if the key is not present
     * @throws IllegalArgumentException if the key is null
     */
    public #primitiveValue# get(final #primitiveKey# key) {
        if (isNullKey(key)) throw new IllegalArgumentException("Null keys are not permitted");
        final int slot = findSlotForGet(key);
        return slot >= 0 ? values[slot] : nullValue;
    }

    /**
     * Retrieves the primitive value for a key, or returns the given default if not found.
     *
     * @param key the key to lookup
     * @param defaultValue the default value if key is absent
     * @return the value associated with the key, or {@code defaultValue}
     * @throws IllegalArgumentException if the key is null
     */
    public #primitiveValue# getOrDefault(final #primitiveKey# key, final #primitiveValue# defaultValue) {
        if (isNullKey(key)) throw new IllegalArgumentException("Null keys are not permitted");
        final int slot = findSlotForGet(key);
        return slot >= 0 ? values[slot] : defaultValue;
    }

    /**
     * Checks if a primitive key exists in the map.
     *
     * @param key the key to check
     * @return {@code true} if the key is present, {@code false} otherwise
     * @throws IllegalArgumentException if the key is null
     */
    public boolean containsKey(final #primitiveKey# key) {
        if (isNullKey(key)) throw new IllegalArgumentException("Null keys are not permitted");
        return findSlotForGet(key) >= 0;
    }

    /**
     * Checks if a primitive value exists in the map.
     *
     * @param value the value to check
     * @return {@code true} if the value is present, {@code false} otherwise
     */
    public boolean containsValue(final #primitiveValue# value) {
        final #primitiveKey#[] ks = keys;
        final #primitiveValue#[] vs = values;
        for (int i = 0; i < ks.length; i++) {
            if (isSlotOccupied(i) && isValueEqual(vs[i], value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a key-value mapping by primitive key.
     *
     * @param key the key to remove
     * @return the previous value associated with the key, or {@code nullValue} if not present
     */
    public #primitiveValue# remove(final #primitiveKey# key) {
        final int slot = findSlotForGet(key);
        if (slot >= 0) {
            final #primitiveValue# oldValue = values[slot];
            markSlotTombstone(slot);
            values[slot] = nullValue;
            size--;
            modCount++;
            return oldValue;
        }
        return nullValue;
    }

    /**
     * Applies a consumer to all occupied key-value pairs.
     *
     * @param action the consumer to apply
     * @throws NullPointerException if the action is null
     */
    public void forEach#upperKey##upperValue#(final #upperKey##upperValue#Consumer action) {
        Objects.requireNonNull(action);

        final #primitiveKey#[] ks = keys;
        final #primitiveValue#[] vs = values;
        for (int i = 0; i < ks.length; i++) {
            if (isSlotOccupied(i)) {
                action.accept(ks[i], vs[i]);
            }
        }
    }

    /**
     * Replaces the value for a key if it matches the given old value.
     *
     * @param key the key to update
     * @param oldValue the expected old value
     * @param newValue the new value to set
     * @return {@code true} if replaced, {@code false} if the key is missing or old value mismatch
     */
    public boolean replace(final #primitiveKey# key, final #primitiveValue# oldValue, final #primitiveValue# newValue) {
        final int slot = findSlotForGet(key);
        if (slot >= 0 && isValueEqual(values[slot], oldValue)) {
            values[slot] = newValue;
            modCount++;
            return true;
        }
        return false;
    }

    /**
     * Replaces the value for a key unconditionally.
     *
     * @param key the key to update
     * @param newValue the new value to set
     * @return the previous value, or {@code nullValue} if the key did not exist
     */
    public #primitiveValue# replace(final #primitiveKey# key, final #primitiveValue# newValue) {
        final int slot = findSlotForGet(key);
        if (slot >= 0) {
            final #primitiveValue# oldValue = values[slot];
            values[slot] = newValue;
            modCount++;
            return oldValue;
        }
        return nullValue;
    }

    /**
     * Computes a new value for the key using a remapping function.
     *
     * @param key the key
     * @param remappingFunction the function to compute new value
     * @return the new value or {@code nullValue} if removed
     */
    public #primitiveValue# compute(
            final #primitiveKey# key,
            final #upperKey##upperValue#RemappingFunction remappingFunction
    ) {
        Objects.requireNonNull(remappingFunction);

        final int slot = findSlotForGet(key);
        final boolean present = slot >= 0;
        final #primitiveValue# oldValue = present ? values[slot] : nullValue;
        final #primitiveValue# newValue = remappingFunction.apply(key, present ? oldValue : nullValue);

        if (isValueEqual(newValue, nullValue)) {
            if (present) {
                markSlotTombstone(slot);
                values[slot] = nullValue;
                size--;
                modCount++;
            }
            return nullValue;
        } else {
            if (size >= threshold) resize();
            final int putSlot = present ? slot : findSlotForPut(key);
            if (!present) size++;
            keys[putSlot] = key;
            values[putSlot] = newValue;
            markSlotOccupied(putSlot);
            modCount++;
            return newValue;
        }
    }

    /**
     * Computes and inserts a value if the key is absent.
     *
     * @param key the key
     * @param mappingFunction function to produce a value if key is missing
     * @return the current or computed value, or {@code nullValue} if mapping returned null
     */
    public #primitiveValue# computeIfAbsent(
            final #primitiveKey# key,
            final #upperKey#Function mappingFunction
    ) {
        Objects.requireNonNull(mappingFunction);

        final int slot = findSlotForGet(key);
        if (slot >= 0) return values[slot];

        final #primitiveValue# newValue = mappingFunction.apply(key);
        if (isValueEqual(newValue, nullValue)) return nullValue;
        if (size >= threshold) resize();

        final int putSlot = findSlotForPut(key);
        keys[putSlot] = key;
        values[putSlot] = newValue;
        markSlotOccupied(putSlot);
        size++;
        modCount++;
        return newValue;
    }

    /**
     * Computes a new value only if the key is present.
     *
     * @param key the key
     * @param remappingFunction function to compute new value based on old value
     * @return the new value, or {@code nullValue} if removed
     */
    public #primitiveValue# computeIfPresent(
            final #primitiveKey# key,
            final #upperKey##upperValue#RemappingFunction remappingFunction
    ) {
        Objects.requireNonNull(remappingFunction);

        final int slot = findSlotForGet(key);
        if (slot < 0) return nullValue;

        final #primitiveValue# oldValue = values[slot];
        final #primitiveValue# newValue = remappingFunction.apply(key, oldValue);

        if (isValueEqual(newValue, nullValue)) {
            markSlotTombstone(slot);
            values[slot] = nullValue;
            size--;
            modCount++;
            return nullValue;
        } else {
            values[slot] = newValue;
            modCount++;
            return newValue;
        }
    }

    /**
     * Merges a value with the existing one using a merge function.
     *
     * @param key the key
     * @param value the value to merge
     * @param remappingFunction function to combine old and new values
     * @return the resulting value, or {@code nullValue} if removed
     */
    public #primitiveValue# merge(
            final #primitiveKey# key,
            final #primitiveValue# value,
            final #upperValue##upperValue#MergeFunction remappingFunction
    ) {
        Objects.requireNonNull(remappingFunction);

        final int slot = findSlotForGet(key);
        if (slot < 0) {
            if (isValueEqual(value, nullValue)) return nullValue;
            put(key, value);
            return value;
        } else {
            final #primitiveValue# oldValue = values[slot];
            final #primitiveValue# newValue = remappingFunction.apply(oldValue, value);
            if (isValueEqual(newValue, nullValue)) {
                markSlotTombstone(slot);
                values[slot] = nullValue;
                size--;
                modCount++;
                return nullValue;
            } else {
                values[slot] = newValue;
                modCount++;
                return newValue;
            }
        }
    }

    @FunctionalInterface
    public interface #upperKey##upperValue#Consumer {
        void accept(#primitiveKey# key, #primitiveValue# value);
    }

    @FunctionalInterface
    public interface #upperKey##upperValue#RemappingFunction {
        #primitiveValue# apply(#primitiveKey# key, #primitiveValue# oldValue);
    }

    @FunctionalInterface
    public interface #upperKey#Function {
        #primitiveValue# apply(#primitiveKey# key);
    }

    @FunctionalInterface
    public interface #upperValue##upperValue#MergeFunction {
        #primitiveValue# apply(#primitiveValue# oldValue, #primitiveValue# value);
    }

    // ------------------- Helper Methods for State Management -------------------

    private boolean isNullKey(final #primitiveKey# key) {
        return #key_null#;
    }

    private boolean isKeyEqual(final #primitiveKey# v1, final #primitiveKey# v2) {
        return #key_equals#;
    }

    private boolean isValueEqual(final #primitiveValue# v1, final #primitiveValue# v2) {
        return #value_equals#;
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

    // ------------------- Map Interface -------------------

    /**
     * Associates a boxed key with a boxed value.
     *
     * @param key the boxed key
     * @param value the boxed value
     * @return previous boxed value, or {@code null} if none
     */
    @Override
    public #boxedValue# put(final #boxedKey# key, final #boxedValue# value) {
        if (key == null || value == null) {
            throw new NullPointerException("Null keys and values are not supported");
        }
        final #primitiveValue# oldValue = get((#primitiveKey#) key);
        put((#primitiveKey#) key, (#primitiveValue#) value);
        return isValueEqual(oldValue, nullValue) ? null : oldValue;
    }

    /**
     * Returns the boxed value for a boxed key.
     *
     * @param key the boxed key
     * @return boxed value, or {@code null} if missing
     */
    @Override
    public #boxedValue# get(final Object key) {
        if (!(key instanceof #boxedKey#)) return null;
        final #primitiveValue# v = get((#primitiveKey#) key);
        return isValueEqual(v, nullValue) ? null : v;
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

    /**
     * Removes a mapping by boxed key.
     *
     * @param key the boxed key
     * @return removed boxed value, or {@code null} if absent
     */
    @Override
    public #boxedValue# remove(final Object key) {
        if (!(key instanceof #boxedKey#)) return null;
        final #primitiveValue# v = remove((#primitiveKey#) key);
        return isValueEqual(v, nullValue) ? null : v;
    }

    @Override
    public #boxedValue# replace(final #boxedKey# key, final #boxedValue# value) {
        if (key == null || value == null) return null;
        final #primitiveValue# old = replace((#primitiveKey#) key, (#primitiveValue#) value);
        return isValueEqual(old, nullValue) ? null : old;
    }

    @Override
    public boolean replace(final #boxedKey# key, final #boxedValue# oldValue, final #boxedValue# newValue) {
        if (key == null || oldValue == null || newValue == null) return false;
        return replace((#primitiveKey#) key, (#primitiveValue#) oldValue, (#primitiveValue#) newValue);
    }

    @Override
    public #boxedValue# compute(
            final #boxedKey# key,
            final java.util.function.BiFunction<? super #boxedKey#, ? super #boxedValue#, ? extends #boxedValue#> remappingFunction
    ) {
        if (key == null || remappingFunction == null) return null;
        final #primitiveValue# result = compute((#primitiveKey#) key, (k, v) -> {
            final #boxedValue# boxedOld = isValueEqual(v, nullValue) ? null : v;
            final #boxedValue# boxedNew = remappingFunction.apply(k, boxedOld);
            return boxedNew == null ? nullValue : boxedNew;
        });
        return isValueEqual(result, nullValue) ? null : result;
    }

    @Override
    public #boxedValue# computeIfAbsent(
            final #boxedKey# key,
            final java.util.function.Function<? super #boxedKey#, ? extends #boxedValue#> mappingFunction
    ) {
        if (key == null || mappingFunction == null) return null;
        final #primitiveValue# result = computeIfAbsent((#primitiveKey#) key, k -> {
            final #boxedValue# boxed = mappingFunction.apply(k);
            return boxed == null ? nullValue : boxed;
        });
        return isValueEqual(result, nullValue) ? null : result;
    }

    @Override
    public #boxedValue# computeIfPresent(
            final #boxedKey# key,
            final java.util.function.BiFunction<? super #boxedKey#, ? super #boxedValue#, ? extends #boxedValue#> remappingFunction
    ) {
        if (key == null || remappingFunction == null) return null;
        final #primitiveValue# result = computeIfPresent((#primitiveKey#) key, (k, v) -> {
            final #boxedValue# boxedOld = isValueEqual(v, nullValue) ? null : v;
            final #boxedValue# boxedNew = remappingFunction.apply(k, boxedOld);
            return boxedNew == null ? nullValue : boxedNew;
        });
        return isValueEqual(result, nullValue) ? null : result;
    }

    @Override
    public #boxedValue# merge(
            final #boxedKey# key,
            final #boxedValue# value,
            final java.util.function.BiFunction<? super #boxedValue#, ? super #boxedValue#, ? extends #boxedValue#> remappingFunction
    ) {
        if (key == null || value == null || remappingFunction == null) return null;
        final #primitiveValue# result = merge((#primitiveKey#) key, (#primitiveValue#) value, (v1, v2) -> {
            final #boxedValue# boxed = remappingFunction.apply(v1, v2);
            return boxed == null ? nullValue : boxed;
        });
        return isValueEqual(result, nullValue) ? null : result;
    }

    /**
     * Removes all mappings from the map.
     */
    @Override
    public void clear() {
        if (size > 0) {
            #clear_state#;
            size = 0;
            modCount++;
        }
    }

    @Override
    public void putAll(final Map<? extends #boxedKey#, ? extends #boxedValue#> m) {
        for (Map.Entry<? extends #boxedKey#, ? extends #boxedValue#> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the map size
     */
    @Override
    public int size() { return size; }

    /**
     * Returns whether the map is empty.
     *
     * @return {@code true} if empty, {@code false} otherwise
     */
    @Override
    public boolean isEmpty() { return size == 0; }

    /**
     * Returns a set view of the keys.
     *
     * @return a key set backed by the map
     */
    @Override
    public Set<#boxedKey#> keySet() {
        return new AbstractSet<#boxedKey#>() {
            @Override public int size() { return #upperKey#2#upperValue#HashMap.this.size(); }
            @Override public boolean contains(final Object o) { return containsKey(o); }
            @Override public Iterator<#boxedKey#> iterator() { return new KeyIterator(); }
            @Override public void clear() { #upperKey#2#upperValue#HashMap.this.clear(); }
        };
    }

    /**
     * Returns a collection view of the values.
     *
     * @return values collection backed by the map
     */
    @Override
    public Collection<#boxedValue#> values() {
        return new AbstractCollection<#boxedValue#>() {
            @Override public int size() { return #upperKey#2#upperValue#HashMap.this.size(); }
            @Override public boolean contains(final Object o) { return containsValue(o); }
            @Override public Iterator<#boxedValue#> iterator() { return new ValueIterator(); }
            @Override public void clear() { #upperKey#2#upperValue#HashMap.this.clear(); }
        };
    }

    /**
     * Returns a set of map entries.
     *
     * @return entry set backed by the map
     */
    @Override
    public Set<Entry<#boxedKey#, #boxedValue#>> entrySet() {
        return new AbstractSet<Entry<#boxedKey#, #boxedValue#>>() {
            @Override public int size() { return #upperKey#2#upperValue#HashMap.this.size(); }
            @Override public Iterator<Entry<#boxedKey#, #boxedValue#>> iterator() { return new EntryIterator(); }
            @Override public void clear() { #upperKey#2#upperValue#HashMap.this.clear(); }
        };
    }

    // ------------------- Iterator Pool -------------------

    /**
     * Borrows a pooled iterator over the map.
     * <p>
     * Example usage:
     * <pre>{@code
     * #upperKey##upperValue#HashMapIterator iter = map.borrowIterator();
     * try {
     *     while (iter.hasNext()) {
     *         int key = iter.nextKey();
     *         int value = iter.nextValue();
     *     }
     * } finally {
     *     iter.close();
     * }
     * }</pre>
     *
     * @return a pooled iterator or {@code null} if pool disabled/exhausted
     */
    public #upperKey##upperValue#HashMapIterator borrowIterator() {
        if (iteratorPool == null) return null;

        long bits = iteratorAvailableBits;
        if (bits == 0) return null;

        final int poolIndex = Long.numberOfTrailingZeros(bits);
        iteratorAvailableBits = bits & ~(1L << poolIndex);

        final #upperKey##upperValue#HashMapIterator iter = iteratorPool[poolIndex].iterator;
        iter.reset();
        return iter;
    }

    /**
     * Returns a previously borrowed iterator to the pool.
     *
     * @param iterator the iterator to return
     */
    public void returnIterator(final #upperKey##upperValue#HashMapIterator iterator) {
        if (iteratorPool == null || iterator == null) return;
        final int poolIndex = iterator.poolIndex;
        if (poolIndex < 0 || poolIndex >= ITERATOR_POOL_SIZE) return;
        if (iteratorPool[poolIndex].iterator != iterator) return;

        iteratorAvailableBits |= (1L << poolIndex);
    }

    /**
     * Returns the number of available iterators in the pool.
     *
     * @return number of free iterators
     */
    public int availableIteratorCount() {
        return iteratorPool == null ? 0 : Long.bitCount(iteratorAvailableBits);
    }

    // Pool entry wrapper to keep iterator and metadata together in memory
    private final class IteratorPoolEntry {
        final #upperKey##upperValue#HashMapIterator iterator;

        IteratorPoolEntry(final int poolIndex) {
            this.iterator = new #upperKey##upperValue#HashMapIterator(poolIndex);
        }
    }

    public final class #upperKey##upperValue#HashMapIterator implements AutoCloseable {
        // Pad to avoid false sharing between pooled iterators
        private long p0, p1, p2, p3, p4, p5, p6;

        private final int poolIndex;
        private int cursor;
        private int expectedModCount;
        private int lastReturnedIndex = -1;

        // More padding to ensure this iterator doesn't share cache lines
        private long p8, p9, p10, p11, p12, p13, p14;

        private #upperKey##upperValue#HashMapIterator(final int poolIndex) {
            this.poolIndex = poolIndex;
            reset();
        }

        void reset() {
            this.cursor = 0;
            this.expectedModCount = modCount;
            this.lastReturnedIndex = -1;
        }

        /**
         * Returns true if there are more occupied slots to iterate.
         */
        public boolean hasNext() {
            int i = cursor;
            while (i < keys.length) {
                if (isSlotOccupied(i)) {
                    return true;
                }
                i++;
            }
            return false;
        }

        /**
         * Advances and returns the next key.
         */
        public #primitiveKey# nextKey() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }

            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    lastReturnedIndex = cursor;
                    return keys[cursor++];
                }
                cursor++;
            }
            throw new NoSuchElementException();
        }

        /**
         * Advances and returns the next value.
         */
        public #primitiveValue# nextValue() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }

            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    lastReturnedIndex = cursor;
                    return values[cursor++];
                }
                cursor++;
            }
            throw new NoSuchElementException();
        }

        /**
         * Peeks the next key without advancing.
         */
        public #primitiveKey# peekNextKey() {
            int i = cursor;
            while (i < keys.length) {
                if (isSlotOccupied(i)) {
                    return keys[i];
                }
                i++;
            }
            throw new NoSuchElementException();
        }

        /**
         * Peeks the next value without advancing.
         */
        public #primitiveValue# peekNextValue() {
            int i = cursor;
            while (i < keys.length) {
                if (isSlotOccupied(i)) {
                    return values[i];
                }
                i++;
            }
            throw new NoSuchElementException();
        }

        /**
         * Removes the last returned entry.
         */
        public void remove() {
            if (lastReturnedIndex < 0) {
                throw new IllegalStateException("next() has not been called or remove() already called");
            }

            markSlotTombstone(lastReturnedIndex);
            values[lastReturnedIndex] = nullValue;
            size--;
            modCount++;
            expectedModCount = modCount;
            lastReturnedIndex = -1;
        }

        /**
         * Applies the given consumer to all remaining entries.
         */
        public void forEachRemaining(final #upperKey##upperValue#Consumer action) {
            Objects.requireNonNull(action);

            final #primitiveKey#[] ks = keys;
            final #primitiveValue#[] vs = values;
            while (cursor < ks.length) {
                if (isSlotOccupied(cursor)) {
                    action.accept(ks[cursor], vs[cursor]);
                }
                cursor++;
            }
        }

        @Override
        public void close() {
            #upperKey#2#upperValue#HashMap.this.returnIterator(this);
        }
    }

    // ------------------- Boxed Iterators -------------------

    private abstract class BaseIterator<T> implements Iterator<T> {
        int cursor = 0;
        int expectedModCount = modCount;

        @Override
        public boolean hasNext() {
            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    return true;
                }
                cursor++;
            }
            return false;
        }

        protected void checkModCount() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    private final class KeyIterator extends BaseIterator<#boxedKey#> {
        @Override
        public #boxedKey# next() {
            checkModCount();

            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    return keys[cursor++];
                }
                cursor++;
            }
            throw new NoSuchElementException();
        }
    }

    private final class ValueIterator extends BaseIterator<#boxedValue#> {
        @Override
        public #boxedValue# next() {
            checkModCount();

            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    final #primitiveValue# value = values[cursor++];
                    return isValueEqual(value, nullValue) ? null : value;
                }
                cursor++;
            }
            throw new NoSuchElementException();
        }
    }

    private final class EntryIterator extends BaseIterator<Entry<#boxedKey#, #boxedValue#>> {
        @Override
        public Entry<#boxedKey#, #boxedValue#> next() {
            checkModCount();

            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    final #primitiveKey# key = keys[cursor];
                    final #primitiveValue# value = values[cursor];
                    cursor++;
                    return new SimpleEntry<>(key, isValueEqual(value, nullValue) ? null : value);
                }
                cursor++;
            }
            throw new NoSuchElementException();
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

    // ------------------- Hashing and Probing -------------------

    private int findSlotForPut(final #primitiveKey# key) {
        int slot = hash(key) & mask;

        // Linear probing
        while (true) {
            if (isSlotEmptyOrTombstone(slot)) {
                return slot;
            }
            if (isKeyEqual(keys[slot], key)) {
                return slot;
            }
            slot = (slot + 1) & mask;
        }
    }

    private int findSlotForGet(final #primitiveKey# key) {
        int slot = hash(key) & mask;

        // Linear probing
        while (true) {
            if (isSlotEmpty(slot)) {
                return -1;
            }
            if (isSlotOccupied(slot) && isKeyEqual(keys[slot], key)) {
                return slot;
            }
            slot = (slot + 1) & mask;
        }
    }

    private int hash(final #primitiveKey# key) {
        #hash_implementation#
    }

    private void resize() {
        final int newCapacity = keys.length << 1;
        if (newCapacity > MAX_CAPACITY) {
            throw new OutOfMemoryError("#upperKey#2#upperValue#HashMap capacity exceeded");
        }

        final #primitiveKey#[] oldKeys = keys;
        final #primitiveValue#[] oldValues = values;
        #save_old_state#;

        keys = new #primitiveKey#[newCapacity];
        values = new #primitiveValue#[newCapacity];
        #state_array_init_resize#;

        mask = newCapacity - 1;
        threshold = (int) (newCapacity * loadFactor);
        size = 0;

        // Rehash all entries
        for (int i = 0; i < oldKeys.length; i++) {
            if (#was_occupied#) {
                final int slot = findSlotForPut(oldKeys[i]);
                keys[slot] = oldKeys[i];
                values[slot] = oldValues[i];
                markSlotOccupied(slot);
                size++;
            }
        }
    }

    // ------------------- Builder -------------------

    public static class Builder {
        private int initialCapacity = DEFAULT_INITIAL_CAPACITY;
        private float loadFactor = DEFAULT_LOAD_FACTOR;
        private #primitiveValue# nullValue = DEFAULT_NULL_VALUE;
        private boolean enableIteratorPool = true;

        public Builder initialCapacity(final int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        public Builder loadFactor(final float loadFactor) {
            this.loadFactor = loadFactor;
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

        public #upperKey#2#upperValue#HashMap build() {
            return new #upperKey#2#upperValue#HashMap(
                initialCapacity, loadFactor, nullValue, enableIteratorPool
            );
        }
    }
}
