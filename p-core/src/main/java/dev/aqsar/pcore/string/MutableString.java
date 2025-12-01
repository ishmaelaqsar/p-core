package dev.aqsar.pcore.string;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;

public interface MutableString extends CharSequence {

    long[] POWERS_OF_TEN = {1L,
                            10L,
                            100L,
                            1000L,
                            10000L,
                            100000L,
                            1000000L,
                            10000000L,
                            100000000L,
                            1000000000L,
                            10000000000L,
                            100000000000L,
                            1000000000000L,
                            10000000000000L,
                            100000000000000L,
                            1000000000000000L,
                            10000000000000000L,
                            100000000000000000L,
                            1000000000000000000L};

    int DEFAULT_PRECISION = 5;
    byte[] TRUE_BYTES = "true".getBytes(StandardCharsets.US_ASCII);
    byte[] FALSE_BYTES = "false".getBytes(StandardCharsets.US_ASCII);
    byte[] NULL_BYTES = "null".getBytes(StandardCharsets.US_ASCII);
    byte[] INT_MIN_BYTES = String.valueOf(Integer.MIN_VALUE).getBytes(StandardCharsets.US_ASCII);
    byte[] LONG_MIN_BYTES = String.valueOf(Long.MIN_VALUE).getBytes(StandardCharsets.US_ASCII);
    byte[] NAN_BYTES = "NaN".getBytes(StandardCharsets.US_ASCII);
    byte[] INF_BYTES = "Infinity".getBytes(StandardCharsets.US_ASCII);

    static int stringSize(final int i) {
        for (int d = 0; d < 10; d++) {
            if (i < POWERS_OF_TEN[d + 1]) return d + 1;
        }
        return 10;
    }

    static int stringSize(final long l) {
        for (int d = 0; d < 18; d++) {
            if (l < POWERS_OF_TEN[d + 1]) return d + 1;
        }
        return 19;
    }

    static int stringSize(final float f, final int precision) {
        final long multiplier = POWERS_OF_TEN[precision];
        long iPart = (long) f;
        final float remainder = f - iPart;
        final long fPart = (long) (remainder * multiplier + 0.5f);
        if (fPart >= multiplier) {
            iPart++; // Rounding rolled over (e.g. 0.99 -> 1.0)
        }
        final int dot = (precision > 0) ? 1 : 0;
        return stringSize(iPart) + dot + precision;
    }

    static int stringSize(final double d, final int precision) {
        final long multiplier = POWERS_OF_TEN[precision];
        long iPart = (long) d;
        final double remainder = d - iPart;
        final long fPart = (long) (remainder * multiplier + 0.5);
        if (fPart >= multiplier) {
            iPart++; // Rounding rolled over (e.g. 9.9 -> 10.0)
        }
        final int dot = (precision > 0) ? 1 : 0;
        return stringSize(iPart) + dot + precision;
    }

    static void getChars(final float f, final int index, final int precision, final byte[] buf) {
        getChars((double) f, index, precision, buf);
    }

    static void getChars(final double d, final int index, final int precision, final byte[] buf) {
        final long multiplier = POWERS_OF_TEN[precision];
        long iPart = (long) d;
        final double remainder = d - iPart;
        long fPart = (long) (remainder * multiplier + 0.5);
        if (fPart >= multiplier) {
            fPart = 0;
            iPart++;
        }
        int charPos = index;
        for (int i = 0; i < precision; i++) {
            final long q = fPart / 10;
            final int r = (int) (fPart - ((q << 3) + (q << 1)));
            buf[--charPos] = (byte) ('0' + r);
            fPart = q;
        }
        if (precision > 0) {
            buf[--charPos] = '.';
        }
        getChars(iPart, charPos, buf);
    }

    static void getChars(long i, int index, final byte[] buf) {
        long q;
        int r;
        int charPos = index;
        while (i > Integer.MAX_VALUE) {
            q = i / 100;
            r = (int) (i - ((q << 6) + (q << 5) + (q << 2)));
            i = q;
            buf[--charPos] = (byte) ('0' + r % 10);
            buf[--charPos] = (byte) ('0' + r / 10);
        }
        getChars((int) i, charPos, buf);
    }

    static void getChars(int i, int index, final byte[] buf) {
        int q, r;
        int charPos = index;
        while (i >= 65536) {
            q = i / 100;
            r = i - ((q << 6) + (q << 5) + (q << 2));
            i = q;
            buf[--charPos] = (byte) ('0' + r % 10);
            buf[--charPos] = (byte) ('0' + r / 10);
        }
        do {
            q = (i * 52429) >>> (16 + 3);
            r = i - ((q << 3) + (q << 1));
            buf[--charPos] = (byte) ('0' + r);
            i = q;
        } while (i != 0);
    }

    void clear();

    void setLength(final int newLength);

    MutableString append(final boolean b);

    MutableString append(final char c);

    MutableString append(final int i);

    MutableString append(final long l);

    MutableString append(final float f);

    MutableString append(final float f, final int precision);

    MutableString append(final double d);

    MutableString append(final double d, final int precision);

    MutableString append(final CharSequence s);

    MutableString append(final CharSequence s, final int start, final int end);

    MutableString copyOf(@Nullable final CharSequence cs);

    int getByteLength();
}
