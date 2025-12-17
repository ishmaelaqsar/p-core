package dev.aqsar.pcore.number;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedPointNumberTest {

    private static final double DELTA = 0.0001; // Tolerance for double comparisons

    @Test
    void testValueOf_DefaultShift() {
        FixedPointNumber fp = FixedPointNumber.valueOf(1.0);
        assertEquals(FixedPointMath.DEFAULT_SHIFT, fp.getShift());
        assertEquals(1.0, fp.doubleValue(), DELTA);
    }

    @Test
    void testValueOf_ExplicitShift() {
        // Shift 8 means multiplier is 256
        FixedPointNumber fp = FixedPointNumber.valueOf(2.5, 8);
        assertEquals(8, fp.getShift());
        assertEquals(2.5, fp.doubleValue(), DELTA);
        // 2.5 * 256 = 640
        assertEquals(640, fp.getRawValue());
    }

    @Test
    void testWrap() {
        // Manually wrapping raw bits
        // Raw 512 with shift 8 (256) -> 512/256 = 2.0
        FixedPointNumber fp = FixedPointNumber.wrap(512, 8);
        assertEquals(2.0, fp.doubleValue(), DELTA);
        assertEquals(512, fp.getRawValue());
    }

    @Test
    void testInvalidShift() {
        assertThrows(IllegalArgumentException.class, () -> FixedPointNumber.valueOf(1.0, -1));
        assertThrows(IllegalArgumentException.class, () -> FixedPointNumber.valueOf(1.0, 63));
    }
    // --- Conversions ---

    @Test
    void testConversions() {
        // 55.125
        // int part = 55
        FixedPointNumber fp = FixedPointNumber.valueOf(55.125);
        assertEquals(55, fp.intValue());
        assertEquals(55L, fp.longValue());
        assertEquals(55.125f, fp.floatValue(), DELTA);
        assertEquals(55.125, fp.doubleValue(), DELTA);
        assertEquals("55.125", fp.toString());
    }

    @Test
    void testNegativeConversions() {
        FixedPointNumber fp = FixedPointNumber.valueOf(-10.5);
        assertEquals(-11, fp.intValue(), "Fixed-point uses fast flooring (shift right)");
        assertEquals(-10.5, fp.doubleValue(), DELTA);
    }
    // --- Mutable Arithmetic ---

    @Test
    void testPlus_SameShift() {
        FixedPointNumber num1 = FixedPointNumber.valueOf(10.0, 16);
        FixedPointNumber num2 = FixedPointNumber.valueOf(5.5, 16);
        // Mutates num1, returns num1
        FixedPointNumber result = num1.plus(num2);
        assertSame(num1, result, "Method should return 'this' for chaining");
        assertEquals(15.5, num1.doubleValue(), DELTA);
        assertEquals(5.5, num2.doubleValue(), DELTA, "Second operand should be unchanged");
    }

    @Test
    void testPlus_MixedShift() {
        // num1 has LESS precision (Shift 8, multiplier 256)
        // num2 has MORE precision (Shift 10, multiplier 1024)
        FixedPointNumber num1 = FixedPointNumber.valueOf(10.0, 8);
        FixedPointNumber num2 = FixedPointNumber.valueOf(0.5, 10);
        // num2 should be down-shifted to match num1
        num1.plus(num2);
        assertEquals(10.5, num1.doubleValue(), DELTA);
    }

    @Test
    void testSubtract() {
        FixedPointNumber num1 = FixedPointNumber.valueOf(10.0);
        FixedPointNumber num2 = FixedPointNumber.valueOf(3.5);
        num1.subtract(num2);
        assertEquals(6.5, num1.doubleValue(), DELTA);
    }

    @Test
    void testMultiply() {
        // 2.0 * 4.0 = 8.0
        FixedPointNumber num1 = FixedPointNumber.valueOf(2.0, 8);
        FixedPointNumber num2 = FixedPointNumber.valueOf(4.0, 8);
        num1.multiply(num2);
        assertEquals(8.0, num1.doubleValue(), DELTA);
    }

    @Test
    void testMultiply_Fractional() {
        // 0.5 * 0.5 = 0.25
        FixedPointNumber num1 = FixedPointNumber.valueOf(0.5, 10);
        FixedPointNumber num2 = FixedPointNumber.valueOf(0.5, 10);
        num1.multiply(num2);
        assertEquals(0.25, num1.doubleValue(), DELTA);
    }

    @Test
    void testDivide() {
        // 10.0 / 2.0 = 5.0
        FixedPointNumber num1 = FixedPointNumber.valueOf(10.0, 8);
        FixedPointNumber num2 = FixedPointNumber.valueOf(2.0, 8);
        num1.divide(num2);
        assertEquals(5.0, num1.doubleValue(), DELTA);
    }

    @Test
    void testDivide_FractionalResult() {
        // 1.0 / 4.0 = 0.25
        FixedPointNumber num1 = FixedPointNumber.valueOf(1.0, 10);
        FixedPointNumber num2 = FixedPointNumber.valueOf(4.0, 10);
        num1.divide(num2);
        assertEquals(0.25, num1.doubleValue(), DELTA);
    }

    @Test
    void testChaining() {
        FixedPointNumber num = FixedPointNumber.valueOf(2.0);
        // (2 + 3) * 4 = 20
        num.plus(FixedPointNumber.valueOf(3.0))
            .multiply(FixedPointNumber.valueOf(4.0));
        assertEquals(20.0, num.doubleValue(), DELTA);
    }

    @Test
    void testSetRawValue() {
        FixedPointNumber num = FixedPointNumber.valueOf(1.0, 8);
        assertEquals(256, num.getRawValue()); // 1.0 * 256

        // Manually change raw value to 512 (2.0)
        num.setRawValue(512);

        assertEquals(2.0, num.doubleValue(), DELTA);
    }

    @Test
    void testEqualsAndHashCode() {
        FixedPointNumber n1 = FixedPointNumber.valueOf(5.0, 8);
        FixedPointNumber n2 = FixedPointNumber.valueOf(5.0, 8);
        FixedPointNumber n3 = FixedPointNumber.valueOf(5.0, 16); // Different shift
        // Test Equality
        assertEquals(n1, n2);
        assertNotEquals(n1, n3, "Different shifts should not be equal even if values represent same double");
        // Test HashCode matches
        assertEquals(n1.hashCode(), n2.hashCode());
        assertNotEquals(n1.hashCode(), n3.hashCode());
        // Test Mutation affects equality
        n1.plus(FixedPointNumber.valueOf(1.0, 8)); // n1 becomes 6.0
        assertNotEquals(n1, n2, "Mutated object should no longer be equal to original value");
    }

    @Test
    void testCompareTo() {
        FixedPointNumber small = FixedPointNumber.valueOf(1.0);
        FixedPointNumber big = FixedPointNumber.valueOf(10.0);
        FixedPointNumber smallDiffShift = FixedPointNumber.valueOf(1.0, 4);
        assertTrue(small.compareTo(big) < 0);
        assertTrue(big.compareTo(small) > 0);
        assertEquals(0, small.compareTo(small));
        // Compare different shifts (should compare underlying double values)
        // 1.0 (shift 16) vs 1.0 (shift 4) -> equal value
        assertEquals(0, small.compareTo(smallDiffShift));
    }
}
