package dev.aqsar.pcore.concurrent;

/**
 * A combined read-write view of a region of off-heap memory.
 * <p>
 * This interface inherits all methods from ReadableBuffer and MutableBuffer.
 */
public interface ReadWriteBuffer extends ReadableBuffer, MutableBuffer {

    /**
     * Wraps a region of memory.
     *
     * @param address the starting absolute memory address
     * @param length  the length of the region in bytes
     * @return this instance
     */
    ReadWriteBuffer wrap(long address, int length);
}
