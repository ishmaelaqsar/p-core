package dev.aqsar.pcore.concurrent.ringbuffer;

import dev.aqsar.pcore.concurrent.UnsafeBuffer;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * <h2>Single-Producer / Single-Consumer Ring Buffer</h2>
 *
 * <p>This implementation is a lock-free, bounded, single-producer/single-consumer
 * ring buffer optimized for extremely low latency. It uses {@link sun.misc.Unsafe}
 * to access the native memory of a {@link java.nio.ByteBuffer} directly.</p>
 *
 * <p>Implements the {@link RingBuffer} interface.</p>
 *
 * <h3>Memory Layout</h3>
 * <pre>
 * +----------------------+------------------+
 * |     Data Region      |   Metadata (128B)|
 * +----------------------+------------------+
 * </pre>
 */
@SuppressWarnings("restriction")
public final class SPSCRingBuffer implements RingBuffer {

    private static final int RECORD_HEADER_SIZE = 8;
    private static final int RECORD_ALIGNMENT = RECORD_HEADER_SIZE;
    private static final int PADDING_TYPE = -1;

    private static final int PRODUCER_POS_OFFSET = 0;
    private static final int PRODUCER_CACHE_OFFSET = 8;
    private static final int CONSUMER_POS_OFFSET = CACHE_LINE;
    private static final int CORRELATION_OFFSET = CACHE_LINE + 16;
    private static final int HEARTBEAT_OFFSET = CACHE_LINE + 24;

    private static final int INSUFFICIENT_SPACE = -2;

