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
 * A Map implementation that stores Object-to-#primitiveValue# mappings without boxing values to achieve high performance.
 * Uses open addressing with linear probing for collision resolution.
 * <p>
 * Null keys are not supported.
 * <p>
 * This class is not thread-safe.
 *
 * @param <K> the type of keys stored in this map
 */
public final class Object2#upperValue#HashMap<K> implements Map<K, #boxedValue#> {

    public static final #primitiveValue# DEFAULT_NULL_VALUE = #nullValue#;
    public static final int DEFAULT_INITIAL_CAPACITY = 16;
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private static final int MAX_CAPACITY = 1 << 30;
    private static final int ITERATOR_POOL_SIZE = 8;
    private static final byte STATE_EMPTY = 0;
    private static final byte STATE_OCCUPIED = 1;
    private static final byte STATE_TOMBSTONE = 2;

    // Hot fields - accessed on every operation (grouped for cache locality)
    private K[] keys;
    private #primitiveValue#[] values;
    private byte[] state;
    private int size = 0;
    private int mask;
    private int threshold;

    // Warm fields
    public final #primitiveValue# nullValue;

    // Cold fields - accessed rarely
    private final float loadFactor;
    private int modCount = 0;
    private final Object[] iteratorPool;
    private long iteratorAvailableBits;

    @SuppressWarnings("unchecked")
    private Object2#upperValue#HashMap(
            final int initialCapacity,
            final float loadFactor,
            final #primitiveValue# nullValue,
            final boolean enableIteratorPool
    ) {
        int capacity = 1;
        while (capacity < initialCapacity) capacity <<= 1;

        this.keys = (K[]) new Object[capacity];
        this.values = new #primitiveValue#[capacity];
        this.state = new byte[capacity];
        Arrays.fill(state, STATE_EMPTY);

        this.mask = capacity - 1;
        this.loadFactor = loadFactor;
        this.threshold = (int) (capacity * loadFactor);
        this.nullValue = nullValue;

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

    public static <K> Builder<K> builder() { return new Builder<>(); }

    // ------------------- Primitive Operations -------------------

    public void put(final K key, final #primitiveValue# value) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");

        if (size >= threshold) resize();

        final int slot = findSlotForPut(key);
        final boolean isNewKey = isSlotEmptyOrTombstone(slot);

        keys[slot] = key;
        values[slot] = value;
        state[slot] = STATE_OCCUPIED;

        if (isNewKey) {
            size++;
            modCount++;
        }
    }

    public #primitiveValue# get#upperValue#(final K key) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        final int slot = findSlotForGet(key);
        return slot >= 0 ? values[slot] : nullValue;
    }

    public #primitiveValue# getOrDefault(final K key, final #primitiveValue# defaultValue) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        final int slot = findSlotForGet(key);
        return slot >= 0 ? values[slot] : defaultValue;
    }

    public boolean containsValue(final #primitiveValue# value) {
        final #primitiveValue#[] vs = values;
        for (int i = 0; i < vs.length; i++) {
            if (state[i] == STATE_OCCUPIED && isValueEqual(vs[i], value)) {
                return true;
            }
        }
        return false;
    }

    public #primitiveValue# remove#upperValue#(final K key) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        final int slot = findSlotForGet(key);
        if (slot >= 0) {
            final #primitiveValue# oldValue = values[slot];
            state[slot] = STATE_TOMBSTONE;
            keys[slot] = null;
            values[slot] = nullValue;
            size--;
            modCount++;
            return oldValue;
        }
        return nullValue;
    }

    public void forEachObject#upperValue#(final Object#upperValue#Consumer<K> action) {
        Objects.requireNonNull(action);

        final K[] ks = keys;
        final #primitiveValue#[] vs = values;
        for (int i = 0; i < ks.length; i++) {
            if (state[i] == STATE_OCCUPIED) {
                action.accept(ks[i], vs[i]);
            }
        }
    }

    @FunctionalInterface
    public interface Object#upperValue#Consumer<K> {
        void accept(K key, #primitiveValue# value);
    }

    // ------------------- Helper Methods -------------------

    private boolean isValueEqual(final #primitiveValue# v1, final #primitiveValue# v2) {
        #value_equals#
    }

    private boolean isSlotEmpty(final int slot) {
        return state[slot] == STATE_EMPTY;
    }

    private boolean isSlotOccupied(final int slot) {
        return state[slot] == STATE_OCCUPIED;
    }

    private boolean isSlotEmptyOrTombstone(final int slot) {
        return state[slot] != STATE_OCCUPIED;
    }

    // ------------------- Map Interface -------------------

    @Override
    public #boxedValue# put(final K key, final #boxedValue# value) {
        if (key == null || value == null) {
            throw new NullPointerException("Null keys and values are not supported");
        }
        final #primitiveValue# oldValue = get#upperValue#(key);
        put(key, (#primitiveValue#) value);
        return isValueEqual(oldValue, nullValue) ? null : oldValue;
    }

    @Override
    public #boxedValue# get(final Object key) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        @SuppressWarnings("unchecked")
        final #primitiveValue# value = get#upperValue#((K) key);
        return value == nullValue ? null : value;
    }

    @Override
    public boolean containsKey(final Object key) {
        if (key == null) throw new IllegalArgumentException("Null keys are not permitted");
        @SuppressWarnings("unchecked") final K k = (K) key;
        return findSlotForGet(k) >= 0;
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
        return value == nullValue ? null : value;
    }

    @Override
    public void clear() {
        if (size > 0) {
            Arrays.fill(state, STATE_EMPTY);
            Arrays.fill(keys, null);
            size = 0;
            modCount++;
        }
    }

    @Override
    public void putAll(final Map<? extends K, ? extends #boxedValue#> m) {
        for (Map.Entry<? extends K, ? extends #boxedValue#> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public int size() { return size; }

    @Override
    public boolean isEmpty() { return size == 0; }

    @Override
    public Set<K> keySet() {
        return new AbstractSet<K>() {
            @Override public int size() { return Object2#upperValue#HashMap.this.size(); }
            @Override public boolean contains(final Object o) { return containsKey(o); }
            @Override public Iterator<K> iterator() { return new KeyIterator(); }
            @Override public void clear() { Object2#upperValue#HashMap.this.clear(); }
        };
    }

    @Override
    public Collection<#boxedValue#> values() {
        return new AbstractCollection<#boxedValue#>() {
            @Override public int size() { return Object2#upperValue#HashMap.this.size(); }
            @Override public boolean contains(final Object o) { return containsValue(o); }
            @Override public Iterator<#boxedValue#> iterator() { return new ValueIterator(); }
            @Override public void clear() { Object2#upperValue#HashMap.this.clear(); }
        };
    }

    @Override
    public Set<Entry<K, #boxedValue#>> entrySet() {
        return new AbstractSet<Entry<K, #boxedValue#>>() {
            @Override public int size() { return Object2#upperValue#HashMap.this.size(); }
            @Override public Iterator<Entry<K, #boxedValue#>> iterator() { return new EntryIterator(); }
            @Override public void clear() { Object2#upperValue#HashMap.this.clear(); }
        };
    }

    // ------------------- Iterator Pool -------------------

    public Object#upperValue#HashMapIterator borrowIterator() {
        if (iteratorPool == null) return null;

        long bits = iteratorAvailableBits;
        if (bits == 0) return null;

        final int poolIndex = Long.numberOfTrailingZeros(bits);
        iteratorAvailableBits = bits & ~(1L << poolIndex);

        @SuppressWarnings("unchecked")
        final Object#upperValue#HashMapIterator iter = ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        iter.reset();
        return iter;
    }

    public void returnIterator(final Object#upperValue#HashMapIterator iterator) {
        if (iteratorPool == null || iterator == null) return;
        final int poolIndex = iterator.poolIndex;
        if (poolIndex < 0 || poolIndex >= ITERATOR_POOL_SIZE) return;
        @SuppressWarnings("unchecked") final Object#upperValue#HashMapIterator iterator_ =
            ((IteratorPoolEntry) iteratorPool[poolIndex]).iterator;
        if (iterator_ != iterator) return;

        iteratorAvailableBits |= (1L << poolIndex);
    }

    public int availableIteratorCount() {
        return iteratorPool == null ? 0 : Long.bitCount(iteratorAvailableBits);
    }

    private final class IteratorPoolEntry {
        final Object#upperValue#HashMapIterator iterator;

        IteratorPoolEntry(final int poolIndex) {
            this.iterator = new Object#upperValue#HashMapIterator(poolIndex);
        }
    }

    public final class Object#upperValue#HashMapIterator implements AutoCloseable {
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

        private Object#upperValue#HashMapIterator(final int poolIndex) {
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
                if (state[cursor] == STATE_OCCUPIED) {
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

        public K nextKey() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            if (nextOccupiedSlot < 0) {
                throw new NoSuchElementException();
            }

            lastReturnedIndex = nextOccupiedSlot;
            final K key = keys[nextOccupiedSlot];
            cursor = nextOccupiedSlot + 1;
            advanceToNextOccupied();
            return key;
        }

        public #primitiveValue# nextValue() {
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            if (nextOccupiedSlot < 0) {
                throw new NoSuchElementException();
            }

            lastReturnedIndex = nextOccupiedSlot;
            final #primitiveValue# value = values[nextOccupiedSlot];
            cursor = nextOccupiedSlot + 1;
            advanceToNextOccupied();
            return value;
        }

        public K peekNextKey() {
            if (nextOccupiedSlot < 0) {
                throw new NoSuchElementException();
            }
            return keys[nextOccupiedSlot];
        }

        public #primitiveValue# peekNextValue() {
            if (nextOccupiedSlot < 0) {
                throw new NoSuchElementException();
            }
            return values[nextOccupiedSlot];
        }

        public void remove() {
            if (lastReturnedIndex < 0) {
                throw new IllegalStateException("next() has not been called or remove() already called");
            }

            state[lastReturnedIndex] = STATE_TOMBSTONE;
            keys[lastReturnedIndex] = null;
            values[lastReturnedIndex] = nullValue;
            size--;
            modCount++;
            expectedModCount = modCount;
            lastReturnedIndex = -1;
        }

        public void forEachRemaining(final Object#upperValue#Consumer<K> action) {
            Objects.requireNonNull(action);

            final K[] ks = keys;
            final #primitiveValue#[] vs = values;

            if (nextOccupiedSlot >= 0) {
                action.accept(ks[nextOccupiedSlot], vs[nextOccupiedSlot]);
                cursor = nextOccupiedSlot + 1;
            }

            while (cursor < ks.length) {
                if (state[cursor] == STATE_OCCUPIED) {
                    action.accept(ks[cursor], vs[cursor]);
                }
                cursor++;
            }
            nextOccupiedSlot = -1;
        }

        @Override
        public void close() {
            Object2#upperValue#HashMap.this.returnIterator(this);
        }
    }

    // ------------------- Boxed Iterators -------------------

    private abstract class BaseIterator<T> implements Iterator<T> {
        int cursor = 0;
        int expectedModCount = modCount;

        @Override
        public boolean hasNext() {
            while (cursor < keys.length) {
                if (state[cursor] == STATE_OCCUPIED) {
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

    private final class KeyIterator extends BaseIterator<K> {
        @Override
        public K next() {
            checkModCount();

            while (cursor < keys.length) {
                if (state[cursor] == STATE_OCCUPIED) {
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
                if (state[cursor] == STATE_OCCUPIED) {
                    final #primitiveValue# value = values[cursor++];
                    return isValueEqual(value, nullValue) ? null : value;
                }
                cursor++;
            }
            throw new NoSuchElementException();
        }
    }

    private final class EntryIterator extends BaseIterator<Entry<K, #boxedValue#>> {
        @Override
        public Entry<K, #boxedValue#> next() {
            checkModCount();

            while (cursor < keys.length) {
                if (state[cursor] == STATE_OCCUPIED) {
                    final K key = keys[cursor];
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

    private int findSlotForPut(final K key) {
        int slot = hash(key) & mask;

        while (true) {
            if (isSlotEmptyOrTombstone(slot)) {
                return slot;
            }
            if (key.equals(keys[slot])) {
                return slot;
            }
            slot = (slot + 1) & mask;
        }
    }

    private int findSlotForGet(final K key) {
        int slot = hash(key) & mask;

        while (true) {
            if (isSlotEmpty(slot)) {
                return -1;
            }
            if (isSlotOccupied(slot) && key.equals(keys[slot])) {
                return slot;
            }
            slot = (slot + 1) & mask;
        }
    }

    private int hash(final K key) {
        int h = key.hashCode();
        h ^= (h >>> 16);
        return h;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        final int newCapacity = keys.length << 1;
        if (newCapacity > MAX_CAPACITY) {
            throw new OutOfMemoryError("Object2#upperValue#HashMap capacity exceeded");
        }

        final K[] oldKeys = keys;
        final #primitiveValue#[] oldValues = values;
        final byte[] oldState = state;

        keys = (K[]) new Object[newCapacity];
        values = new #primitiveValue#[newCapacity];
        state = new byte[newCapacity];
        Arrays.fill(state, STATE_EMPTY);

        mask = newCapacity - 1;
        threshold = (int) (newCapacity * loadFactor);
        size = 0;

        for (int i = 0; i < oldKeys.length; i++) {
            if (oldState[i] == STATE_OCCUPIED) {
                final int slot = findSlotForPut(oldKeys[i]);
                keys[slot] = oldKeys[i];
                values[slot] = oldValues[i];
                state[slot] = STATE_OCCUPIED;
                size++;
            }
        }
    }

    // ------------------- Builder -------------------

    public static class Builder<K> {
        private int initialCapacity = DEFAULT_INITIAL_CAPACITY;
        private float loadFactor = DEFAULT_LOAD_FACTOR;
        private #primitiveValue# nullValue = DEFAULT_NULL_VALUE;
        private boolean enableIteratorPool = true;

        public Builder<K> initialCapacity(final int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        public Builder<K> loadFactor(final float loadFactor) {
            this.loadFactor = loadFactor;
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

        public Object2#upperValue#HashMap<K> build() {
            return new Object2#upperValue#HashMap<>(
                initialCapacity, loadFactor, nullValue, enableIteratorPool
            );
        }
    }
}
