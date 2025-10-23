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
 * A Map implementation that stores #primitiveKey#-to-Object mappings without boxing keys to achieve high performance.
 * Uses open addressing with linear probing for collision resolution.
 * <p>
 * For floating point keys (float/double):
 * - Uses bitwise equality (Double.doubleToLongBits for doubles, Float.floatToIntBits for floats)
 * - NaN values are supported and treated as equal to each other
 * - +0.0 and -0.0 are treated as the same key
 * <p>
 * Null keys and null values are not supported.
 * <p>
 * This class is not thread-safe.
 *
 * @param <V> the type of values stored in this map
 */
public final class #upperKey#2ObjectHashMap<V> implements Map<#boxedKey#, V> {

    public static final int DEFAULT_INITIAL_CAPACITY = 16;
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private static final int MAX_CAPACITY = 1 << 30;
    private static final int ITERATOR_POOL_SIZE = 8;

    // Sentinel values for internal state tracking
    #sentinel_declarations#

    // Hot fields - accessed on every operation (grouped for cache locality)
    private #primitiveKey#[] keys;
    private V[] values;
    #state_array_declaration# // For float/double: byte[] to track empty/tombstone state
    private int size = 0;
    private int mask;
    private int threshold;

    // Cold fields - accessed rarely
    private final float loadFactor;
    private int modCount = 0;
    private final Object[] iteratorPool;
    private long iteratorAvailableBits;

    @SuppressWarnings("unchecked")
    private #upperKey#2ObjectHashMap(
            final int initialCapacity,
            final float loadFactor,
            final boolean enableIteratorPool
    ) {
        int capacity = 1;
        while (capacity < initialCapacity) capacity <<= 1;

        this.keys = new #primitiveKey#[capacity];
        this.values = (V[]) new Object[capacity];
        #state_array_init#; // Initialize state array for float/double

        this.mask = capacity - 1;
        this.loadFactor = loadFactor;
        this.threshold = (int) (capacity * loadFactor);

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

    // ------------------- Primitive Operations -------------------

    public void put(final #primitiveKey# key, final V value) {
        if (isNullKey(key)) throw new IllegalArgumentException("Null keys are not permitted");
        if (value == null) throw new IllegalArgumentException("Null values are not permitted");

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

    public V get(final #primitiveKey# key) {
        if (isNullKey(key)) throw new IllegalArgumentException("Null keys are not permitted");
        final int slot = findSlotForGet(key);
        return slot >= 0 ? values[slot] : null;
    }

    public V getOrDefault(final #primitiveKey# key, final V defaultValue) {
        if (isNullKey(key)) throw new IllegalArgumentException("Null keys are not permitted");
        final int slot = findSlotForGet(key);
        return slot >= 0 ? values[slot] : defaultValue;
    }

    public boolean containsKey(final #primitiveKey# key) {
        if (isNullKey(key)) throw new IllegalArgumentException("Null keys are not permitted");
        return findSlotForGet(key) >= 0;
    }

    public V remove(final #primitiveKey# key) {
        if (isNullKey(key)) throw new IllegalArgumentException("Null keys are not permitted");

        final int slot = findSlotForGet(key);
        if (slot >= 0) {
            final V oldValue = values[slot];
            markSlotTombstone(slot);
            values[slot] = null;
            size--;
            modCount++;
            return oldValue;
        }
        return null;
    }

    public void forEach#upperKey#Object(final #upperKey#ObjectConsumer<V> action) {
        Objects.requireNonNull(action);

        final #primitiveKey#[] ks = keys;
        final V[] vs = values;
        for (int i = 0; i < ks.length; i++) {
            if (isSlotOccupied(i)) {
                action.accept(ks[i], vs[i]);
            }
        }
    }

    @FunctionalInterface
    public interface #upperKey#ObjectConsumer<V> {
        void accept(#primitiveKey# key, V value);
    }

    // ------------------- Helper Methods for State Management -------------------

    private boolean isNullKey(final #primitiveKey# key) {
        return #key_null#;
    }

    private boolean isKeyEqual(final #primitiveKey# v1, final #primitiveKey# v2) {
        #key_equals#
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
    public boolean containsValue(final Object value) {
        if (value == null) return false;

        final V[] vs = values;
        for (int i = 0; i < vs.length; i++) {
            if (isSlotOccupied(i) && value.equals(vs[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V remove(final Object key) {
        if (!(key instanceof #boxedKey#)) return null;
        return remove((#primitiveKey#) key);
    }

    @Override
    public void clear() {
        if (size > 0) {
            #clear_state#;
            Arrays.fill(values, null);
            size = 0;
            modCount++;
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
            @Override public int size() { return #upperKey#2ObjectHashMap.this.size(); }
            @Override public boolean contains(final Object o) { return containsKey(o); }
            @Override public Iterator<#boxedKey#> iterator() { return new KeyIterator(); }
            @Override public void clear() { #upperKey#2ObjectHashMap.this.clear(); }
        };
    }

    @Override
    public Collection<V> values() {
        return new AbstractCollection<V>() {
            @Override public int size() { return #upperKey#2ObjectHashMap.this.size(); }
            @Override public boolean contains(final Object o) { return containsValue(o); }
            @Override public Iterator<V> iterator() { return new ValueIterator(); }
            @Override public void clear() { #upperKey#2ObjectHashMap.this.clear(); }
        };
    }

    @Override
    public Set<Entry<#boxedKey#, V>> entrySet() {
        return new AbstractSet<Entry<#boxedKey#, V>>() {
            @Override public int size() { return #upperKey#2ObjectHashMap.this.size(); }
            @Override public Iterator<Entry<#boxedKey#, V>> iterator() { return new EntryIterator(); }
            @Override public void clear() { #upperKey#2ObjectHashMap.this.clear(); }
        };
    }

    // ------------------- Iterator Pool -------------------

    public #upperKey#ObjectHashMapIterator borrowIterator() {
        if (iteratorPool == null) return null;

        long bits = iteratorAvailableBits;
        if (bits == 0) return null;

        final int poolIndex = Long.numberOfTrailingZeros(bits);
        iteratorAvailableBits = bits & ~(1L << poolIndex);

        @SuppressWarnings("unchecked")
        final #upperKey#ObjectHashMapIterator iter = ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        iter.reset();
        return iter;
    }

    public void returnIterator(final #upperKey#ObjectHashMapIterator iterator) {
        if (iteratorPool == null || iterator == null) return;
        final int poolIndex = iterator.poolIndex;
        if (poolIndex < 0 || poolIndex >= ITERATOR_POOL_SIZE) return;
        @SuppressWarnings("unchecked") final #upperKey#ObjectHashMapIterator iterator_ =
                    ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        if (iterator_ != iterator) return;

        iteratorAvailableBits |= (1L << poolIndex);
    }

    public int availableIteratorCount() {
        return iteratorPool == null ? 0 : Long.bitCount(iteratorAvailableBits);
    }

    private final class IteratorPoolEntry {
        final #upperKey#ObjectHashMapIterator iterator;

        IteratorPoolEntry(final int poolIndex) {
            this.iterator = new #upperKey#ObjectHashMapIterator(poolIndex);
        }
    }

    public final class #upperKey#ObjectHashMapIterator implements AutoCloseable {
        // Pad to avoid false sharing between pooled iterators (56 bytes = 7 longs)
        private long p0, p1, p2, p3, p4, p5, p6;

        // Hot fields accessed every iteration (20 bytes = 5 ints)
        private final int poolIndex;
        private int cursor;
        private int nextOccupiedSlot;
        private int expectedModCount;
        private int lastReturnedIndex;

        // Trailing padding to complete cache line
        private long p8, p9, p10, p11, p12;

        private #upperKey#ObjectHashMapIterator(final int poolIndex) {
            this.poolIndex = poolIndex;
            reset();
        }

        void reset() {
            this.cursor = 0;
            this.nextOccupiedSlot = -1;
            this.expectedModCount = modCount;
            this.lastReturnedIndex = -1;
            advanceToNextOccupied();
        }

        private void advanceToNextOccupied() {
            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    nextOccupiedSlot = cursor;
                    return;
                }
                cursor++;
            }
            nextOccupiedSlot = -1;
        }

        public boolean hasNext() {
            return nextOccupiedSlot >= 0;
        }

        public #primitiveKey# nextKey() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            if (nextOccupiedSlot < 0) {
                throw new NoSuchElementException();
            }

            lastReturnedIndex = nextOccupiedSlot;
            final #primitiveKey# key = keys[nextOccupiedSlot];
            cursor = nextOccupiedSlot + 1;
            advanceToNextOccupied();
            return key;
        }

        public V nextValue() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            if (nextOccupiedSlot < 0) {
                throw new NoSuchElementException();
            }

            lastReturnedIndex = nextOccupiedSlot;
            final V value = values[nextOccupiedSlot];
            cursor = nextOccupiedSlot + 1;
            advanceToNextOccupied();
            return value;
        }

        public #primitiveKey# peekNextKey() {
            if (nextOccupiedSlot < 0) {
                throw new NoSuchElementException();
            }
            return keys[nextOccupiedSlot];
        }

        public V peekNextValue() {
            if (nextOccupiedSlot < 0) {
                throw new NoSuchElementException();
            }
            return values[nextOccupiedSlot];
        }

        public void remove() {
            if (lastReturnedIndex < 0) {
                throw new IllegalStateException("next() has not been called or remove() already called");
            }

            markSlotTombstone(lastReturnedIndex);
            values[lastReturnedIndex] = null;
            size--;
            modCount++;
            expectedModCount = modCount;
            lastReturnedIndex = -1;
        }

        public void forEachRemaining(final #upperKey#ObjectConsumer<V> action) {
            Objects.requireNonNull(action);

            final #primitiveKey#[] ks = keys;
            final V[] vs = values;

            if (nextOccupiedSlot >= 0) {
                action.accept(ks[nextOccupiedSlot], vs[nextOccupiedSlot]);
                cursor = nextOccupiedSlot + 1;
            }

            while (cursor < ks.length) {
                if (isSlotOccupied(cursor)) {
                    action.accept(ks[cursor], vs[cursor]);
                }
                cursor++;
            }
            nextOccupiedSlot = -1;
        }

        @Override
        public void close() {
            #upperKey#2ObjectHashMap.this.returnIterator(this);
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

    private final class ValueIterator extends BaseIterator<V> {
        @Override
        public V next() {
            checkModCount();

            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    return values[cursor++];
                }
                cursor++;
            }
            throw new NoSuchElementException();
        }
    }

    private final class EntryIterator extends BaseIterator<Entry<#boxedKey#, V>> {
        @Override
        public Entry<#boxedKey#, V> next() {
            checkModCount();

            while (cursor < keys.length) {
                if (isSlotOccupied(cursor)) {
                    final #primitiveKey# key = keys[cursor];
                    final V value = values[cursor];
                    cursor++;
                    return new SimpleEntry<>(key, value);
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

    @SuppressWarnings("unchecked")
    private void resize() {
        final int newCapacity = keys.length << 1;
        if (newCapacity > MAX_CAPACITY) {
            throw new OutOfMemoryError("#upperKey#2ObjectHashMap capacity exceeded");
        }

        final #primitiveKey#[] oldKeys = keys;
        final V[] oldValues = values;
        #save_old_state#;

        keys = new #primitiveKey#[newCapacity];
        values = (V[]) new Object[newCapacity];
        #state_array_init_resize#;

        mask = newCapacity - 1;
        threshold = (int) (newCapacity * loadFactor);
        size = 0;

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

    public static class Builder<V> {
        private int initialCapacity = DEFAULT_INITIAL_CAPACITY;
        private float loadFactor = DEFAULT_LOAD_FACTOR;
        private boolean enableIteratorPool = true;

        public Builder<V> initialCapacity(final int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        public Builder<V> loadFactor(final float loadFactor) {
            this.loadFactor = loadFactor;
            return this;
        }

        public Builder<V> disableIteratorPool() {
            this.enableIteratorPool = false;
            return this;
        }

        public #upperKey#2ObjectHashMap<V> build() {
            return new #upperKey#2ObjectHashMap<>(
                initialCapacity, loadFactor, enableIteratorPool
            );
        }
    }
}
