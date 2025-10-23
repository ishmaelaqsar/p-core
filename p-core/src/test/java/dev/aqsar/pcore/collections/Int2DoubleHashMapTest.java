package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Int2DoubleHashMapTest {

    private Int2DoubleHashMap map;
    private static final double DELTA = 1e-10;

    @BeforeEach
    void setUp() {
        map = Int2DoubleHashMap.builder().build();
    }

    @Test
    void testPutAndGet() {
        map.put(1, 1.5);
        map.put(2, 2.5);
        map.put(3, 3.14159);

        assertEquals(1.5, map.get(1), DELTA);
        assertEquals(2.5, map.get(2), DELTA);
        assertEquals(3.14159, map.get(3), DELTA);
    }

    @Test
    void testPutOverwrite() {
        map.put(1, 1.5);
        map.put(1, 9.99);

        assertEquals(9.99, map.get(1), DELTA);
        assertEquals(1, map.size());
    }

    @Test
    void testGetMissing() {
        assertEquals(Int2DoubleHashMap.DEFAULT_NULL_VALUE, map.get(999), DELTA);
    }

    @Test
    void testContainsValue() {
        map.put(1, 1.5);
        map.put(2, 2.5);

        assertTrue(map.containsValue(1.5));
        assertTrue(map.containsValue(2.5));
        assertFalse(map.containsValue(3.14));
    }

    @Test
    void testRemove() {
        map.put(1, 1.5);
        map.put(2, 2.5);

        assertEquals(1.5, map.remove(1), DELTA);
        assertFalse(map.containsValue(1));
        assertEquals(1, map.size());
    }

    @Test
    void testZeroValues() {
        map.put(1, 0.0);
        map.put(2, -0.0);

        assertEquals(0.0, map.get(1), DELTA);
        assertEquals(-0.0, map.get(2), DELTA);

        // Note: +0.0 and -0.0 are different in bitwise comparison
        // but equal in == comparison
        assertTrue(map.containsValue(0.0));
    }

    @Test
    void testNaNValues() {
        map.put(1, Double.NaN);
        map.put(2, Double.NaN);

        // All NaNs should be treated as equal (using doubleToLongBits for hash)
        assertTrue(Double.isNaN(map.get(1)));
        assertTrue(map.containsValue(Double.NaN));

        assertEquals(2, map.size()); // Two different keys
    }

    @Test
    void testInfinityValues() {
        map.put(1, Double.POSITIVE_INFINITY);
        map.put(2, Double.NEGATIVE_INFINITY);

        assertEquals(Double.POSITIVE_INFINITY, map.get(1), DELTA);
        assertEquals(Double.NEGATIVE_INFINITY, map.get(2), DELTA);

        assertTrue(map.containsValue(Double.POSITIVE_INFINITY));
        assertTrue(map.containsValue(Double.NEGATIVE_INFINITY));
    }

    @Test
    void testVerySmallValues() {
        map.put(1, Double.MIN_VALUE);
        map.put(2, Double.MIN_NORMAL);

        assertEquals(Double.MIN_VALUE, map.get(1), DELTA);
        assertEquals(Double.MIN_NORMAL, map.get(2), DELTA);
    }

    @Test
    void testVeryLargeValues() {
        map.put(1, Double.MAX_VALUE);
        map.put(2, -Double.MAX_VALUE);

        assertEquals(Double.MAX_VALUE, map.get(1), DELTA);
        assertEquals(-Double.MAX_VALUE, map.get(2), DELTA);
    }

    @Test
    void testFloatingPointPrecision() {
        double value1 = 0.1 + 0.2;
        double value2 = 0.3;

        map.put(1, value1);
        map.put(2, value2);

        // These are NOT equal in IEEE 754!
        assertNotEquals(value1, value2, 0.0);

        // Map should treat them as different values (bitwise comparison)
        assertNotEquals(map.get(1), map.get(2), 0.0);
    }

    @Test
    void testExactBitwiseEquality() {
        double value = Math.PI;
        map.put(1, value);
        map.put(2, value);

        // Same exact value should work
        assertEquals(value, map.get(1), 0.0);
        assertEquals(value, map.get(2), 0.0);
    }

    @Test
    void testForEachWithDoubles() {
        map.put(1, 1.5);
        map.put(2, 2.5);
        map.put(3, 3.5);

        Set<Integer> keys = new HashSet<>();
        Set<Double> values = new HashSet<>();

        map.forEachIntDouble((k, v) -> {
            keys.add(k);
            values.add(v);
        });

        assertEquals(3, keys.size());
        assertEquals(3, values.size());
        assertTrue(values.contains(1.5));
        assertTrue(values.contains(2.5));
        assertTrue(values.contains(3.5));
    }

    @Test
    void testIteratorWithDoubles() {
        map.put(1, 1.1);
        map.put(2, 2.2);
        map.put(3, 3.3);

        try (Int2DoubleHashMap.IntDoubleHashMapIterator iter = map.borrowIterator()) {
            int count = 0;
            while (iter.hasNext()) {
                int key = iter.nextKey();
                assertTrue(key >= 1 && key <= 3);
                count++;
            }
            assertEquals(3, count);
        }
    }

    @Test
    void testBoxedPutGetWithDoubles() {
        map.put(Integer.valueOf(1), Double.valueOf(1.5));
        map.put(Integer.valueOf(2), Double.valueOf(2.5));

        assertEquals(1.5, map.get(Integer.valueOf(1)), DELTA);
        assertEquals(2.5, map.get(Integer.valueOf(2)), DELTA);
        assertNull(map.get(Integer.valueOf(999)));
    }

    @Test
    void testBoxedNullValue() {
        assertThrows(NullPointerException.class, () -> map.put(1, null));
    }

    @Test
    void testValuesCollection() {
        map.put(1, 1.1);
        map.put(2, 2.2);
        map.put(3, 3.3);

        var values = map.values();
        assertEquals(3, values.size());

        Set<Double> valueSet = new HashSet<>();
        for (Double val : values) {
            valueSet.add(val);
        }

        assertEquals(3, valueSet.size());
        assertTrue(valueSet.contains(1.1));
        assertTrue(valueSet.contains(2.2));
        assertTrue(valueSet.contains(3.3));
    }

    @Test
    void testManyDoubleValues() {
        for (int i = 1; i <= 1000; i++) {
            map.put(i, i * 0.1);
        }

        assertEquals(1000, map.size());

        for (int i = 1; i <= 1000; i++) {
            assertEquals(i * 0.1, map.get(i), DELTA);
        }
    }

    @Test
    void testScientificNotation() {
        map.put(1, 1.23e-10);
        map.put(2, 4.56e10);
        map.put(3, -7.89e-5);

        assertEquals(1.23e-10, map.get(1), DELTA);
        assertEquals(4.56e10, map.get(2), DELTA);
        assertEquals(-7.89e-5, map.get(3), DELTA);
    }
}