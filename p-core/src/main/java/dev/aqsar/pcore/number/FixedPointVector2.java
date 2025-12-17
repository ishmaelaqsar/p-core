package dev.aqsar.pcore.number;

/**
 * 2D Vector using Mutable FixedPointNumbers.
 */
public final class FixedPointVector2 {

    public final FixedPointNumber x;
    public final FixedPointNumber y;

    public FixedPointVector2(final int shift) {
        this.x = FixedPointNumber.valueOf(0, shift);
        this.y = FixedPointNumber.valueOf(0, shift);
    }

    public FixedPointVector2(final double x, final double y, final int shift) {
        this.x = FixedPointNumber.valueOf(x, shift);
        this.y = FixedPointNumber.valueOf(y, shift);
    }

    /**
     * In-place addition.
     */
    public FixedPointVector2 add(final FixedPointVector2 other) {
        x.plus(other.x);
        y.plus(other.y);
        return this;
    }

    /**
     * In-place scalar multiplication.
     */
    public FixedPointVector2 scale(final FixedPointNumber scalar) {
        x.multiply(scalar);
        y.multiply(scalar);
        return this;
    }

    /**
     * Calculates magnitude using the FixedPointMath utility.
     * <p>
     * This implementation uses intermediate double precision to prevent 64-bit overflow
     * during the squaring process (x*x + y*y), ensuring accuracy for large coordinates.
     * </p>
     *
     * @param resultContainer The FixedPointNumber instance to store the result in.
     */
    public void magnitude(final FixedPointNumber resultContainer) {
        final long xRaw = x.getRawValue();
        final long yRaw = y.getRawValue();
        final int shift = x.getShift();
        // Calculate magnitude: sqrt(x^2 + y^2)
        // We use FixedPointMath.sqrt which handles the double conversion safely
        // to avoid overflow before the square root is taken.
        final double xVal = FixedPointMath.toDouble(xRaw, shift);
        final double yVal = FixedPointMath.toDouble(yRaw, shift);
        final double mag = Math.sqrt(xVal * xVal + yVal * yVal);
        resultContainer.setRawValue(FixedPointMath.toFixedPoint(mag, shift));
    }

    @Override
    public String toString() {
        return String.format("[%s, %s]", x, y);
    }
}
