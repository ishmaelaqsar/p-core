package dev.aqsar.pcore.string;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Unsafe-optimized ASCII string builder.
 */
@NotThreadSafe
@SuppressWarnings("restriction")
public final class AsciiString implements MutableString {

    private static final sun.misc.Unsafe UNSAFE;
    private static final long BYTE_ARRAY_OFFSET;

    static {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) f.get(null);
            BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] buffer;
    private int length;
    private int hash;

    public AsciiString() {
        this(64);
    }

    public AsciiString(final int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Negative capacity");
        }
        buffer = new byte[initialCapacity];
        length = 0;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public char charAt(final int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException();
        }
        return (char) (buffer[index] & 0xFF);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        if (start < 0 || end < start || end > length) {
            throw new IndexOutOfBoundsException();
        }
        return new String(buffer, start, end - start, StandardCharsets.US_ASCII);
    }

    public byte[] getRawBytes() {
        return buffer;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(buffer, length);
    }

    @Override
    public void clear() {
        length = 0;
        hash = 0;
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0 && length > 0) {
            for (int i = 0; i < length; i++) {
                h = 31 * h + (buffer[i] & 0xFF);
            }
            hash = h;
        }
        return h;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AsciiString a)) {
            return false;
        }
        if (length != a.length) {
            return false;
        }
        if (hash != 0 && a.hash != 0 && hash != a.hash) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (buffer[i] != a.buffer[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return new String(buffer, 0, length, StandardCharsets.US_ASCII);
    }

    @Override
    public void setLength(final int newLength) {
        if (newLength < 0) {
            throw new IllegalArgumentException("Negative length");
        }
        ensureCapacity(newLength);
        if (newLength > length) {
            Arrays.fill(buffer, length, newLength, (byte) 0);
        }
        length = newLength;
        hash = 0;
    }

    @Override
    public MutableString append(final char c) {
        ensureCapacity(length + 1);
        buffer[length++] = (byte) c;
        hash = 0;
        return this;
    }

    @Override
    public MutableString append(@Nullable final CharSequence s) {
        if (s == null) {
            return appendBytes(NULL_BYTES);
        }
        return append(s, 0, s.length());
    }

    @Override
    public MutableString append(@Nullable final CharSequence s, final int start, final int end) {
        if (s == null) {
            return appendBytes(NULL_BYTES);
        }
        if (start < 0 || end < start || end > s.length()) {
            throw new IndexOutOfBoundsException();
        }
        final int len = end - start;
        if (len == 0) {
            return this;
        }
        ensureCapacity(length + len);
        if (s instanceof final AsciiString ascii) {
            UNSAFE.copyMemory(ascii.buffer, BYTE_ARRAY_OFFSET + start, buffer, BYTE_ARRAY_OFFSET + length, len);
            length += len;
            hash = 0;
            return this;
        }
        if (s instanceof final String str) {
            // String.getBytes(US_ASCII) allocates, so loop.
            for (int i = start; i < end; i++) {
                buffer[length++] = (byte) str.charAt(i);
            }
            hash = 0;
            return this;
        }
        for (int i = start; i < end; i++) {
            buffer[length++] = (byte) s.charAt(i);
        }
        hash = 0;
        return this;
    }

    @Override
    public MutableString copyOf(@Nullable final CharSequence cs) {
        if (cs == null) {
            clear();
            return this;
        }
        if (cs instanceof final AsciiString a) {
            ensureCapacity(a.length);
            UNSAFE.copyMemory(a.buffer, BYTE_ARRAY_OFFSET, buffer, BYTE_ARRAY_OFFSET, a.length);
            length = a.length;
            hash = 0;
            return this;
        }
        if (cs instanceof final String s) {
            final byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
            ensureCapacity(bytes.length);
            UNSAFE.copyMemory(bytes, BYTE_ARRAY_OFFSET, buffer, BYTE_ARRAY_OFFSET, bytes.length);
            length = bytes.length;
            hash = 0;
            return this;
        }
        clear();
        append(cs);
        return this;
    }

    private MutableString appendBytes(final byte[] src) {
        ensureCapacity(length + src.length);
        UNSAFE.copyMemory(src, BYTE_ARRAY_OFFSET, buffer, BYTE_ARRAY_OFFSET + length, src.length);
        length += src.length;
        hash = 0;
        return this;
    }

    @Override
    public MutableString append(final int i) {
        if (i == Integer.MIN_VALUE) {
            return appendBytes(INT_MIN_BYTES);
        }
        final int value;
        if (i < 0) {
            ensureCapacity(length + 1);
            buffer[length++] = '-';
            value = -i;
        } else {
            value = i;
        }
        final int size = MutableString.stringSize(value);
        ensureCapacity(length + size);
        MutableString.getChars(value, length + size, buffer);
        length += size;
        hash = 0;
        return this;
    }

    @Override
    public MutableString append(final long l) {
        if (l == Long.MIN_VALUE) {
            return appendBytes(LONG_MIN_BYTES);
        }
        final long value;
        if (l < 0) {
            ensureCapacity(length + 1);
            buffer[length++] = '-';
            value = -l;
        } else {
            value = l;
        }
        final int size = MutableString.stringSize(value);
        ensureCapacity(length + size);
        MutableString.getChars(value, length + size, buffer);
        length += size;
        hash = 0;
        return this;
    }

    @Override
    public MutableString append(final float f) {
        return append(f, DEFAULT_PRECISION);
    }

    @Override
    public MutableString append(final float f, final int precision) {
        if (precision < 0 || precision >= POWERS_OF_TEN.length) {
            throw new IllegalArgumentException("Precision must be between 0 and " + (POWERS_OF_TEN.length - 1));
        }
        if (Float.isNaN(f)) {
            return appendBytes(NAN_BYTES);
        }
        if (Float.isInfinite(f)) {
            return appendBytes(INF_BYTES);
        }
        final float value;
        if (f < 0) {
            ensureCapacity(length + 1);
            buffer[length++] = '-';
            value = -f;
        } else {
            value = f;
        }
        final int size = MutableString.stringSize(value, precision);
        ensureCapacity(length + size);
        MutableString.getChars(value, length + size, precision, buffer);
        length += size;
        hash = 0;
        return this;
    }

    @Override
    public MutableString append(final double d) {
        return append(d, DEFAULT_PRECISION);
    }

    @Override
    public MutableString append(final double d, final int precision) {
        if (precision < 0 || precision >= POWERS_OF_TEN.length) {
            throw new IllegalArgumentException("Precision must be between 0 and " + (POWERS_OF_TEN.length - 1));
        }
        if (Double.isNaN(d)) {
            return appendBytes(NAN_BYTES);
        }
        if (Double.isInfinite(d)) {
            return appendBytes(INF_BYTES);
        }
        final double value;
        if (d < 0) {
            ensureCapacity(length + 1);
            buffer[length++] = '-';
            value = -d;
        } else {
            value = d;
        }
        final int size = MutableString.stringSize(value, precision);
        ensureCapacity(length + size);
        MutableString.getChars(value, length + size, precision, buffer);
        length += size;
        hash = 0;
        return this;
    }

    @Override
    public MutableString append(final boolean b) {
        return appendBytes(b ? TRUE_BYTES : FALSE_BYTES);
    }

    private void ensureCapacity(final int needed) {
        if (needed <= buffer.length) {
            return;
        }
        if (needed > Integer.MAX_VALUE - 8) {
            throw new OutOfMemoryError("Requested size exceeds VM array limit");
        }
        final int newCap = Math.max(needed, buffer.length + (buffer.length >> 1) + 1);
        final byte[] newBuf = new byte[newCap];
        UNSAFE.copyMemory(buffer, BYTE_ARRAY_OFFSET, newBuf, BYTE_ARRAY_OFFSET, length);
        buffer = newBuf;
    }

    @Override
    public int getByteLength() {
        return length;
    }
}
