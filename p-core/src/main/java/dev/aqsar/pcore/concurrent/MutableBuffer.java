package dev.aqsar.pcore.concurrent;

import java.nio.charset.Charset;

/**
 * A write-only view of a region of off-heap memory.
 * <p>
 * All multibyte accessors (e.g., putLong, putInt) write using
 * native byte order.
 */
public interface MutableBuffer {

    /**
     * Writes a byte to the given index.
     *
     * @param index index relative to the start of this buffer view
     * @param value the byte to write
     */
    void putByte(int index, byte value);

    /**
     * Writes a short to the given index (in native byte order).
     *
     * @param index index relative to the start of this buffer view
     * @param value the short to write
     */
    void putShort(int index, short value);

    /**
     * Writes a char to the given index (in native byte order).
     *
     * @param index index relative to the start of this buffer view
     * @param value the char to write
     */
    void putChar(int index, char value);

    /**
     * Writes an int to the given index (in native byte order).
     *
     * @param index index relative to the start of this buffer view
     * @param value the int to write
     */
    void putInt(int index, int value);

    /**
     * Writes a float to the given index (in native byte order).
     *
     * @param index index relative to the start of this buffer view
     * @param value the float to write
     */
    void putFloat(int index, float value);

    /**
     * Writes a long to the given index (in native byte order).
     *
     * @param index index relative to the start of this buffer view
     * @param value the long to write
     */
    void putLong(int index, long value);

    /**
     * Writes a double to the given index (in native byte order).
     *
     * @param index index relative to the start of this buffer view
     * @param value the double to write
     */
    void putDouble(int index, double value);

    /**
     * Bulk copies bytes from a source byte array into this buffer.
     *
     * @param index     the destination index in this buffer
     * @param src       the source byte array
     * @param srcOffset the source offset in the byte array
     * @param length    the number of bytes to copy
     */
    void putBytes(int index, byte[] src, int srcOffset, int length);

    /**
     * Encodes and writes an ASCII string.
     * <p>
     * NOTE: This method truncates chars to 8 bits.
     *
     * @param index the destination index in this buffer
     * @param value the String to write
     * @return the number of bytes written
     */
    int putStringAscii(int index, String value);

    /**
     * Encodes and writes a string using the specified charset.
     * <p>
     * NOTE: This method allocates a {@code byte[]}.
     *
     * @param index   the destination index in this buffer
     * @param value   the String to write
     * @param charset the charset to use for encoding
     * @return the number of bytes written
     */
    int putString(int index, String value, Charset charset);
}
