package dev.aqsar.pcore.concurrent;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.Charset;

/**
 * An Unsafe-backed implementation of {@link ReadWriteBuffer}.
 * <p>
 * This is a "flyweight" object. A single instance should be created and
 * reused by calling {@link #wrap(long, int)}. This class is not thread-safe
 * (it is intended to be used by a single consumer thread at a time).
 */
@SuppressWarnings("restriction")
public final class UnsafeBuffer implements ReadWriteBuffer {

    private static final Unsafe UNSAFE;
    private static final long ARRAY_BYTE_BASE_OFFSET;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);

            // Get the offset of the first element in a byte array
            ARRAY_BYTE_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private long address;
    private int length;

    /**
     * Wraps a region of memory.
     *
     * @param address the starting absolute memory address
     * @param length  the length of the region in bytes
     * @return this instance
     */
    @Override
    public UnsafeBuffer wrap(long address, int length) {
        this.address = address;
        this.length = length;
        return this;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public byte getByte(int index) {
        checkIndex(index, 1);
        return UNSAFE.getByte(address + index);
    }

    @Override
    public short getShort(int index) {
        checkIndex(index, 2);
        return UNSAFE.getShort(address + index);
    }

    @Override
    public char getChar(int index) {
        checkIndex(index, 2);
        return UNSAFE.getChar(address + index);
    }

    @Override
    public int getInt(int index) {
        checkIndex(index, 4);
        return UNSAFE.getInt(address + index);
    }

    @Override
    public float getFloat(int index) {
        checkIndex(index, 4);
        return UNSAFE.getFloat(address + index);
    }

    @Override
    public long getLong(int index) {
        checkIndex(index, 8);
        return UNSAFE.getLong(address + index);
    }

    @Override
    public double getDouble(int index) {
        checkIndex(index, 8);
        return UNSAFE.getDouble(address + index);
    }

    @Override
    public void getBytes(int index, byte[] dst, int dstOffset, int length) {
        checkIndex(index, length);
        UNSAFE.copyMemory(null, address + index, dst, ARRAY_BYTE_BASE_OFFSET + dstOffset, length);
    }

    @Override
    public String getStringAscii(int index, int length) {
        checkIndex(index, length);
        char[] chars = new char[length];
        long readAddress = address + index;
        for (int i = 0; i < length; i++) {
            // Read byte and convert to char (masking to treat as unsigned)
            chars[i] = (char) (UNSAFE.getByte(readAddress + i) & 0xFF);
        }
        return new String(chars);
    }

    @Override
    public String getString(int index, int length, Charset charset) {
        checkIndex(index, length);
        final byte[] bytes = new byte[length];
        getBytes(index, bytes, 0, length);
        return new String(bytes, charset);
    }

    @Override
    public void putByte(int index, byte value) {
        checkIndex(index, 1);
        UNSAFE.putByte(address + index, value);
    }

    @Override
    public void putShort(int index, short value) {
        checkIndex(index, 2);
        UNSAFE.putShort(address + index, value);
    }

    @Override
    public void putChar(int index, char value) {
        checkIndex(index, 2);
        UNSAFE.putChar(address + index, value);
    }

    @Override
    public void putInt(int index, int value) {
        checkIndex(index, 4);
        UNSAFE.putInt(address + index, value);
    }

    @Override
    public void putFloat(int index, float value) {
        checkIndex(index, 4);
        UNSAFE.putFloat(address + index, value);
    }

    @Override
    public void putLong(int index, long value) {
        checkIndex(index, 8);
        UNSAFE.putLong(address + index, value);
    }

    @Override
    public void putDouble(int index, double value) {
        checkIndex(index, 8);
        UNSAFE.putDouble(address + index, value);
    }

    @Override
    public void putBytes(int index, byte[] src, int srcOffset, int length) {
        checkIndex(index, length);
        UNSAFE.copyMemory(src, ARRAY_BYTE_BASE_OFFSET + srcOffset, null, address + index, length);
    }

    @Override
    public int putStringAscii(int index, String value) {
        final int stringLength = value.length();
        checkIndex(index, stringLength);
        long writeAddress = address + index;
        for (int i = 0; i < stringLength; i++) {
            // Write the lower 8 bits of the char
            UNSAFE.putByte(writeAddress + i, (byte) value.charAt(i));
        }
        return stringLength;
    }

    @Override
    public int putString(int index, String value, Charset charset) {
        final byte[] bytes = value.getBytes(charset);
        final int bytesLength = bytes.length;
        checkIndex(index, bytesLength);
        putBytes(index, bytes, 0, bytesLength);
        return bytesLength;
    }

    private void checkIndex(int index, int size) {
        assert (index >= 0 && index + size <= length) :
            "Index out of bounds: index=" + index + ", size=" + size + ", length=" + length;
    }
}
