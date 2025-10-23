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
 *
 * For floating point keys (float/double):
 * - Uses bitwise equality (Double.doubleToRawLongBits for doubles, Float.floatToRawIntBits for floats)
 * - NaN values are supported and treated as equal to each other
 * - +0.0 and -0.0 are treated as distinct keys (different bit patterns)
 *
 * This class is not thread-safe.
 */
public final class #upperKey#2#upperValue#HashMap implements Map<#boxedKey#, #boxedValue#> {

    public static final #primitiveKey# DEFAULT_NULL_KEY = #nullKey#;
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
    #state_array_declaration#; // For float/double: byte[] to track empty/tombstone state
    private int size = 0;
    private int mask;
    private int threshold;

    // Warm fields - accessed frequently but not every operation
    private final boolean useNullKey;
    private final boolean useNullValue;
    private final #primitiveKey# nullKey;
    private final #primitiveValue# nullValue;
    private boolean hasNullKey = false;
    private #primitiveValue# nullKeyValue;

    // Cold fields - accessed rarely
    private final float loadFactor;
    private int modCount = 0;
    private final IteratorPoolEntry[] iteratorPool;
    private long iteratorAvailableBits;

    private #upperKey#2#upperValue#HashMap(
            final int initialCapacity,
            final float loadFactor,
            final #primitiveKey# nullKey,
            final #primitiveValue# nullValue,
            final boolean useNullKey,
            final boolean useNullValue,
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

        this.nullKey = nullKey;
        this.nullValue = nullValue;
        this.useNullKey = useNullKey;
        this.useNullValue = useNullValue;

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

    public void put#upperKey##upperValue#(final #primitiveKey# key, final #primitiveValue# value) {
        if (useNullKey && isKeyEqual(key, nullKey)) {
            if (!hasNullKey) {
                hasNullKey = true;
                size++;
                modCount++;
            }
            nullKeyValue = value;
            return;
        }

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

    public #primitiveValue# get#upperValue#(final #primitiveKey# key) {
        if (useNullKey && isKeyEqual(key, nullKey)) {
            return hasNullKey ? nullKeyValue : (useNullValue ? nullValue : #defaultValue#);
        }

        final int slot = findSlotForGet(key);
        if (slot >= 0) {
            return values[slot];
        }
        return useNullValue ? nullValue : #defaultValue#;
    }

    public #primitiveValue# getOrDefault#upperValue#(final #primitiveKey# key, final #primitiveValue# defaultValue) {
        if (useNullKey && isKeyEqual(key, nullKey)) {
            return hasNullKey ? nullKeyValue : defaultValue;
        }

        final int slot = findSlotForGet(key);
        return slot >= 0 ? values[slot] : defaultValue;
    }

    public boolean contains#upperKey#(final #primitiveKey# key) {
        if (useNullKey && isKeyEqual(key, nullKey)) {
            return hasNullKey;
        }
        return findSlotForGet(key) >= 0;
    }

    public boolean contains#upperValue##value_suffix#(final #primitiveValue# value) {
        if (hasNullKey && isValueEqual(nullKeyValue, value)) {
            return true;
        }

        final #primitiveKey#[] ks = keys;
        final #primitiveValue#[] vs = values;
        for (int i = 0; i < ks.length; i++) {
            if (isSlotOccupied(i) && isValueEqual(vs[i], value)) {
                return true;
            }
        }
        return false;
    }

    public #primitiveValue# remove#upperKey#(final #primitiveKey# key) {
        if (useNullKey && isKeyEqual(key, nullKey)) {
            if (hasNullKey) {
                hasNullKey = false;
                size--;
                modCount++;
                final #primitiveValue# old = nullKeyValue;
                nullKeyValue = useNullValue ? nullValue : #defaultValue#;
                return old;
            }
            return useNullValue ? nullValue : #defaultValue#;
        }

        final int slot = findSlotForGet(key);
        if (slot >= 0) {
            final #primitiveValue# oldValue = values[slot];
            markSlotTombstone(slot);
            values[slot] = useNullValue ? nullValue : #defaultValue#;
            size--;
            modCount++;
            return oldValue;
        }
        return useNullValue ? nullValue : #defaultValue#;
    }

    public void forEach#upperKey##upperValue#(final #upperKey##upperValue#Consumer action) {
        Objects.requireNonNull(action);

        if (hasNullKey) {
            action.accept(nullKey, nullKeyValue);
        }

        final #primitiveKey#[] ks = keys;
        final #primitiveValue#[] vs = values;
        for (int i = 0; i < ks.length; i++) {
            if (isSlotOccupied(i)) {
                action.accept(ks[i], vs[i]);
            }
        }
    }

    @FunctionalInterface
    public interface #upperKey##upperValue#Consumer {
        void accept(#primitiveKey# key, #primitiveValue# value);
    }

    // ------------------- Helper Methods for State Management -------------------

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

    @Override
    public #boxedValue# put(final #boxedKey# key, final #boxedValue# value) {
        final #primitiveKey# k = useNullKey && key == null ? nullKey : key;
        final #primitiveValue# v = useNullValue && value == null ? nullValue : value;
        final #primitiveValue# oldValue = get#upperValue#(k);
        put#upperKey##upperValue#(k, v);
        return useNullValue && isValueEqual(oldValue, nullValue) ? null : oldValue;
    }

    @Override
    public #boxedValue# get(final Object key) {
        if (useNullKey && key == null) {
            return hasNullKey ? (useNullValue && isValueEqual(nullKeyValue, nullValue) ? null : nullKeyValue) : null;
        }
        if (!(key instanceof #boxedKey#)) return null;

        final #primitiveKey# k = (#boxedKey#) key;
        final #primitiveValue# v = get#upperValue#(k);
        return useNullValue && isValueEqual(v, nullValue) ? null : v;
    }

    @Override
    public boolean containsKey(final Object key) {
        if (useNullKey && key == null) {
            return hasNullKey;
        }
        if (!(key instanceof #boxedKey#)) return false;

        final #primitiveKey# k = (#boxedKey#) key;
        return contains#upperKey#(k);
    }

    @Override
    public boolean containsValue(final Object value) {
        if (useNullValue && value == null) {
            return contains#upperValue##value_suffix#(nullValue);
        }
        if (!(value instanceof #boxedValue#)) return false;

        final #primitiveValue# v = (#boxedValue#) value;
        return contains#upperValue##value_suffix#(v);
    }

    @Override
    public #boxedValue# remove(final Object key) {
        if (useNullKey && key == null) {
            final #primitiveValue# v = remove#upperKey#(nullKey);
            return useNullValue && isValueEqual(v, nullValue) ? null : v;
        }
        if (!(key instanceof #boxedKey#)) return null;

        final #primitiveKey# k = (#boxedKey#) key;
        final #primitiveValue# v = remove#upperKey#(k);
        return useNullValue && isValueEqual(v, nullValue) ? null : v;
    }

    @Override
    public void clear() {
        if (size > 0) {
            #clear_state#;
            size = 0;
            hasNullKey = false;
            modCount++;
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
        return new AbstractSet<#boxedKey#>() {
            @Override public int size() { return #upperKey#2#upperValue#HashMap.this.size(); }
            @Override public boolean contains(final Object o) { return containsKey(o); }
            @Override public Iterator<#boxedKey#> iterator() { return new KeyIterator(); }
            @Override public void clear() { #upperKey#2#upperValue#HashMap.this.clear(); }
        };
    }

    @Override
    public Collection<#boxedValue#> values() {
        return new AbstractCollection<#boxedValue#>() {
            @Override public int size() { return #upperKey#2#upperValue#HashMap.this.size(); }
            @Override public boolean contains(final Object o) { return containsValue(o); }
            @Override public Iterator<#boxedValue#> iterator() { return new ValueIterator(); }
            @Override public void clear() { #upperKey#2#upperValue#HashMap.this.clear(); }
        };
    }

    @Override
    public Set<Entry<#boxedKey#, #boxedValue#>> entrySet() {
        return new AbstractSet<Entry<#boxedKey#, #boxedValue#>>() {
            @Override public int size() { return #upperKey#2#upperValue#HashMap.this.size(); }
            @Override public Iterator<Entry<#boxedKey#, #boxedValue#>> iterator() { return new EntryIterator(); }
            @Override public void clear() { #upperKey#2#upperValue#HashMap.this.clear(); }
        };
    }

    // ------------------- Iterator Pool -------------------

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

    public void returnIterator(final #upperKey##upperValue#HashMapIterator iterator) {
        if (iteratorPool == null || iterator == null) return;
        final int poolIndex = iterator.poolIndex;
        if (poolIndex < 0 || poolIndex >= ITERATOR_POOL_SIZE) return;
        if (iteratorPool[poolIndex].iterator != iterator) return;

        iteratorAvailableBits |= (1L << poolIndex);
    }

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
        private boolean visitedNullKey;
        private int expectedModCount;

        // More padding to ensure this iterator doesn't share cache lines
        private long p8, p9, p10, p11, p12, p13, p14;

        private #upperKey##upperValue#HashMapIterator(final int poolIndex) {
            this.poolIndex = poolIndex;
            reset();
        }

        void reset() {
            this.cursor = 0;
            this.visitedNullKey = false;
            this.expectedModCount = modCount;
        }

        public boolean hasNext() {
            if (hasNullKey && !visitedNullKey) return true;

            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    return true;
                }
                cursor++;
            }
            return false;
        }

        public #primitiveKey# nextKey() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }

            if (hasNullKey && !visitedNullKey) {
                visitedNullKey = true;
                return nullKey;
            }

            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    return keys[cursor++];
                }
                cursor++;
            }
            throw new NoSuchElementException();
        }

        public #primitiveValue# nextValue() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }

            if (hasNullKey && !visitedNullKey) {
                visitedNullKey = true;
                return nullKeyValue;
            }

            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    return values[cursor++];
                }
                cursor++;
            }
            throw new NoSuchElementException();
        }

        public void forEachRemaining(final #upperKey##upperValue#Consumer action) {
            Objects.requireNonNull(action);

            if (hasNullKey && !visitedNullKey) {
                visitedNullKey = true;
                action.accept(nullKey, nullKeyValue);
            }

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
        boolean visitedNullKey = false;
        int expectedModCount = modCount;

        @Override
        public boolean hasNext() {
            if (hasNullKey && !visitedNullKey) return true;

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

            if (hasNullKey && !visitedNullKey) {
                visitedNullKey = true;
                return useNullKey && isKeyEqual(nullKey, #upperKey#2#upperValue#HashMap.this.nullKey) ? null : nullKey;
            }

            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    final #primitiveKey# key = keys[cursor++];
                    return useNullKey && isKeyEqual(key, #upperKey#2#upperValue#HashMap.this.nullKey) ? null : key;
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

            if (hasNullKey && !visitedNullKey) {
                visitedNullKey = true;
                final #primitiveValue# v = nullKeyValue;
                return useNullValue && isValueEqual(v, nullValue) ? null : v;
            }

            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    final #primitiveValue# value = values[cursor++];
                    return useNullValue && isValueEqual(value, nullValue) ? null : value;
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

            if (hasNullKey && !visitedNullKey) {
                visitedNullKey = true;
                return new SimpleEntry<>(
                    useNullKey && isKeyEqual(nullKey, #upperKey#2#upperValue#HashMap.this.nullKey) ? null : nullKey,
                    useNullValue && isValueEqual(nullKeyValue, nullValue) ? null : nullKeyValue
                );
            }

            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    final #primitiveKey# key = keys[cursor];
                    final #primitiveValue# value = values[cursor];
                    cursor++;
                    return new SimpleEntry<>(
                        useNullKey && isKeyEqual(key, #upperKey#2#upperValue#HashMap.this.nullKey) ? null : key,
                        useNullValue && isValueEqual(value, nullValue) ? null : value
                    );
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
        size = hasNullKey ? 1 : 0;

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
        private #primitiveKey# nullKey = DEFAULT_NULL_KEY;
        private #primitiveValue# nullValue = DEFAULT_NULL_VALUE;
        private boolean useNullKey = true;
        private boolean useNullValue = true;
        private boolean enableIteratorPool = true;

        public Builder initialCapacity(final int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        public Builder loadFactor(final float loadFactor) {
            this.loadFactor = loadFactor;
            return this;
        }

        public Builder nullKey(final #primitiveKey# nullKey) {
            this.nullKey = nullKey;
            this.useNullKey = true;
            return this;
        }

        public Builder nullValue(final #primitiveValue# nullValue) {
            this.nullValue = nullValue;
            this.useNullValue = true;
            return this;
        }

        public Builder disableNullKey() {
            this.useNullKey = false;
            return this;
        }

        public Builder disableNullValue() {
            this.useNullValue = false;
            return this;
        }

        public Builder disableIteratorPool() {
            this.enableIteratorPool = false;
            return this;
        }

        public #upperKey#2#upperValue#HashMap build() {
            return new #upperKey#2#upperValue#HashMap(
                initialCapacity, loadFactor, nullKey, nullValue,
                useNullKey, useNullValue, enableIteratorPool
            );
        }
    }
}
