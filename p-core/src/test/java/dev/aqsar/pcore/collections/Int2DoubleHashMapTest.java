package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Int2DoubleHashMap - integer keys to double values
 */
class Int2DoubleHashMapTest {

    private Int2DoubleHashMap map;
    private static final double DELTA = 1e-10;

    @BeforeEach
    void setUp() {
        map = Int2DoubleHashMap.builder().build();
    }

    // ==================== Basic Operations ====================

    @Test
    void testPutAndGet() {
        map.putIntDouble(1, 1.5);
        map.putIntDouble(2, 2.5);
        map.putIntDouble(3, 3.14159);

        assertEquals(1.5, map.getDouble(1), DELTA);
        assertEquals(2.5, map.getDouble(2), DELTA);
        assertEquals(3.14159, map.getDouble(3), DELTA);
    }

    @Test
    void testPutOverwrite() {
        map.putIntDouble(1, 1.5);
        map.putIntDouble(1, 9.99);

        assertEquals(9.99, map.getDouble(1), DELTA);
        assertEquals(1, map.size());
    }

    @Test
    void testGetMissing() {
        assertEquals(Int2DoubleHashMap.DEFAULT_NULL_VALUE, map.getDouble(999), DELTA);
    }

    @Test
    void testContainsValue() {
        map.putIntDouble(1, 1.5);
        map.putIntDouble(2, 2.5);

        assertTrue(map.containsDouble(1.5));
        assertTrue(map.containsDouble(2.5));
        assertFalse(map.containsDouble(3.14));
    }

    @Test
    void testRemove() {
        map.putIntDouble(1, 1.5);
        map.putIntDouble(2, 2.5);

        assertEquals(1.5, map.removeInt(1), DELTA);
        assertFalse(map.containsInt(1));
        assertEquals(1, map.size());
    }

    // ==================== Special Double Values ====================

    @Test
    void testZeroValues() {
        map.putIntDouble(1, 0.0);
        map.putIntDouble(2, -0.0);

        assertEquals(0.0, map.getDouble(1), DELTA);
        assertEquals(-0.0, map.getDouble(2), DELTA);

        // Note: +0.0 and -0.0 are different in bitwise comparison
        // but equal in == comparison
        assertTrue(map.containsDouble(0.0));
    }

    @Test
    void testNaNValues() {
        map.putIntDouble(1, Double.NaN);
        map.putIntDouble(2, Double.NaN);

        // All NaNs should be treated as equal (using doubleToLongBits for hash)
        assertTrue(Double.isNaN(map.getDouble(1)));
        assertTrue(map.containsDouble(Double.NaN));
        
        assertEquals(2, map.size()); // Two different keys
    }

    @Test
    void testInfinityValues() {
        map.putIntDouble(1, Double.POSITIVE_INFINITY);
        map.putIntDouble(2, Double.NEGATIVE_INFINITY);

        assertEquals(Double.POSITIVE_INFINITY, map.getDouble(1), DELTA);
        assertEquals(Double.NEGATIVE_INFINITY, map.getDouble(2), DELTA);

        assertTrue(map.containsDouble(Double.POSITIVE_INFINITY));
        assertTrue(map.containsDouble(Double.NEGATIVE_INFINITY));
    }

    @Test
    void testVerySmallValues() {
        map.putIntDouble(1, Double.MIN_VALUE);
        map.putIntDouble(2, Double.MIN_NORMAL);

        assertEquals(Double.MIN_VALUE, map.getDouble(1), DELTA);
        assertEquals(Double.MIN_NORMAL, map.getDouble(2), DELTA);
    }

    @Test
    void testVeryLargeValues() {
        map.putIntDouble(1, Double.MAX_VALUE);
        map.putIntDouble(2, -Double.MAX_VALUE);

        assertEquals(Double.MAX_VALUE, map.getDouble(1), DELTA);
        assertEquals(-Double.MAX_VALUE, map.getDouble(2), DELTA);
    }

    // ==================== Precision Testing ====================

    @Test
    void testFloatingPointPrecision() {
        double value1 = 0.1 + 0.2;
        double value2 = 0.3;

        map.putIntDouble(1, value1);
        map.putIntDouble(2, value2);

        // These are NOT equal in IEEE 754!
        assertNotEquals(value1, value2, 0.0);
        
        // Map should treat them as different values (bitwise comparison)
        assertNotEquals(map.getDouble(1), map.getDouble(2), 0.0);
    }

    @Test
    void testExactBitwiseEquality() {
        double value = Math.PI;
        map.putIntDouble(1, value);
        map.putIntDouble(2, value);

        // Same exact value should work
        assertEquals(value, map.getDouble(1), 0.0);
        assertEquals(value, map.getDouble(2), 0.0);
    }

    // ==================== Iteration Testing ====================

    @Test
    void testForEachWithDoubles() {
        map.putIntDouble(1, 1.5);
        map.putIntDouble(2, 2.5);
        map.putIntDouble(3, 3.5);

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
        map.putIntDouble(1, 1.1);
        map.putIntDouble(2, 2.2);
        map.putIntDouble(3, 3.3);

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

    // ==================== Boxed API with Doubles ====================

    @Test
    void testBoxedPutGetWithDoubles() {
        map.put(1, 1.5);
        map.put(2, 2.5);

        assertEquals(1.5, map.get(1), DELTA);
        assertEquals(2.5, map.get(2), DELTA);
        assertNull(map.get(999));
    }

    @Test
    void testBoxedNullValue() {
        map = Int2DoubleHashMap.builder().nullValue(Double.NaN).build();

        map.put(1, null);
        assertNull(map.get(1));
        assertTrue(map.containsValue(null));
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

    // ==================== Large Scale Testing ====================

    @Test
    void testManyDoubleValues() {
        for (int i = 1; i <= 1000; i++) {
            map.putIntDouble(i, i * 0.1);
        }

        assertEquals(1000, map.size());

        for (int i = 1; i <= 1000; i++) {
            assertEquals(i * 0.1, map.getDouble(i), DELTA);
        }
    }

    @Test
    void testScientificNotation() {
        map.putIntDouble(1, 1.23e-10);
        map.putIntDouble(2, 4.56e10);
        map.putIntDouble(3, -7.89e-5);

        assertEquals(1.23e-10, map.getDouble(1), DELTA);
        assertEquals(4.56e10, map.getDouble(2), DELTA);
        assertEquals(-7.89e-5, map.getDouble(3), DELTA);
    }
}