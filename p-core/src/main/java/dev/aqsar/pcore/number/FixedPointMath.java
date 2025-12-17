package dev.aqsar.pcore.number;

/**
 * A comprehensive utility for stateless, allocation-free Fixed-Point arithmetic.
 * <p>
 * This class uses binary fixed-point arithmetic, where the multiplier is a power of two (2^n).
 * To prevent overflow during multiplication and division, intermediate calculations
 * are performed using double-precision floating point.
 * </p>
 */
public final class FixedPointMath {

    /**
     * The default number of fractional bits (precision) used when no specific shift is provided.
     * 16 bits provides a precision of ~0.000015.
     */
    public static final int DEFAULT_SHIFT = 16;

    /**
     * The multiplier corresponding to the {@link #DEFAULT_SHIFT}.
     */
    public static final int DEFAULT_MULTIPLIER = 1 << DEFAULT_SHIFT;

    // LUT constants for Trigonometry (~0.08 degree precision)
    private static final int LUT_SIZE = 4096;
    private static final int LUT_MASK = LUT_SIZE - 1;
    private static final long[] SIN_LUT = new long[LUT_SIZE];
    private static final double INDEX_PI_MUL = LUT_SIZE / (2.0 * Math.PI);

    static {
        // Pre-compute sine values for the LUT at default precision
        for (int i = 0; i < LUT_SIZE; i++) {
            final double angle = (i * 2.0 * Math.PI) / LUT_SIZE;
            SIN_LUT[i] = (long) Math.scalb(Math.sin(angle), DEFAULT_SHIFT);
        }
    }

    private FixedPointMath() {
        throw new AssertionError();
    }

    /**
     * Converts a long integer to a fixed-point long using {@link #DEFAULT_SHIFT}.
     *
     * @param l the long value to convert
     * @return raw fixed-point representation
     */
    public static long toFixedPoint(final long l) {
        return l << DEFAULT_SHIFT;
    }

    /**
     * Converts a long integer to a fixed-point long with a specific shift.
     *
     * @param l     the long value
     * @param shift fractional bits
     * @return raw fixed-point representation
     */
    public static long toFixedPoint(final long l, final int shift) {
        return l << shift;
    }

    /**
     * Converts a double to a fixed-point long using {@link #DEFAULT_SHIFT}.
     *
     * @param d the double value
     * @return raw fixed-point representation
     */
    public static long toFixedPoint(final double d) {
        return (long) Math.scalb(d, DEFAULT_SHIFT);
    }

    /**
     * Converts a double to a fixed-point long with a specific shift.
     *
     * @param d     the double value
     * @param shift fractional bits
     * @return raw fixed-point representation
     */
    public static long toFixedPoint(final double d, final int shift) {
        return (long) Math.scalb(d, shift);
    }

    /**
     * Converts raw fixed-point to a long (truncating fractional part) using {@link #DEFAULT_SHIFT}.
     *
     * @param fixedPoint raw bits
     * @return integer part
     */
    public static long toLong(final long fixedPoint) {
        return fixedPoint >> DEFAULT_SHIFT;
    }

    /**
     * Converts a raw fixed-point value to a standard long integer.
     * <p>
     * This operation uses an arithmetic shift right, which results in
     * <strong>flooring</strong> (rounding toward negative infinity).
     * </p>
     *
     * @param fixedPoint the raw fixed-point value
     * @param shift      the fractional bits to discard
     * @return the floored integer part
     */
    public static long toLong(final long fixedPoint, final int shift) {
        return fixedPoint >> shift;
    }

    /**
     * Converts raw fixed-point back to a double using {@link #DEFAULT_SHIFT}.
     *
     * @param fixedPoint raw bits
     * @return double representation
     */
    public static double toDouble(final long fixedPoint) {
        return Math.scalb((double) fixedPoint, -DEFAULT_SHIFT);
    }

    /**
     * Converts raw fixed-point back to a double.
     *
     * @param fixedPoint raw bits
     * @param shift      fractional bits used for encoding
     * @return double representation
     */
    public static double toDouble(final long fixedPoint, final int shift) {
        return Math.scalb((double) fixedPoint, -shift);
    }

