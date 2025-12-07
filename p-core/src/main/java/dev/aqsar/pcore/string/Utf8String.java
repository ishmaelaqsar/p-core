package dev.aqsar.pcore.string;

import dev.aqsar.pcore.concurrent.ReadableBuffer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Unsafe-optimized UTF-8 string builder.
 */
@NotThreadSafe
@SuppressWarnings("restriction")
public final class Utf8String extends AbstractMutableString {
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
    private int byteLength;
    private int charLength;
    private int hash;

    public Utf8String() {
        this(128);
    }

    public Utf8String(final int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Negative capacity");
        }
        buffer = new byte[capacity];
        byteLength = 0;
        charLength = 0;
    }

    @Override
    public void clear() {
        byteLength = 0;
        charLength = 0;
        hash = 0;
    }

    @Override
    public void setLength(final int newByteLen) {
        if (newByteLen < 0) {
            throw new IllegalArgumentException("Negative length");
        }
        ensureCapacity(newByteLen);
        if (newByteLen == 0) {
            byteLength = 0;
            charLength = 0;
            hash = 0;
            return;
        }
        if (newByteLen > byteLength) {
            // Padding with zeros (null chars)
            final int diff = newByteLen - byteLength;
            Arrays.fill(buffer, byteLength, newByteLen, (byte) 0);
            charLength += diff; // 0 byte is 1 char
        } else if (newByteLen < byteLength) {
            // Truncation: recalculate charLength as we don't know how many chars we cut
            recalculateCharLength(newByteLen);
        }
        byteLength = newByteLen;
        hash = 0;
    }

    private void ensureCapacity(final int needed) {
        if (needed <= buffer.length) {
            return;
        }
        // Check for overflow or massive size
        if (needed > Integer.MAX_VALUE - 8) {
            throw new OutOfMemoryError("Requested size exceeds VM array limit");
        }
        final int newCap = Math.max(needed, buffer.length + (buffer.length >> 1) + 1);
        final byte[] newBuf = new byte[newCap]; // JVM handles the MAX_VALUE limit check here usually
        UNSAFE.copyMemory(buffer, BYTE_ARRAY_OFFSET, newBuf, BYTE_ARRAY_OFFSET, byteLength);
        buffer = newBuf;
    }

    /**
     * Recalculates char count up to the new byte limit.
     */
    private void recalculateCharLength(final int limit) {
        int chars = 0;
        for (int i = 0; i < limit; i++) {
            byte b = buffer[i];
            if (b >= 0) {
                chars++;
            } else if ((b & 0xE0) == 0xC0) {
                chars++;
                i += 1;
            } else if ((b & 0xF0) == 0xE0) {
                chars++;
                i += 2;
            } else if ((b & 0xF8) == 0xF0) {
                chars += 2; // Surrogate pair is 2 chars
                i += 3;
            }
        }
        this.charLength = chars;
    }

    @Override
    public MutableString append(final boolean b) {
        return appendBytes(b ? TRUE_BYTES : FALSE_BYTES);
    }

    @Override
    public MutableString append(final char c) {
        if (c < 0x80) {
            ensureCapacity(byteLength + 1);
            buffer[byteLength++] = (byte) c;
        } else if (c < 0x800) {
            ensureCapacity(byteLength + 2);
            buffer[byteLength++] = (byte) (0xC0 | (c >> 6));
            buffer[byteLength++] = (byte) (0x80 | (c & 0x3F));
        } else if (c < 0xD800 || c > 0xDFFF) {
            ensureCapacity(byteLength + 3);
            buffer[byteLength++] = (byte) (0xE0 | (c >> 12));
            buffer[byteLength++] = (byte) (0x80 | ((c >> 6) & 0x3F));
            buffer[byteLength++] = (byte) (0x80 | (c & 0x3F));
        } else {
            appendReplacementChar(); // Takes 3 bytes, represents 1 char (REPLACEMENT CHARACTER)
        }
        charLength++;
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
            ensureCapacity(byteLength + 1);
            buffer[byteLength++] = '-';
            charLength++;
            value = -i;
        } else {
            value = i;
        }
        final int len = stringSize(value);
        ensureCapacity(byteLength + len);
        getChars(value, byteLength + len, buffer);
        byteLength += len;
        charLength += len;
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
            ensureCapacity(byteLength + 1);
            buffer[byteLength++] = '-';
            charLength++;
            value = -l;
        } else {
            value = l;
        }
        final int len = stringSize(value);
        ensureCapacity(byteLength + len);
        getChars(value, byteLength + len, buffer);
        byteLength += len;
        charLength += len;
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
            ensureCapacity(byteLength + 1);
            buffer[byteLength++] = '-';
            charLength++;
            value = -f;
        } else {
            value = f;
        }
        final int len = stringSize(value, precision);
        ensureCapacity(byteLength + len);
        getChars(value, byteLength + len, precision, buffer);
        byteLength += len;
        charLength += len;
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
            ensureCapacity(byteLength + 1);
            buffer[byteLength++] = '-';
            charLength++;
            value = -d;
        } else {
            value = d;
        }
        final int len = stringSize(value, precision);
        ensureCapacity(byteLength + len);
        getChars(value, byteLength + len, precision, buffer);
        byteLength += len;
        charLength += len;
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
        if (s instanceof final String str) {
            appendString(str, start, end);
            charLength += len;
            return this;
        }
        if (s instanceof final AsciiString ascii) {
            final byte[] src = ascii.getRawBytes();
            ensureCapacity(byteLength + len);
            UNSAFE.copyMemory(src, BYTE_ARRAY_OFFSET + start, buffer, BYTE_ARRAY_OFFSET + byteLength, len);
            byteLength += len;
            charLength += len;
            hash = 0;
            return this;
        }
        if (s instanceof final Utf8String u && start == 0 && end == s.length()) {
            ensureCapacity(byteLength + u.byteLength);
            UNSAFE.copyMemory(u.buffer, BYTE_ARRAY_OFFSET, buffer, BYTE_ARRAY_OFFSET + byteLength, u.byteLength);
            byteLength += u.byteLength;
            charLength += u.charLength;
            hash = 0;
            return this;
        }
        for (int i = start; i < end; i++) {
            final char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < end && Character.isLowSurrogate(s.charAt(i + 1))) {
                    final int codePoint = Character.toCodePoint(c, s.charAt(++i));
                    appendCodePoint(codePoint);
                } else {
                    appendReplacementChar();
                }
            } else if (Character.isLowSurrogate(c)) {
                appendReplacementChar();
            } else {
                append(c); // internally increments charLength
            }
        }
        return this;
    }

    @Override
    public MutableString copyOf(@Nullable final CharSequence cs) {
        if (cs == null) {
            clear();
            return this;
        }
        if (cs instanceof Utf8String u) {
            ensureCapacity(u.byteLength);
            UNSAFE.copyMemory(u.buffer, BYTE_ARRAY_OFFSET, buffer, BYTE_ARRAY_OFFSET, u.byteLength);
            byteLength = u.byteLength;
            charLength = u.charLength;
            hash = 0;
            return this;
        }
        if (cs instanceof final AsciiString ascii) {
            final byte[] src = ascii.getRawBytes();
            ensureCapacity(src.length);
            UNSAFE.copyMemory(src, BYTE_ARRAY_OFFSET, buffer, BYTE_ARRAY_OFFSET, src.length);
            byteLength = src.length;
            charLength = src.length;
            hash = 0;
            return this;
        }
        if (cs instanceof String s) {
            final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            ensureCapacity(bytes.length);
            UNSAFE.copyMemory(bytes, BYTE_ARRAY_OFFSET, buffer, BYTE_ARRAY_OFFSET, bytes.length);
            byteLength = bytes.length;
            charLength = s.length();
            hash = 0;
            return this;
        }
        clear();
        append(cs);
        return this;
    }

    @Override
    public void copyFrom(final ReadableBuffer src, final int index, final int length) {
        ensureCapacity(length);
        src.getBytes(index, this.buffer, 0, length);
        this.byteLength = length;
        recalculateCharLength(length);
        this.hash = 0;
    }

    @Override
    public int getByteLength() {
        return byteLength;
    }

    @Override
    public ImmutableView toImmutableView() {
        return new ImmutableView(this);
    }

    private MutableString appendBytes(final byte[] src) {
        ensureCapacity(byteLength + src.length);
        UNSAFE.copyMemory(src, BYTE_ARRAY_OFFSET, buffer, BYTE_ARRAY_OFFSET + byteLength, src.length);
        byteLength += src.length;
        charLength += src.length;
        hash = 0;
        return this;
    }

    private void appendString(final String s, final int start, final int end) {
        for (int i = start; i < end; i++) {
            final char c = s.charAt(i);
            if (c < 0x80) {
                // Fast path for ASCII
                ensureCapacity(byteLength + 1);
                buffer[byteLength++] = (byte) c;
            } else if (c < 0x800) {
                ensureCapacity(byteLength + 2);
                buffer[byteLength++] = (byte) (0xC0 | (c >> 6));
                buffer[byteLength++] = (byte) (0x80 | (c & 0x3F));
            } else if (Character.isSurrogate(c)) {
                if (Character.isHighSurrogate(c) && i + 1 < end && Character.isLowSurrogate(s.charAt(i + 1))) {
                    int cp = Character.toCodePoint(c, s.charAt(++i));
                    appendCodePoint(cp);
                } else {
                    appendReplacementChar();
                }
            } else {
                ensureCapacity(byteLength + 3);
                buffer[byteLength++] = (byte) (0xE0 | (c >> 12));
                buffer[byteLength++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                buffer[byteLength++] = (byte) (0x80 | (c & 0x3F));
            }
        }
        hash = 0;
    }

    private void appendCodePoint(final int cp) {
        if (cp < 0x80) {
            ensureCapacity(byteLength + 1);
            buffer[byteLength++] = (byte) cp;
        } else if (cp < 0x800) {
            ensureCapacity(byteLength + 2);
            buffer[byteLength++] = (byte) (0xC0 | (cp >> 6));
            buffer[byteLength++] = (byte) (0x80 | (cp & 0x3F));
        } else if (cp < 0x10000) {
            ensureCapacity(byteLength + 3);
            buffer[byteLength++] = (byte) (0xE0 | (cp >> 12));
            buffer[byteLength++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
            buffer[byteLength++] = (byte) (0x80 | (cp & 0x3F));
        } else {
            ensureCapacity(byteLength + 4);
            buffer[byteLength++] = (byte) (0xF0 | (cp >> 18));
            buffer[byteLength++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
            buffer[byteLength++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
            buffer[byteLength++] = (byte) (0x80 | (cp & 0x3F));
        }
    }

    private void appendReplacementChar() {
        ensureCapacity(byteLength + 3);
        buffer[byteLength++] = (byte) 0xEF;
        buffer[byteLength++] = (byte) 0xBF;
        buffer[byteLength++] = (byte) 0xBD;
    }

    public byte[] getRawBytes() {
        return buffer;
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0 && byteLength > 0) {
            for (int i = 0; i < byteLength; i++) {
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
        if (!(o instanceof Utf8String u)) {
            return false;
        }
        if (byteLength != u.byteLength) {
            return false;
        }
        if (hash != 0 && u.hash != 0 && hash != u.hash) {
            return false;
        }
        for (int i = 0; i < byteLength; i++) {
            if (buffer[i] != u.buffer[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return new String(buffer, 0, byteLength, StandardCharsets.UTF_8);
    }

    @Override
    public int length() {
        return charLength;
    }

    @Override
    public char charAt(final int index) {
        if (index < 0 || index >= charLength) {
            throw new IndexOutOfBoundsException(index);
        }
        // NOTE: This remains O(N) because UTF-8 is variable width.
        // We cannot skip to the Nth char without knowing the size of previous chars.
        int charCount = 0;
        for (int i = 0; i < byteLength; i++) {
            final byte b = buffer[i];
            final int charLen;
            final int seqLen;
            final int codePoint;
            if (b >= 0) {
                charLen = 1;
                seqLen = 1;
                codePoint = b;
            } else if ((b & 0xE0) == 0xC0) {
                charLen = 1;
                seqLen = 2;
                codePoint = ((b & 0x1F) << 6) | (buffer[i + 1] & 0x3F);
            } else if ((b & 0xF0) == 0xE0) {
                charLen = 1;
                seqLen = 3;
                codePoint = ((b & 0x0F) << 12) | ((buffer[i + 1] & 0x3F) << 6) | (buffer[i + 2] & 0x3F);
            } else if ((b & 0xF8) == 0xF0) {
                charLen = 2;
                seqLen = 4;
                codePoint = ((b & 0x07) << 18) | ((buffer[i + 1] & 0x3F) << 12) | ((buffer[i + 2] & 0x3F) << 6) |
                            (buffer[i + 3] & 0x3F);
            } else {
                continue; // Skip invalid
            }
            if (charCount == index) {
                if (charLen == 2) {
                    return Character.highSurrogate(codePoint);
                }
                return (char) codePoint;
            }
            // If we are looking for the low surrogate of a 4-byte sequence
            if (charLen == 2 && charCount + 1 == index) {
                return Character.lowSurrogate(codePoint);
            }
            charCount += charLen;
            i += (seqLen - 1);
        }
        throw new IndexOutOfBoundsException(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        if (start < 0 || end < start || end > charLength) {
            throw new IndexOutOfBoundsException();
        }
        if (start == end) {
            return "";
        }
        int byteStart = -1;
        int byteEnd = -1;
        int charCount = 0;
        for (int i = 0; i < byteLength; i++) {
            if (charCount == start) {
                byteStart = i;
            }
            if (charCount == end) {
                byteEnd = i;
                break;
            }
            final byte b = buffer[i];
            if (b >= 0) {
                charCount++;
            } else if ((b & 0xE0) == 0xC0) {
                charCount++;
                i += 1;
            } else if ((b & 0xF0) == 0xE0) {
                charCount++;
                i += 2;
            } else if ((b & 0xF8) == 0xF0) {
                charCount += 2;
                i += 3;
            }
        }
        if (byteStart == -1) {
            throw new IndexOutOfBoundsException();
        }
        if (byteEnd == -1) {
            byteEnd = byteLength;
        }
        return new String(buffer, byteStart, byteEnd - byteStart, StandardCharsets.UTF_8);
    }
}
