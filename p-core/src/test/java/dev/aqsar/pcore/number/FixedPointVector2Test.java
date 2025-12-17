package dev.aqsar.pcore.number;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedPointVector2Test {

    private static final int SHIFT = 16;
    private static final double DELTA = 0.0001;

    @Test
    void initialization() {
        FixedPointVector2 v = new FixedPointVector2(1.5, 2.5, SHIFT);
        assertEquals(1.5, v.x.doubleValue(), DELTA);
        assertEquals(2.5, v.y.doubleValue(), DELTA);
    }

    @Test
    void addInPlace() {
        FixedPointVector2 v1 = new FixedPointVector2(1.0, 2.0, SHIFT);
        FixedPointVector2 v2 = new FixedPointVector2(0.5, 0.5, SHIFT);
        FixedPointVector2 returned = v1.add(v2);
        assertSame(v1, returned, "Should return 'this' for chaining");
        assertEquals(1.5, v1.x.doubleValue(), DELTA);
        assertEquals(2.5, v1.y.doubleValue(), DELTA);
        // v2 should be unchanged
        assertEquals(0.5, v2.x.doubleValue(), DELTA);
    }

    @Test
    void scaleInPlace() {
        FixedPointVector2 v = new FixedPointVector2(2.0, 3.0, SHIFT);
        FixedPointNumber scalar = FixedPointNumber.valueOf(2.0, SHIFT);
        v.scale(scalar);
        assertEquals(4.0, v.x.doubleValue(), DELTA);
        assertEquals(6.0, v.y.doubleValue(), DELTA);
    }

    @Test
    void magnitude() {
        // 3-4-5 triangle
        FixedPointVector2 v = new FixedPointVector2(3.0, 4.0, SHIFT);
        FixedPointNumber result = FixedPointNumber.valueOf(0, SHIFT);
        v.magnitude(result);
        assertEquals(5.0, result.doubleValue(), DELTA);
        assertEquals(SHIFT, result.getShift());
    }

    @Test
    void chainingOperations() {
        FixedPointVector2 v = new FixedPointVector2(1.0, 1.0, SHIFT);
        FixedPointVector2 other = new FixedPointVector2(1.0, 1.0, SHIFT);
        FixedPointNumber scalar = FixedPointNumber.valueOf(2.0, SHIFT);
        // (1,1) + (1,1) = (2,2) -> scale(2) = (4,4)
        v.add(other).scale(scalar);
        assertEquals(4.0, v.x.doubleValue(), DELTA);
        assertEquals(4.0, v.y.doubleValue(), DELTA);
    }

    @Test
    void toStringFormat() {
        FixedPointVector2 v = new FixedPointVector2(1.5, 2.5, SHIFT);
        String str = v.toString();
        // Assuming FixedPointNumber.toString returns Double.toString()
        assertTrue(str.contains("1.5"));
        assertTrue(str.contains("2.5"));
    }
}
