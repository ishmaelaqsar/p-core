package dev.aqsar.pcore.concurrent.ringbuffer;

import dev.aqsar.pcore.concurrent.UnsafeBuffer;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * <h2>Multi-Producer / Multi-Consumer Ring Buffer</h2>
 *
 * <p>A high-performance, lock-free multi-producer multi-consumer (MPMC) ring buffer
 * based on a direct {@link ByteBuffer} and {@link Unsafe}.</p>
 *
 * <h3>Memory Layout</h3>
 * <pre>
 * +------------------+----------------------+
 * |   Metadata (128B)|     Data Region      |
 * +------------------+----------------------+
 * </pre>
 */
@SuppressWarnings("restriction")
public final class MPMCRingBuffer implements RingBuffer {

    // --- Constants ---
    private static final int RECORD_HEADER_SIZE = 8; // packed (size<<32 | type)
    private static final int RECORD_ALIGNMENT = RECORD_HEADER_SIZE;
    private static final int PADDING_TYPE = -1;

    private static final int INSUFFICIENT_SPACE = -2;

    private static final Unsafe UNSAFE;
    private static final long BUFFER_ADDRESS_FIELD_OFFSET;
    private static final long PRODUCER_INDEX_OFFSET;
    private static final long CONSUMER_INDEX_OFFSET;
    private static final long CORRELATION_OFFSET;


    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);

            Field addrField = Buffer.class.getDeclaredField("address");
            addrField.setAccessible(true);
            BUFFER_ADDRESS_FIELD_OFFSET = UNSAFE.objectFieldOffset(addrField);

            PRODUCER_INDEX_OFFSET = 0L;
            CONSUMER_INDEX_OFFSET = CACHE_LINE;
            CORRELATION_OFFSET = CACHE_LINE + 16;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final ByteBuffer buffer;
    private final long baseAddress;
    private final long dataBaseAddress;
    private final int capacityMask;
    private final int dataSize;
    private final int maxPayload;

    private final long producerIndexAddress;
    private final long consumerIndexAddress;
    private final long correlationAddr;

    // Flyweight buffer for the consumer
    private final ThreadLocal<UnsafeBuffer> consumerBuffer = ThreadLocal.withInitial(UnsafeBuffer::new);

    /**
     * Constructs an {@code MPMCRingBuffer} backed by the given direct {@link ByteBuffer}.
     *
     * @param directBuffer a direct buffer, e.g., from {@link RingBufferAllocator}.
     * @throws IllegalArgumentException if the buffer is null, non-direct, or size invalid.
     */
    public MPMCRingBuffer(final ByteBuffer directBuffer) {
        if (directBuffer == null || !directBuffer.isDirect()) {
            throw new IllegalArgumentException("Buffer must be direct");
        }
        if (directBuffer.capacity() < METADATA_SIZE + MIN_SIZE) {
            throw new IllegalArgumentException("Buffer too small");
        }

        this.buffer = directBuffer.order(ByteOrder.nativeOrder());
        this.dataSize = directBuffer.capacity() - METADATA_SIZE;
        this.capacityMask = dataSize - 1;
        this.maxPayload = dataSize - RECORD_HEADER_SIZE;

        if ((dataSize & capacityMask) != 0) {
            throw new IllegalArgumentException("Data region size must be a power of two: " + dataSize);
        }

        this.baseAddress = UNSAFE.getLong(directBuffer, BUFFER_ADDRESS_FIELD_OFFSET);
        this.dataBaseAddress = baseAddress + METADATA_SIZE;
        this.producerIndexAddress = baseAddress + PRODUCER_INDEX_OFFSET;
        this.consumerIndexAddress = baseAddress + CONSUMER_INDEX_OFFSET;
        this.correlationAddr = baseAddress + CORRELATION_OFFSET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean offer(final int typeId, final ByteBuffer src, final int srcOffset, final int len) {
        if (typeId < 1) {
            throw new IllegalArgumentException("typeId must be positive");
        }
        if (len < 0) {
            throw new IllegalArgumentException("invalid length");
        }

        final int recordSize = alignUp(len + RECORD_HEADER_SIZE);
        if (len > maxPayload) {
            throw new IllegalArgumentException("length " + len + " exceeds max payload " + maxPayload);
        }

        final long capacity = this.dataSize;
        long head;
        long tail;

        while (true) {
            head = UNSAFE.getLongVolatile(null, consumerIndexAddress);
            tail = UNSAFE.getLongVolatile(null, producerIndexAddress);

            final long free = capacity - (tail - head);
            final int offset = (int) (tail & capacityMask);
            final int remaining = (int) capacity - offset;

            long newTail = tail;
            int padding = 0;

            if (recordSize > remaining) {
                if (free < (recordSize + remaining)) {
                    return false;
                }
                padding = remaining;
                newTail += padding + recordSize;
            } else {
                if (free < recordSize) {
                    return false;
                }
                newTail += recordSize;
            }

            if (UNSAFE.compareAndSwapLong(null, producerIndexAddress, tail, newTail)) {
                int writeOffset = offset;
                if (padding > 0) {
                    putPaddingRecord(offset, padding);
                    writeOffset = 0;
                }
                putRecord(writeOffset, src, srcOffset, len, typeId, recordSize);
                return true;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int claim(final int typeId, final int len) {
        if (typeId < 1) {
            throw new IllegalArgumentException("typeId must be positive");
        }
        if (len < 0) {
            throw new IllegalArgumentException("invalid length");
        }

        final int recordSize = alignUp(len + RECORD_HEADER_SIZE);
        if (len > maxPayload) {
            throw new IllegalArgumentException("length " + len + " exceeds max payload " + maxPayload);
        }

        final long capacity = this.dataSize;
        long head;
        long tail;

        while (true) {
            head = UNSAFE.getLongVolatile(null, consumerIndexAddress);
            tail = UNSAFE.getLongVolatile(null, producerIndexAddress);

            final long free = capacity - (tail - head);
            final int offset = (int) (tail & capacityMask);
            final int remaining = (int) capacity - offset;

            long newTail = tail;
            int padding = 0;

            if (recordSize > remaining) {
                if (free < (recordSize + remaining)) {
                    return INSUFFICIENT_SPACE;
                }
                padding = remaining;
                newTail += padding + recordSize;
            } else {
                if (free < recordSize) {
                    return INSUFFICIENT_SPACE;
                }
                newTail += recordSize;
            }

            if (UNSAFE.compareAndSwapLong(null, producerIndexAddress, tail, newTail)) {
                int writeOffset = offset;
                if (padding > 0) {
                    putPaddingRecord(offset, padding);
                    writeOffset = 0;
                }

                long headerAddr = dataBaseAddress + writeOffset;
                UNSAFE.putLong(headerAddr, packHeader(-recordSize, typeId));
                UNSAFE.storeFence();

                // Return offset relative to the buffer's start
                return METADATA_SIZE + writeOffset + RECORD_HEADER_SIZE;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(final int payloadOffset) {
        // Convert from absolute offset back to data-region offset
        final int offset = (payloadOffset - METADATA_SIZE) - RECORD_HEADER_SIZE;
        if (offset < 0 || offset >= dataSize) {
            throw new IllegalArgumentException("invalid offset");
        }
        final long headerAddr = dataBaseAddress + offset;

        final long packed = UNSAFE.getLong(headerAddr);
        final int recSize = unpackSize(packed);
        if (recSize >= 0) {
            throw new IllegalStateException("slot already published or invalid");
        }

        UNSAFE.storeFence();

        final int typeId = unpackType(packed);
        UNSAFE.putLongVolatile(null, headerAddr, packHeader(-recSize, typeId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void abandon(final int payloadOffset) {
        // Convert from absolute offset back to data-region offset
        final int offset = (payloadOffset - METADATA_SIZE) - RECORD_HEADER_SIZE;
        if (offset < 0 || offset >= dataSize) {
            throw new IllegalArgumentException("invalid offset");
        }
        final long headerAddr = dataBaseAddress + offset;

        final long packed = UNSAFE.getLong(headerAddr);
        final int recSize = unpackSize(packed);
        if (recSize >= 0) {
            throw new IllegalStateException("slot already published or invalid");
        }

        UNSAFE.storeFence();

        UNSAFE.putLongVolatile(null, headerAddr, packHeader(-recSize, PADDING_TYPE));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int poll(final MessageConsumer consumer, final int limit) {
        final UnsafeBuffer buffer = consumerBuffer.get();
        int count = 0;

        while (count < limit) {
            final long head = UNSAFE.getLongVolatile(null, consumerIndexAddress);
            final long tail = UNSAFE.getLongVolatile(null, producerIndexAddress);

            if (head >= tail) {
                return count; // Empty
            }

            final int offset = (int) (head & capacityMask);
            final long recordAddr = dataBaseAddress + offset;
            final long header = UNSAFE.getLongVolatile(null, recordAddr);
            final int recordSize = unpackSize(header);

            if (recordSize <= 0) {
                return count; // Message not yet published
            }

            final int alignedRecordSize = alignUp(recordSize);

            // Atomically claim this message
            final long newHead = head + alignedRecordSize;
            if (UNSAFE.compareAndSwapLong(null, consumerIndexAddress, head, newHead)) {
                // We own this message
                final int typeId = unpackType(header);
                if (typeId != PADDING_TYPE) {
                    final int payloadLen = recordSize - RECORD_HEADER_SIZE;
                    final long payloadAddr = recordAddr + RECORD_HEADER_SIZE;
                    consumer.accept(typeId, buffer.wrap(payloadAddr, payloadLen));
                    ++count;
                }
            }
        }
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int controlledPoll(final ControlledConsumer consumer, final int limit) {
        final UnsafeBuffer buffer = consumerBuffer.get();
        int count = 0;

        while (count < limit) {
            final long head = UNSAFE.getLongVolatile(null, consumerIndexAddress);
            final long tail = UNSAFE.getLongVolatile(null, producerIndexAddress);

            if (head >= tail) {
                return count; // Empty
            }

            final int offset = (int) (head & capacityMask);
            final long recordAddr = dataBaseAddress + offset;
            final long header = UNSAFE.getLongVolatile(null, recordAddr);
            final int recordSize = unpackSize(header);

            if (recordSize <= 0) {
                return count; // Message not yet published
            }

            final int alignedRecordSize = alignUp(recordSize);

            // Atomically claim this message
            final long newHead = head + alignedRecordSize;
            if (UNSAFE.compareAndSwapLong(null, consumerIndexAddress, head, newHead)) {
                // We own this message
                final int typeId = unpackType(header);

                if (typeId == PADDING_TYPE) {
                    continue; // Consumed padding, loop for next
                }

                final int payloadLen = recordSize - RECORD_HEADER_SIZE;
                final long payloadAddr = recordAddr + RECORD_HEADER_SIZE;
                final ConsumerAction action = consumer.accept(typeId, buffer.wrap(payloadAddr, payloadLen));

                ++count;

                if (action == ConsumerAction.ABORT) {
                    throw new UnsupportedOperationException("ABORT action is not supported by MPMCRingBuffer. " +
                                                            "Message was already atomically consumed.");
                }

                if (action == ConsumerAction.BREAK) {
                    return count;
                }
            }
        }
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return dataSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int maxPayloadLength() {
        return maxPayload;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        UNSAFE.putLongVolatile(null, producerIndexAddress, 0);
        UNSAFE.putLongVolatile(null, consumerIndexAddress, 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int utilization() {
        long consBefore;
        long prod;
        long consAfter = UNSAFE.getLongVolatile(null, consumerIndexAddress);
        do {
            consBefore = consAfter;
            prod = UNSAFE.getLongVolatile(null, producerIndexAddress);
            consAfter = UNSAFE.getLongVolatile(null, consumerIndexAddress);
        } while (consAfter != consBefore);

        final long used = prod - consAfter;
        return (int) Math.min(dataSize, Math.max(0, used));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBuffer underlyingBuffer() {
        return buffer;
    }

    /**
     * {@inheritDoc}
     * _
     */
    @Override
    public long producerSeq() {
        return UNSAFE.getLongVolatile(null, producerIndexAddress);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long consumerSeq() {
        return UNSAFE.getLongVolatile(null, consumerIndexAddress);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long nextCorrelation() {
        return UNSAFE.getAndAddLong(null, correlationAddr, 1L);
    }

    private void putPaddingRecord(final int offset, final int paddingSize) {
        final long headerAddr = dataBaseAddress + offset;
        UNSAFE.putLongVolatile(null, headerAddr, packHeader(paddingSize, PADDING_TYPE));
    }

    private void putRecord(final int offset,
                           final ByteBuffer src,
                           final int srcOffset,
                           final int length,
                           final int typeId,
                           final int recordSize) {
        final long headerAddr = dataBaseAddress + offset;
        final long payloadAddr = headerAddr + RECORD_HEADER_SIZE;
        final long inProgress = packHeader(-recordSize, typeId);

        UNSAFE.putLong(headerAddr, inProgress);

        if (src.isDirect()) {
            final long srcAddr = UNSAFE.getLong(src, BUFFER_ADDRESS_FIELD_OFFSET) + srcOffset;
            UNSAFE.copyMemory(srcAddr, payloadAddr, length);
        } else {
            for (int i = 0; i < length; i++) {
                UNSAFE.putByte(payloadAddr + i, src.get(srcOffset + i));
            }
        }

        UNSAFE.storeFence();

        UNSAFE.putLongVolatile(null, headerAddr, packHeader(recordSize, typeId));
    }

    private static int alignUp(final int value) {
        return (value + (RECORD_ALIGNMENT - 1)) & -RECORD_ALIGNMENT;
    }

    private static long packHeader(final int size, final int typeId) {
        return (((long) size) << 32) | (typeId & 0xFFFFFFFFL);
    }

    private static int unpackSize(final long header) {
        return (int) (header >> 32);
    }

    private static int unpackType(final long header) {
        return (int) header;
    }
}