    /**
     * Adds two fixed-point numbers, aligning precision if shifts differ.
     *
     * @return result in the precision of aShift
     */
    public static long add(final long a, final int aShift, final long b, final int bShift) {
        if (aShift == bShift) return a + b;
        return a + align(b, bShift, aShift);
    }

    /**
     * Subtracts two fixed-point numbers, aligning precision if shifts differ.
     *
     * @return result in the precision of aShift
     */
    public static long subtract(final long a, final int aShift, final long b, final int bShift) {
        if (aShift == bShift) return a - b;
        return a - align(b, bShift, aShift);
    }

    /**
     * Multiplies two fixed-point numbers. Uses double-precision intermediate to prevent overflow.
     *
     * @param shift bits to normalize the product by
     * @return product in the specified shift
     */
    public static long multiply(final long a, final long b, final int shift) {
        return (long) (((double) a * b) / (1L << shift));
    }

    /**
     * Divides two fixed-point numbers. Uses double-precision intermediate to prevent overflow.
     *
     * @param shift bits to preserve precision
     * @return quotient in the specified shift
     */
    public static long divide(final long a, final long b, final int shift) {
        return (long) (((double) a * (1L << shift)) / b);
    }

    /**
     * Adds an integer to a fixed-point number.
     */
    public static long add(final long fixed, final long integer, final int shift) {
        return fixed + (integer << shift);
    }

    /**
     * Adds a double to a fixed-point number.
     */
    public static long add(final long fixed, final double d, final int shift) {
        return fixed + toFixedPoint(d, shift);
    }

    /**
     * Multiplies fixed-point by an integer scalar.
     */
    public static long multiply(final long fixed, final long integer) {
        return fixed * integer;
    }

    /**
     * Multiplies fixed-point by a double scalar.
     */
    public static long multiply(final long fixed, final double d) {
        return (long) (fixed * d);
    }

    /**
     * Divides fixed-point by an integer divisor.
     */
    public static long divide(final long fixed, final long integer) {
        return fixed / integer;
    }

    /**
     * Divides fixed-point by a double divisor.
     */
    public static long divide(final long fixed, final double d) {
        return (long) (fixed / d);
    }

    /**
     * Calculates Sine using a fast Look-Up Table (LUT).
     *
     * @param rawValue angle in radians
     * @param shift    precision of the input
     * @return sine result in the same shift
     */
    public static long sin(final long rawValue, final int shift) {
        final double rads = toDouble(rawValue, shift);
        final int index = (int) Math.round(rads * INDEX_PI_MUL) & LUT_MASK;
        long result = SIN_LUT[index];
        return (shift == DEFAULT_SHIFT) ? result : align(result, DEFAULT_SHIFT, shift);
    }

    /**
     * Calculates Cosine using a fast Look-Up Table (LUT).
     *
     * @param rawValue angle in radians
     * @param shift    precision of the input
     * @return cosine result in the same shift
     */
    public static long cos(final long rawValue, final int shift) {
        final long halfPi = toFixedPoint(1.5707963267948966, shift);
        return sin(rawValue + halfPi, shift);
    }

    /**
     * Calculates the Square Root.
     * Uses double conversion internally for accuracy across any shift.
     *
     * @throws ArithmeticException if input is negative
     */
    public static long sqrt(final long rawValue, final int shift) {
        if (rawValue < 0) throw new ArithmeticException("Sqrt of negative number");
        return toFixedPoint(Math.sqrt(toDouble(rawValue, shift)), shift);
    }

    /**
     * Scales a raw value from one bit-shift to another.
     *
     * @param value       raw bits
     * @param sourceShift current fractional bits
     * @param targetShift desired fractional bits
     * @return rescaled raw bits
     */
    public static long align(final long value, final int sourceShift, final int targetShift) {
        if (sourceShift == targetShift) return value;
        return (sourceShift > targetShift) ? value >> (sourceShift - targetShift) : value << (targetShift - sourceShift);
    }
}