    private static final Unsafe UNSAFE;
    private static final long BUFFER_ADDRESS_FIELD_OFFSET;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);

            Field addrField = Buffer.class.getDeclaredField("address");
            addrField.setAccessible(true);
            BUFFER_ADDRESS_FIELD_OFFSET = UNSAFE.objectFieldOffset(addrField);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final ByteBuffer buffer;
    private final long baseAddress;
    private final int dataSize;
    private final int dataMask;
    private final int maxPayload;

    private final long producerPosAddr;
    private final long producerCacheAddr;
    private final long consumerPosAddr;
    private final long correlationAddr;
    private final long heartbeatAddr;

    // Flyweight buffer for the consumer
    private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer();

    // Reservation state: holds slot info between reserveSlot() and publish
    // Safe because single-producer guarantees no concurrent access
    private int reservedSlot;
    private long reservedNewProducerPos;
    private int reservedPaddingSlot;
    private int reservedPaddingSize;

    /**
     * Constructs an {@code SPSCRingBuffer} backed by the given direct {@link ByteBuffer}.
     *
     * @param directBuffer a direct buffer; capacity must be power-of-two + {@link #METADATA_SIZE}.
     * @throws IllegalArgumentException if the buffer is null, non-direct, or size invalid.
     */
    public SPSCRingBuffer(final ByteBuffer directBuffer) {
        if (directBuffer == null || !directBuffer.isDirect()) {
            throw new IllegalArgumentException("SPSCRingBuffer requires a direct ByteBuffer");
        }

        this.buffer = directBuffer;
        // get native address of direct buffer
        final long addr = UNSAFE.getLong(directBuffer, BUFFER_ADDRESS_FIELD_OFFSET);
        if (addr == 0L) {
            throw new IllegalStateException("Could not obtain direct buffer address");
        }
        this.baseAddress = addr;

        final int capacity = directBuffer.capacity();
        this.dataSize = validateSize(capacity);
        this.dataMask = dataSize - 1;
        this.maxPayload = dataSize - RECORD_HEADER_SIZE;

        final long metaBase = baseAddress + dataSize;
        this.producerPosAddr = metaBase + PRODUCER_POS_OFFSET;
        this.producerCacheAddr = metaBase + PRODUCER_CACHE_OFFSET;
        this.consumerPosAddr = metaBase + CONSUMER_POS_OFFSET;
        this.correlationAddr = metaBase + CORRELATION_OFFSET;
        this.heartbeatAddr = metaBase + HEARTBEAT_OFFSET;
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
    public boolean offer(final int typeId, final ByteBuffer src, final int srcOffset, final int len) {
        if (typeId < 1) {
            throw new IllegalArgumentException("typeId must be positive");
        }
        if (len < 0 || len > maxPayload) {
            throw new IllegalArgumentException("invalid length");
        }

        final int recordSize = len + RECORD_HEADER_SIZE;
        if (!reserveSlot(recordSize)) {
            return false;
        }

        // If wrapping, publish padding first (volatile write ensures visibility)
        if (reservedPaddingSize > 0) {
            final long paddingHeader = packHeader(reservedPaddingSize, PADDING_TYPE);
            UNSAFE.putLongVolatile(null, baseAddress + reservedPaddingSlot, paddingHeader);
        }

        final long headerAddr = baseAddress + reservedSlot;
        final long payloadAddr = headerAddr + RECORD_HEADER_SIZE;

        // Write payload (plain writes via Unsafe)
        if (src.isDirect()) {
            final long srcAddr = UNSAFE.getLong(src, BUFFER_ADDRESS_FIELD_OFFSET) + srcOffset;
            UNSAFE.copyMemory(srcAddr, payloadAddr, len);
        } else {
            // For heap buffers, copy byte by byte
            for (int i = 0; i < len; i++) {
                UNSAFE.putByte(payloadAddr + i, src.get(srcOffset + i));
            }
        }

        // Store fence ensures all plain writes are visible before volatile write
        UNSAFE.storeFence();

        // Publish header (volatile write)
        final long published = packHeader(recordSize, typeId);
        UNSAFE.putLongVolatile(null, headerAddr, published);

        // Update producer position AFTER record is published (volatile write)
        // This ensures consumer only sees producerPos advance when data is ready
        UNSAFE.putLongVolatile(null, producerPosAddr, reservedNewProducerPos);

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int claim(final int typeId, final int len) {
        if (typeId < 1) {
            throw new IllegalArgumentException("typeId must be positive");
        }
        if (len < 0 || len > maxPayload) {
            throw new IllegalArgumentException("invalid length");
        }

        final int recordSize = len + RECORD_HEADER_SIZE;
        if (!reserveSlot(recordSize)) {
            return INSUFFICIENT_SPACE;
        }

        // If wrapping, publish padding first (volatile write ensures visibility)
        if (reservedPaddingSize > 0) {
            final long paddingHeader = packHeader(reservedPaddingSize, PADDING_TYPE);
            UNSAFE.putLongVolatile(null, baseAddress + reservedPaddingSlot, paddingHeader);
        }

        // Write in-progress header (plain write via ByteBuffer)
        // This is safe because publish() will use Unsafe.storeFence()
        final long inProgress = packHeader(-recordSize, typeId);
        buffer.putLong(reservedSlot, inProgress);

        return reservedSlot + RECORD_HEADER_SIZE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(final int payloadOffset) {
        final int slot = payloadOffset - RECORD_HEADER_SIZE;
        if (slot < 0 || slot >= dataSize) {
            throw new IllegalArgumentException("invalid offset");
        }

        // Read the in-progress header (plain read via ByteBuffer)
        final long packed = buffer.getLong(slot);
        final int recSize = unpackSize(packed);
        if (recSize >= 0) {
            throw new IllegalStateException("slot already published");
        }
        final int typeId = unpackType(packed);

        // Ensure any user writes to payload (which *must* be via ByteBuffer)
        // are visible before we publish.
        UNSAFE.storeFence();

        // flip sign to publish
        final long published = packHeader(-recSize, typeId);
        UNSAFE.putLongVolatile(null, baseAddress + slot, published);

        // Update producer position AFTER record is published (volatile write)
        UNSAFE.putLongVolatile(null, producerPosAddr, reservedNewProducerPos);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void abandon(final int payloadOffset) {
        final int slot = payloadOffset - RECORD_HEADER_SIZE;
        if (slot < 0 || slot >= dataSize) {
            throw new IllegalArgumentException("invalid offset");
        }

        final long packed = buffer.getLong(slot);
        final int recSize = unpackSize(packed);
        if (recSize >= 0) {
            throw new IllegalStateException("slot already published");
        }

        UNSAFE.storeFence();

        final long padded = packHeader(-recSize, PADDING_TYPE);
        UNSAFE.putLongVolatile(null, baseAddress + slot, padded);

        // Update producer position AFTER record is published (volatile write)
        UNSAFE.putLongVolatile(null, producerPosAddr, reservedNewProducerPos);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int poll(final RingBuffer.MessageConsumer consumer, final int limit) {
        int count = 0;
        long pos = UNSAFE.getLongVolatile(null, consumerPosAddr);
        final long producerPos = UNSAFE.getLongVolatile(null, producerPosAddr);
        long consumedBytes = 0;
        final long available = producerPos - pos;

        try {
            while (consumedBytes < available && count < limit) {
                final long currentHead = pos + consumedBytes;
                final int slot = (int) (currentHead & dataMask);
                final long headerAddr = baseAddress + slot;

                // This is the "acquire" read.
                final long header = UNSAFE.getLongVolatile(null, headerAddr);
                final int recordSize = unpackSize(header);

                if (recordSize <= 0) {
                    break; // Not published or in-progress
                }

                final int alignedRecordSize = alignUp(recordSize);
                if (consumedBytes + alignedRecordSize > available) {
                    break; // Read past producer
                }

                consumedBytes += alignedRecordSize;

                // Handle wrap padding (which SPSC producer writes)
                if (slot + alignedRecordSize > dataSize) {
                    continue; // This was a padding record, just continue
                }

                final int typeId = unpackType(header);
                if (typeId != PADDING_TYPE) {
                    final int payloadLen = recordSize - RECORD_HEADER_SIZE;
                    final long payloadAddr = baseAddress + slot + RECORD_HEADER_SIZE;

                    // Wrap the payload address and pass to consumer
                    consumer.accept(typeId, unsafeBuffer.wrap(payloadAddr, payloadLen));
                    ++count;
                }
            }
        } finally {
            if (consumedBytes > 0) {
                UNSAFE.putLongVolatile(null, consumerPosAddr, pos + consumedBytes);
            }
        }
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int poll(final RingBuffer.MessageConsumer consumer) {
        return poll(consumer, Integer.MAX_VALUE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int controlledPoll(final RingBuffer.ControlledConsumer consumer, final int limit) {
        int count = 0;
        long lastCommittedPos = UNSAFE.getLongVolatile(null, consumerPosAddr);
        final long producerPos = UNSAFE.getLongVolatile(null, producerPosAddr);

        long currentPos = lastCommittedPos;
        final long available = producerPos - currentPos;
        long bytesRead = 0;

        try {
            while (bytesRead < available && count < limit) {
                final int slot = (int) (currentPos & dataMask);
                final long headerAddr = baseAddress + slot;

                final long header = UNSAFE.getLongVolatile(null, headerAddr);
                final int recordSize = unpackSize(header);

                if (recordSize <= 0) {
                    break; // Not ready
                }

                final int alignedRecordSize = alignUp(recordSize);
                if (bytesRead + alignedRecordSize > available) {
                    break; // Past producer
                }

                bytesRead += alignedRecordSize; // Tentatively read

                if (slot + alignedRecordSize > dataSize) {
                    currentPos += alignedRecordSize;
                    lastCommittedPos = currentPos; // Commit padding
                    continue;
                }

                final int typeId = unpackType(header);
                if (typeId == PADDING_TYPE) {
                    currentPos += alignedRecordSize;
                    lastCommittedPos = currentPos; // Commit padding
                    continue;
                }

                final int payloadLen = recordSize - RECORD_HEADER_SIZE;
                final long payloadAddr = baseAddress + slot + RECORD_HEADER_SIZE;

                final ConsumerAction action = consumer.accept(typeId, unsafeBuffer.wrap(payloadAddr, payloadLen));

                if (action == ConsumerAction.ABORT) {
                    break;
                }

                currentPos += alignedRecordSize;
                lastCommittedPos = currentPos;
                ++count;

                if (action == ConsumerAction.BREAK) {
                    break;
                }
            }
        } finally {
            if (lastCommittedPos != UNSAFE.getLongVolatile(null, consumerPosAddr)) {
                UNSAFE.putLongVolatile(null, consumerPosAddr, lastCommittedPos);
            }
        }
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int controlledPoll(final RingBuffer.ControlledConsumer consumer) {
        return controlledPoll(consumer, Integer.MAX_VALUE);
    }

    /**
     * Reserves space for a record but does NOT update the producer position.
     * Stores reservation info in instance fields (no allocations, single-producer safe).
     * Producer position will be updated only after the record is fully published.
     *
     * @param recordSize total size of record (header + payload)
     * @return true if space was reserved, false if insufficient space
     */
    private boolean reserveSlot(final int recordSize) {
        final int aligned = alignUp(recordSize);
        final int required = aligned;

        long consumerPos = UNSAFE.getLong(producerCacheAddr);
        long producerPos = UNSAFE.getLongVolatile(null, producerPosAddr);

        int free = dataSize - (int) (producerPos - consumerPos);
        if (required > free) {
            consumerPos = UNSAFE.getLongVolatile(null, consumerPosAddr);
            free = dataSize - (int) (producerPos - consumerPos);
            if (required > free) {
                return false;
            }

            UNSAFE.putLong(producerCacheAddr, consumerPos);
        }

        int padding = 0;
        final int slot = (int) producerPos & dataMask;
        final int remaining = dataSize - slot;
        int writeSlot = slot;
        long newProducerPos = producerPos + aligned;

        if (required > remaining) {
            if (RECORD_HEADER_SIZE > remaining) {
                return false; // cannot fit header at end
            }

            writeSlot = 0;
            padding = remaining;
            newProducerPos = producerPos + remaining + aligned;

            int consumerIdx = (int) consumerPos & dataMask;
            if (required > consumerIdx) {
                consumerPos = UNSAFE.getLongVolatile(null, consumerPosAddr);
                int newConsumerIdx = (int) consumerPos & dataMask;
                if (required > newConsumerIdx) {
                    return false;
                }

                UNSAFE.putLong(producerCacheAddr, consumerPos);
            }
        }

        // Store reservation info (no allocation, single-producer safe)
        reservedSlot = writeSlot;
        reservedNewProducerPos = newProducerPos;
        if (padding > 0) {
            reservedPaddingSlot = slot;
            reservedPaddingSize = padding;
        } else {
            reservedPaddingSize = 0;
        }

        // zero only header ints of next record (prevent old header being interpreted)
        final int nextSlot = writeSlot + aligned;
        if (nextSlot < dataSize) {
            UNSAFE.putLong(baseAddress + nextSlot, 0L); // Zero the next *header*
        }

        return true;
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

    private static int validateSize(final int bufferCapacity) {
        final int dataSize = bufferCapacity - METADATA_SIZE;
        if (dataSize < RECORD_HEADER_SIZE * 2) {
            throw new IllegalArgumentException("capacity too small: " + bufferCapacity);
        }
        if ((dataSize & (dataSize - 1)) != 0) {
            throw new IllegalArgumentException("data size must be power of 2: " + dataSize);
        }
        return dataSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long producerSeq() {
        return UNSAFE.getLongVolatile(null, producerPosAddr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long consumerSeq() {
        return UNSAFE.getLongVolatile(null, consumerPosAddr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long nextCorrelation() {
        return UNSAFE.getAndAddLong(null, correlationAddr, 1L);
    }

    /**
     * Updates the heartbeat timestamp (volatile store).
     */
    public void markHeartbeat(final long timestamp) {
        UNSAFE.putLongVolatile(null, heartbeatAddr, timestamp);
    }

    /**
     * @return last heartbeat timestamp.
     */
    public long readHeartbeat() {
        return UNSAFE.getLongVolatile(null, heartbeatAddr);
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
     */
    @Override
    public int utilization() {
        long consBefore;
        long prod;
        long consAfter = UNSAFE.getLongVolatile(null, consumerPosAddr);
        do {
            consBefore = consAfter;
            prod = UNSAFE.getLongVolatile(null, producerPosAddr);
            consAfter = UNSAFE.getLongVolatile(null, consumerPosAddr);
        } while (consAfter != consBefore);

        final long used = prod - consAfter;
        return (int) Math.min(dataSize, Math.max(0, used));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        UNSAFE.putLongVolatile(null, producerPosAddr, 0L);
        UNSAFE.putLongVolatile(null, consumerPosAddr, 0L);
        UNSAFE.putLongVolatile(null, producerCacheAddr, 0L);
    }
}
