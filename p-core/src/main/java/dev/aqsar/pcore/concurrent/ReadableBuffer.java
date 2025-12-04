package dev.aqsar.pcore.concurrent;

import dev.aqsar.pcore.string.MutableString;

import java.nio.charset.Charset;

/**
 * A safe, read-only view of a region of off-heap memory.
 * This interface provides a way to read data without exposing
 * the underlying ByteBuffer or Unsafe.
 * <p>
 * All multibyte accessors (e.g., getLong, getInt) read using
 * native byte order.
 */
public interface ReadableBuffer {
    /**
     * @return The length of this buffer view in bytes.
     */
    int length();

    /**
     * Reads a byte from the given index.
     *
     * @param index index relative to the start of this buffer view (0 to length-1)
     * @return the byte at the index
     */
    byte getByte(final int index);

    /**
     * Reads a short from the given index (in native byte order).
     *
     * @param index index relative to the start of this buffer view (0 to length-2)
     * @return the short at the index
     */
    short getShort(final int index);

    /**
     * Reads a char from the given index (in native byte order).
     *
     * @param index index relative to the start of this buffer view (0 to length-2)
     * @return the char at the index
     */
    char getChar(final int index);

    /**
     * Reads an int from the given index (in native byte order).
     *
     * @param index index relative to the start of this buffer view (0 to length-4)
     * @return the int at the index
     */
    int getInt(final int index);

    /**
     * Reads a float from the given index (in native byte order).
     *
     * @param index index relative to the start of this buffer view (0 to length-4)
     * @return the float at the index
     */
    float getFloat(final int index);

    /**
     * Reads a long from the given index (in native byte order).
     *
     * @param index index relative to the start of this buffer view (0 to length-8)
     * @return the long at the index
     */
    long getLong(final int index);

    /**
     * Reads a double from the given index (in native byte order).
     *
     * @param index index relative to the start of this buffer view (0 to length-8)
     * @return the double at the index
     */
    double getDouble(final int index);

    /**
     * Bulk copies bytes from this buffer into a destination byte array.
     *
     * @param index     the source index in this buffer
     * @param dst       the destination byte array
     * @param dstOffset the destination offset in the byte array
     * @param length    the number of bytes to copy
     */
    void getBytes(final int index, final byte[] dst, final int dstOffset, final int length);

    /**
     * Decodes and heap-allocates an ASCII string.
     * <p>
     * NOTE: This method allocates a new {@code String} object.
     *
     * @param index  the source index in this buffer
     * @param length the number of bytes (chars) to read
     * @return a new String
     */
    String getStringAscii(final int index, final int length);

    /**
     * Decodes and heap-allocates a string using the specified charset.
     * <p>
     * NOTE: This method allocates a {@code byte[]} and a new {@code String} object.
     *
     * @param index   the source index in this buffer
     * @param length  the number of bytes to read
     * @param charset the charset to use for decoding
     * @return a new String
     */
    String getString(final int index, final int length, final Charset charset);

    /**
     * Decodes a string without allocations.
     *
     * @param index  the source index in this buffer
     * @param length the number of bytes to read
     * @param dst    the MutableString to write to
     */
    void getString(final int index, final int length, final MutableString dst);
}
