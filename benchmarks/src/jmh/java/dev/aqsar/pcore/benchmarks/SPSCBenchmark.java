package dev.aqsar.pcore.benchmarks;

import dev.aqsar.pcore.concurrent.ringbuffer.RingBuffer;
import dev.aqsar.pcore.concurrent.ringbuffer.SPSCRingBuffer;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

/**
 * A simple allocator stub to make the benchmark self-contained.
 * Matches the allocator used in the user's stress test.
 */
class RingBufferAllocator {
    public static ByteBuffer allocate(int capacity) {
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
    }
}

/**
 * JMH benchmark comparing SPSCRingBuffer and Agrona's OneToOneRingBuffer
 * using a ping-pong latency test.
 */
@State(Scope.Group)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SPSCBenchmark {

    // --- Benchmark Parameters ---
    @Param({"131072"}) // 128 KiB data size (1 << 17)
    public int dataSize;

    private static final int MSG_TYPE_ID = 1;
    private static final int PAYLOAD_SIZE = 8; // 8 bytes for a long

    // --- State for "My" Ring Buffer ---
    private SPSCRingBuffer myPingBuffer;
    private SPSCRingBuffer myPongBuffer;
    private ByteBuffer myPingWriteBuffer; // Backing buffer for ping
    private ByteBuffer myPongWriteBuffer; // Backing buffer for pong

    // --- State for "Agrona" Ring Buffer ---
    private OneToOneRingBuffer agronaPingBuffer;
    private OneToOneRingBuffer agronaPongBuffer;
    private UnsafeBuffer agronaPingAtomic; // Backing buffer for ping
    private UnsafeBuffer agronaPongAtomic; // Backing buffer for pong

    /**
     * Holds per-thread state.
     * Crucially, this holds the value to be read/written
     * and the direct buffer for copy-in benchmarks.
     */
    @State(Scope.Thread)
    public static class ThreadState {
        long value; // The value we expect to read
        final ByteBuffer copyInBuffer = RingBufferAllocator.allocate(PAYLOAD_SIZE);

        // Agrona's DirectBuffer wrapper for the copy-in buffer
        final UnsafeBuffer agronaCopyInBuf = new UnsafeBuffer(copyInBuffer);

        // --- Read Handlers ---
        // These handlers update the thread's 'value' field when a message is read.

        // Handler for MyRingBuffer.poll()
        final RingBuffer.MessageConsumer myConsumer = (id, buf) -> {
            this.value = buf.getLong(0);
        };

        // Handler for Agrona.read()
        final MessageHandler agronaHandler = (msgTypeId, buffer, index, length) -> {
            this.value = buffer.getLong(index);
        };
    }

    @Setup(Level.Trial)
    public void setupBuffers() {
        // --- Setup MySPSCRingBuffer ---
        // (dataSize + METADATA_SIZE)
        int totalCapacityMyRb = dataSize + RingBuffer.METADATA_SIZE;
        myPingBuffer = new SPSCRingBuffer(RingBufferAllocator.allocate(totalCapacityMyRb));
        myPongBuffer = new SPSCRingBuffer(RingBufferAllocator.allocate(totalCapacityMyRb));
        myPingWriteBuffer = myPingBuffer.underlyingBuffer();
        myPongWriteBuffer = myPongBuffer.underlyingBuffer();

        // --- Setup Agrona OneToOneRingBuffer ---
        // (dataSize + TRAILER_LENGTH)
        int totalCapacityAgrona = dataSize + RingBufferDescriptor.TRAILER_LENGTH;
        agronaPingAtomic = new UnsafeBuffer(RingBufferAllocator.allocate(totalCapacityAgrona));
        agronaPongAtomic = new UnsafeBuffer(RingBufferAllocator.allocate(totalCapacityAgrona));
        agronaPingBuffer = new OneToOneRingBuffer(agronaPingAtomic);
        agronaPongBuffer = new OneToOneRingBuffer(agronaPongAtomic);
    }

    // ========================================================================
    // Benchmark 1: MySPSCRingBuffer (Zero-Copy: claim/publish)
    // ========================================================================

    @Group("myRb_ZeroCopy")
    @GroupThreads(1)
    @Benchmark
    public void myRb_ZeroCopy_Pinger(ThreadState state) {
        // 1. Send the ping
        int offset;
        while ((offset = myPingBuffer.claim(MSG_TYPE_ID, PAYLOAD_SIZE)) < 0) {
            Thread.onSpinWait();
        }
        myPingWriteBuffer.putLong(offset, state.value);
        myPingBuffer.publish(offset);

        // 2. Wait for the pong
        while (myPongBuffer.poll(state.myConsumer, 1) == 0) {
            Thread.onSpinWait();
        }
    }

    @Group("myRb_ZeroCopy")
    @GroupThreads(1)
    @Benchmark
    public void myRb_ZeroCopy_Ponger(ThreadState state) {
        // 1. Wait for the ping
        while (myPingBuffer.poll(state.myConsumer, 1) == 0) {
            Thread.onSpinWait();
        }

        // 2. Send the pong
        int offset;
        while ((offset = myPongBuffer.claim(MSG_TYPE_ID, PAYLOAD_SIZE)) < 0) {
            Thread.onSpinWait();
        }
        myPongWriteBuffer.putLong(offset, state.value); // Echo the value back
        myPongBuffer.publish(offset);
    }

    // ========================================================================
    // Benchmark 2: Agrona (Zero-Copy: tryClaim/commit)
    // ========================================================================

    @Group("agrona_ZeroCopy")
    @GroupThreads(1)
    @Benchmark
    public void agrona_ZeroCopy_Pinger(ThreadState state) {
        // 1. Send the ping
        int offset;
        while ((offset = (int) agronaPingBuffer.tryClaim(MSG_TYPE_ID, PAYLOAD_SIZE)) < 0) {
            Thread.onSpinWait();
        }
        agronaPingAtomic.putLong(offset, state.value);
        agronaPingBuffer.commit(offset);

        // 2. Wait for the pong
        while (agronaPongBuffer.read(state.agronaHandler, 1) == 0) {
            Thread.onSpinWait();
        }
    }

    @Group("agrona_ZeroCopy")
    @GroupThreads(1)
    @Benchmark
    public void agrona_ZeroCopy_Ponger(ThreadState state) {
        // 1. Wait for the ping
        while (agronaPingBuffer.read(state.agronaHandler, 1) == 0) {
            Thread.onSpinWait();
        }

        // 2. Send the pong
        int offset;
        while ((offset = (int) agronaPongBuffer.tryClaim(MSG_TYPE_ID, PAYLOAD_SIZE)) < 0) {
            Thread.onSpinWait();
        }
        agronaPongAtomic.putLong(offset, state.value); // Echo the value back
        agronaPongBuffer.commit(offset);
    }

    // ========================================================================
    // Benchmark 3: MySPSCRingBuffer (Copy-In: offer)
    // ========================================================================

    @Group("myRb_CopyIn")
    @GroupThreads(1)
    @Benchmark
    public void myRb_CopyIn_Pinger(ThreadState state) {
        // 1. Send the ping
        state.copyInBuffer.putLong(0, state.value);
        while (!myPingBuffer.offer(MSG_TYPE_ID, state.copyInBuffer, 0, PAYLOAD_SIZE)) {
            Thread.onSpinWait();
        }

        // 2. Wait for the pong
        while (myPongBuffer.poll(state.myConsumer, 1) == 0) {
            Thread.onSpinWait();
        }
    }

    @Group("myRb_CopyIn")
    @GroupThreads(1)
    @Benchmark
    public void myRb_CopyIn_Ponger(ThreadState state) {
        // 1. Wait for the ping
        while (myPingBuffer.poll(state.myConsumer, 1) == 0) {
            Thread.onSpinWait();
        }

        // 2. Send the pong
        state.copyInBuffer.putLong(0, state.value);
        while (!myPongBuffer.offer(MSG_TYPE_ID, state.copyInBuffer, 0, PAYLOAD_SIZE)) {
            Thread.onSpinWait();
        }
    }

    // ========================================================================
    // Benchmark 4: Agrona (Copy-In: write)
    // ========================================================================

    @Group("agrona_CopyIn")
    @GroupThreads(1)
    @Benchmark
    public void agrona_CopyIn_Pinger(ThreadState state) {
        // 1. Send the ping
        state.agronaCopyInBuf.putLong(0, state.value);
        while (!agronaPingBuffer.write(MSG_TYPE_ID, state.agronaCopyInBuf, 0, PAYLOAD_SIZE)) {
            Thread.onSpinWait();
        }

        // 2. Wait for the pong
        while (agronaPongBuffer.read(state.agronaHandler, 1) == 0) {
            Thread.onSpinWait();
        }
    }

    @Group("agrona_CopyIn")
    @GroupThreads(1)
    @Benchmark
    public void agrona_CopyIn_Ponger(ThreadState state) {
        // 1. Wait for the ping
        while (agronaPingBuffer.read(state.agronaHandler, 1) == 0) {
            Thread.onSpinWait();
        }

        // 2. Send the pong
        state.agronaCopyInBuf.putLong(0, state.value);
        while (!agronaPongBuffer.write(MSG_TYPE_ID, state.agronaCopyInBuf, 0, PAYLOAD_SIZE)) {
            Thread.onSpinWait();
        }
    }
}
