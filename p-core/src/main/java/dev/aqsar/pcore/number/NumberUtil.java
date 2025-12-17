package dev.aqsar.pcore.number;

public final class NumberUtil {

    private NumberUtil() {
        throw new AssertionError();
    }

    public static boolean isPowerOfTwo(final int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
}
