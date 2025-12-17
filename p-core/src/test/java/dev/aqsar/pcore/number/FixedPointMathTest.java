package dev.aqsar.pcore.number;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixedPointMathTest {

    // LUT precision is ~0.08 degrees, so we need a lenient delta for trig tests.
    // 0.005 is roughly 0.28 degrees error margin, safe for 4096-entry LUT.
    private static final double TRIG_DELTA = 0.005;
    private static final double SQRT_DELTA = 0.0001;

    @Nested
    @DisplayName("Trigonometry")
    class TrigonometryTests {

        @Test
        void sinZero() {
            long zero = FixedPointMath.toFixedPoint(0.0);
            long result = FixedPointMath.sin(zero, FixedPointMath.DEFAULT_SHIFT);
            assertEquals(0, result);
        }

        @Test
        void sinPiOver2() {
            long halfPi = FixedPointMath.toFixedPoint(Math.PI / 2.0);
            long result = FixedPointMath.sin(halfPi, FixedPointMath.DEFAULT_SHIFT);
            double resultDouble = FixedPointMath.toDouble(result);
            assertEquals(1.0, resultDouble, TRIG_DELTA);
        }

        @Test
        void sinPi() {
            long pi = FixedPointMath.toFixedPoint(Math.PI);
            long result = FixedPointMath.sin(pi, FixedPointMath.DEFAULT_SHIFT);
            double resultDouble = FixedPointMath.toDouble(result);
            assertEquals(0.0, resultDouble, TRIG_DELTA);
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.1, 0.5, 1.0, 2.5, 3.14, 4.0, -1.0})
        void sinGeneralValues(double angle) {
            long raw = FixedPointMath.toFixedPoint(angle);
            long result = FixedPointMath.sin(raw, FixedPointMath.DEFAULT_SHIFT);
            double resultDouble = FixedPointMath.toDouble(result);
            assertEquals(Math.sin(angle), resultDouble, TRIG_DELTA);
        }

        @Test
        void cosConsistency() {
            // cos(0) should be 1
            long zero = FixedPointMath.toFixedPoint(0.0);
            long result = FixedPointMath.cos(zero, FixedPointMath.DEFAULT_SHIFT);
            double resultDouble = FixedPointMath.toDouble(result);
            assertEquals(1.0, resultDouble, TRIG_DELTA);
        }

        @Test
        void handlesNonDefaultShift() {
            // Test with Q8.8 (shift 8)
            int shift = 8;
            long halfPi = FixedPointMath.toFixedPoint(Math.PI / 2.0, shift);
            long result = FixedPointMath.sin(halfPi, shift);
            double resultDouble = FixedPointMath.toDouble(result, shift);
            assertEquals(1.0, resultDouble, TRIG_DELTA);
            // Raw value for 1.0 at shift 8 is 256
            assertEquals(256, result, "Should match expected integer representation for 1.0 in Q8");
        }
    }

    @Nested
    @DisplayName("Square Root")
    class SqrtTests {

        @Test
        void sqrtPerfectSquares() {
            long input = FixedPointMath.toFixedPoint(4.0);
            long result = FixedPointMath.sqrt(input, FixedPointMath.DEFAULT_SHIFT);
            double d = FixedPointMath.toDouble(result);
            assertEquals(2.0, d, SQRT_DELTA);
        }

        @Test
        void sqrtNonPerfect() {
            long input = FixedPointMath.toFixedPoint(2.0);
            long result = FixedPointMath.sqrt(input, FixedPointMath.DEFAULT_SHIFT);
            double d = FixedPointMath.toDouble(result);
            assertEquals(Math.sqrt(2.0), d, SQRT_DELTA);
        }

        @Test
        void sqrtWithDifferentShift() {
            // Q4.12 shift
            int shift = 12;
            long input = FixedPointMath.toFixedPoint(9.0, shift);
            long result = FixedPointMath.sqrt(input, shift);
            double d = FixedPointMath.toDouble(result, shift);
            assertEquals(3.0, d, SQRT_DELTA);
        }

        @Test
        void throwsOnNegative() {
            long negative = FixedPointMath.toFixedPoint(-4.0);
            assertThrows(ArithmeticException.class, () ->
                FixedPointMath.sqrt(negative, FixedPointMath.DEFAULT_SHIFT)
            );
        }
    }
}
