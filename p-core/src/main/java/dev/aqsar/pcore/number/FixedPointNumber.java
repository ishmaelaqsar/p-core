package dev.aqsar.pcore.number;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.Serial;
import java.io.Serializable;

/**
 * A mutable, high-performance wrapper for a fixed-point number.
 * <p>
 * This class couples a raw fixed-point value with its specific precision (shift).
 * It delegates all arithmetic operations to the stateless {@link FixedPointMath} utility,
 * allowing this class to act as an Object-Oriented view over raw integer math.
 * </p>
 * <p>
 * <strong>Mutability:</strong> Operations like {@link #plus(FixedPointNumber)} mutate
 * the instance in-place to avoid allocation overhead.
 * </p>
 */
@SuppressWarnings("UnusedReturnValue")
@NotThreadSafe
public final class FixedPointNumber extends Number implements Comparable<FixedPointNumber>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The number of fractional bits (precision) of this number.
     */
    private final int shift;

    /**
     * The underlying raw integer representation.
     */
    private long rawValue;

    private FixedPointNumber(final long rawValue, final int shift) {
        if (shift < 0 || shift > 62) {
            throw new IllegalArgumentException("Shift must be between 0 and 62");
        }
        this.rawValue = rawValue;
        this.shift = shift;
    }

    /**
     * Creates a new instance from a double using the default 16-bit precision.
     *
     * @param value the double value
     * @return a new FixedPointNumber
     */
    public static FixedPointNumber valueOf(final double value) {
        return valueOf(value, FixedPointMath.DEFAULT_SHIFT);
    }

    /**
     * Creates a new instance from a double with the specified precision.
     *
     * @param value the double value
     * @param shift the number of fractional bits
     * @return a new FixedPointNumber
     */
    public static FixedPointNumber valueOf(final double value, final int shift) {
        return new FixedPointNumber(FixedPointMath.toFixedPoint(value, shift), shift);
    }

    /**
     * Wraps an existing raw fixed-point value.
     * <p>Use this only if you have a pre-calculated raw value.</p>
     *
     * @param rawValue the raw integer bits
     * @param shift    the precision of the raw value
     * @return a new FixedPointNumber
     */
    public static FixedPointNumber wrap(final long rawValue, final int shift) {
        return new FixedPointNumber(rawValue, shift);
    }

    /**
     * Adds another fixed-point number to this one.
     * <p>Mutates this instance in-place.</p>
     *
     * @param other the value to add
     * @return this instance (for chaining)
     */
    public FixedPointNumber plus(final FixedPointNumber other) {
        this.rawValue = FixedPointMath.add(this.rawValue, this.shift, other.rawValue, other.shift);
        return this;
    }

    /**
     * Adds a standard integer to this fixed-point number.
     * <p>Mutates this instance in-place.</p>
     *
     * @param integer the integer to add
     * @return this instance (for chaining)
     */
    public FixedPointNumber plus(final int integer) {
        rawValue = FixedPointMath.add(rawValue, integer, shift);
        return this;
    }

    /**
     * Adds a standard double to this fixed-point number.
     * <p>Mutates this instance in-place.</p>
     *
     * @param value the double to add
     * @return this instance (for chaining)
     */
    public FixedPointNumber plus(final double value) {
        rawValue = FixedPointMath.add(rawValue, value, shift);
        return this;
    }

    /**
     * Subtracts another fixed-point number from this one.
     * <p>Mutates this instance in-place.</p>
     *
     * @param other the value to subtract
     * @return this instance (for chaining)
     */
    public FixedPointNumber subtract(final FixedPointNumber other) {
        this.rawValue = FixedPointMath.subtract(this.rawValue, this.shift, other.rawValue, other.shift);
        return this;
    }

    /**
     * Multiplies this number by another fixed-point number.
     * <p>Mutates this instance in-place.</p>
     *
     * @param other the multiplier
     * @return this instance (for chaining)
     */
    public FixedPointNumber multiply(final FixedPointNumber other) {
        // If other.shift != this.shift, strict fixed-point mul is complex.
        // Here we align 'other' to our shift for consistency before multiplying.
        final long otherRaw = other.shift == this.shift ? other.rawValue : FixedPointMath.add(0, this.shift, other.rawValue, other.shift);
        this.rawValue = FixedPointMath.multiply(this.rawValue, otherRaw, this.shift);
        return this;
    }

    /**
     * Multiplies this number by a standard integer scalar.
     * <p>Mutates this instance in-place.</p>
     *
     * @param scalar the integer multiplier
     * @return this instance (for chaining)
     */
    public FixedPointNumber multiply(final int scalar) {
        rawValue = FixedPointMath.multiply(rawValue, scalar);
        return this;
    }

    /**
     * Divides this number by another fixed-point number.
     * <p>Mutates this instance in-place.</p>
     *
     * @param other the divisor
     * @return this instance (for chaining)
     */
    public FixedPointNumber divide(final FixedPointNumber other) {
        // Align 'other' to our shift first to ensure units match
        final long otherRaw = other.shift == this.shift ? other.rawValue : FixedPointMath.add(0, this.shift, other.rawValue, other.shift);
        this.rawValue = FixedPointMath.divide(this.rawValue, otherRaw, this.shift);
        return this;
    }

    /**
     * Divides this number by a standard integer divisor.
     * <p>Mutates this instance in-place.</p>
     *
     * @param scalar the integer divisor
     * @return this instance (for chaining)
     */
    public FixedPointNumber divide(final int scalar) {
        rawValue = FixedPointMath.divide(rawValue, scalar);
        return this;
    }

    /**
     * Returns the value of this number as an {@code int}.
     * <p>This involves truncation (rounding towards zero).</p>
     */
    @Override
    public int intValue() {
        return (int) longValue();
    }

    /**
     * Returns the value of this number as a {@code long}.
     * <p>This involves truncation (rounding towards zero).</p>
     */
    @Override
    public long longValue() {
        return FixedPointMath.toLong(rawValue, shift);
    }

    /**
     * Returns the value of this number as a {@code float}.
     */
    @Override
    public float floatValue() {
        return (float) doubleValue();
    }

    /**
     * Returns the value of this number as a {@code double}.
     */
    @Override
    public double doubleValue() {
        return FixedPointMath.toDouble(rawValue, shift);
    }

    /**
     * Gets the raw underlying integer bits of this number.
     *
     * @return the raw value
     */
    public long getRawValue() {
        return rawValue;
    }

    /**
     * Manually updates the raw value.
     * <p>
     * <strong>Warning:</strong> Use with caution. Ensure the provided raw value
     * matches the current {@link #getShift()}.
     * </p>
     *
     * @param rawValue the new raw bits
     */
    public void setRawValue(long rawValue) {
        this.rawValue = rawValue;
    }

    /**
     * Gets the bit shift (precision) of this number.
     *
     * @return the shift amount
     */
    public int getShift() {
        return shift;
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(rawValue);
        result = 31 * result + shift;
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FixedPointNumber other)) return false;
        return this.rawValue == other.rawValue && this.shift == other.shift;
    }

    @Override
    public String toString() {
        return Double.toString(doubleValue());
    }

    @Override
    public int compareTo(final FixedPointNumber other) {
        if (this.shift == other.shift) {
            return Long.compare(this.rawValue, other.rawValue);
        }
        return Double.compare(this.doubleValue(), other.doubleValue());
    }
}
