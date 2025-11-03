package dev.aqsar.pcore.concurrent.ringbuffer;

import dev.aqsar.pcore.concurrent.ReadableBuffer;

import java.nio.ByteBuffer;

/**
 * Common interface for lock-free, message-passing ring buffers using
 * a direct {@link java.nio.ByteBuffer} as the underlying storage.
 *
 * <p>Messages consist of a type identifier and a payload.</p>
 */
public interface RingBuffer {

    // --- Constants ---

    /**
     * Cache line size in bytes (assumed).
     */
    int CACHE_LINE = 64;

    /**
     * Total size of the metadata region, padded to 2 cache lines.
     */
    int METADATA_SIZE = CACHE_LINE * 2;

    /**
     * Minimum data region size (must hold at least 2 headers).
     */
    int MIN_SIZE = 8 * 2; // RECORD_HEADER_SIZE * 2

    // -------------------------------------------------------------------------
    // Nested Consumer Interfaces
    // -------------------------------------------------------------------------

    /**
     * Callback for consuming a single message.
     */
    @FunctionalInterface
    interface MessageConsumer {
        /**
         * @param typeId message type identifier
         * @param buffer a read-only buffer view of the message payload
         */
        void accept(final int typeId, final ReadableBuffer buffer);
    }

    /**
     * Callback for consuming messages with fine-grained flow control.
     */
    @FunctionalInterface
    interface ControlledConsumer {
        /**
         * @param typeId message type identifier
         * @param buffer a read-only buffer view of the message payload
         * @return a {@link ConsumerAction} to control the poll loop
         */
        ConsumerAction accept(final int typeId, final ReadableBuffer buffer);
    }

    /**
     * Defines flow control actions for {@link ControlledConsumer}.
     */
    enum ConsumerAction {
        /**
         * Continue processing.
         */
        CONTINUE,
        /**
         * Stop processing and commit progress.
         */
        BREAK,
        /**
         * Stop processing and roll back the current message.
         */
        ABORT,
        /**
         * Commit progress so far and continue processing.
         */
        COMMIT
    }

    // -------------------------------------------------------------------------
    // Write API
    // -------------------------------------------------------------------------

    /**
     * Offers a message by copying its payload into the ring buffer.
     *
     * @param typeId    positive message type identifier.
     * @param src       source buffer.
     * @param srcOffset offset within {@code src}.
     * @param len       number of bytes to copy.
     * @return {@code true} if successfully written; {@code false} if insufficient space.
     */
    boolean offer(final int typeId, final ByteBuffer src, final int srcOffset, final int len);

    /**
     * Claims space for a zero-copy write.
     * <p>
     * NOTE: The client is responsible for writing to the underlying
     * {@link #underlyingBuffer()} at the returned offset. This write
     * *must* be done using the {@link ByteBuffer} API (e.g., `buf.putByte(offset, val)`)
     * to ensure memory visibility with the `publish` call.
     *
     * @param typeId message type.
     * @param len    payload length.
     * @return payload offset to write into, or a negative value if insufficient space.
     */
    int claim(final int typeId, final int len);

    /**
     * Publishes a previously claimed record, making it visible to the consumer.
     *
     * @param payloadOffset offset returned by {@link #claim(int, int)}.
     * @throws IllegalArgumentException if the offset is invalid
     * @throws IllegalStateException    if the slot is not in a 'claimed' state
     */
    void publish(final int payloadOffset);

    /**
     * Abandons a claimed record (converts it into padding).
     *
     * @param payloadOffset offset returned by {@link #claim(int, int)}.
     * @throws IllegalArgumentException if the offset is invalid
     * @throws IllegalStateException    if the slot is not in a 'claimed' state
     */
    void abandon(final int payloadOffset);

    // -------------------------------------------------------------------------
    // Read API
    // -------------------------------------------------------------------------

    /**
     * Polls and delivers up to {@code limit} messages to the given consumer.
     *
     * @param consumer message handler callback.
     * @param limit    maximum number of messages to consume.
     * @return number of messages consumed.
     */
    int poll(final MessageConsumer consumer, final int limit);

    /**
     * Convenience overload: polls all available messages.
     */
    default int poll(final MessageConsumer consumer) {
        return poll(consumer, Integer.MAX_VALUE);
    }

    /**
     * Controlled poll (commit/abort/continue semantics).
     *
     * @param consumer message handler callback.
     * @param limit    maximum number of messages to consume.
     * @return number of messages consumed.
     */
    int controlledPoll(final ControlledConsumer consumer, final int limit);

    /**
     * Convenience overload: polls all available messages.
     */
    default int controlledPoll(ControlledConsumer consumer) {
        return controlledPoll(consumer, Integer.MAX_VALUE);
    }

    // -------------------------------------------------------------------------
    // Utility API
    // -------------------------------------------------------------------------

    /**
     * @return total usable data region capacity (bytes).
     */
    int size();

    /**
     * @return maximum payload length per record (bytes).
     */
    int maxPayloadLength();

    /**
     * Clears the buffer to an empty state (not thread-safe).
     */
    void clear();

    /**
     * @return number of bytes currently filled in the ring buffer.
     */
    int utilization();

    /**
     * @return the underlying direct buffer.
     */
    ByteBuffer underlyingBuffer();

    /**
     * @return current producer sequence (absolute byte position).
     */
    long producerSeq();

    /**
     * @return current consumer sequence (absolute byte position).
     */
    long consumerSeq();

    /**
     * Atomically increments and returns a unique correlation identifier.
     * <p>
     * This method is thread-safe for multiple producers.
     *
     * @return a unique, sequential correlation ID.
     */
    long nextCorrelation();
}
