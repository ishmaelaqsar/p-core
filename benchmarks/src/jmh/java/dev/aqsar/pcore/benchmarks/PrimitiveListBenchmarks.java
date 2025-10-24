package dev.aqsar.pcore.benchmarks;

import dev.aqsar.pcore.collections.LongList;
import org.agrona.collections.LongArrayList;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsAppend = {"-XX:+UseG1GC", "-Xms2g", "-Xmx2g"})
@State(Scope.Thread)
public class PrimitiveListBenchmarks {

    @Param({"10", "100", "1000", "10000"})
    private int size;

    private LongList myLongList;
    private LongArrayList agronaLongList;
    private ArrayList<Long> boxedLongList;

    private long[] longArray;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(42);

        // Setup long data
        longArray = new long[size];
        for (int i = 0; i < size; i++) {
            longArray[i] = random.nextLong();
        }
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        // LongList variants
        myLongList = LongList.builder().disableNullValue().initialCapacity(size).build();
        agronaLongList = new LongArrayList(size, Long.MIN_VALUE);
        boxedLongList = new ArrayList<>(size);
    }

    // ==================== Add Benchmarks ====================

    @Benchmark
    public LongList myLongList_sequentialAdd() {
        for (int i = 0; i < size; i++) {
            myLongList.add(longArray[i]);
        }
        return myLongList;
    }

    @Benchmark
    public LongArrayList agronaLong_sequentialAdd() {
        for (int i = 0; i < size; i++) {
            agronaLongList.addLong(longArray[i]);
        }
        return agronaLongList;
    }

    @Benchmark
    public ArrayList<Long> boxedLong_sequentialAdd() {
        for (int i = 0; i < size; i++) {
            boxedLongList.add(longArray[i]);
        }
        return boxedLongList;
    }

    // ==================== Random Access Benchmarks ====================

    @State(Scope.Thread)
    public static class PopulatedState {
        @Param({"10", "100", "1000", "10000"})
        private int size;

        private LongList myLongList;
        private LongArrayList agronaLongList;
        private ArrayList<Long> boxedLongList;

        private int[] accessPattern;

        @Setup(Level.Trial)
        public void setup() {
            Random random = new Random(42);

            // Create access pattern
            accessPattern = new int[1000];
            for (int i = 0; i < accessPattern.length; i++) {
                accessPattern[i] = random.nextInt(size);
            }

            // Populate LongList
            myLongList = LongList.builder().disableNullValue().disableIteratorPool().initialCapacity(size).build();
            for (int i = 0; i < size; i++) {
                myLongList.add(random.nextLong());
            }

            // Populate Agrona LongList
            agronaLongList = new LongArrayList(size, Long.MIN_VALUE);
            for (int i = 0; i < size; i++) {
                agronaLongList.addLong(random.nextLong());
            }

            // Populate boxed LongList
            boxedLongList = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                boxedLongList.add(random.nextLong());
            }
        }
    }

    @Benchmark
    public void myLongList_randomGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.accessPattern.length; i++) {
            bh.consume(state.myLongList.getLong(state.accessPattern[i]));
        }
    }

    @Benchmark
    public void agronaLong_randomGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.accessPattern.length; i++) {
            bh.consume(state.agronaLongList.getLong(state.accessPattern[i]));
        }
    }

    @Benchmark
    public void boxedLong_randomGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.accessPattern.length; i++) {
            bh.consume(state.boxedLongList.get(state.accessPattern[i]));
        }
    }

    @Benchmark
    public void myLongList_sequentialGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.myLongList.getLong(i));
        }
    }

    @Benchmark
    public void agronaLong_sequentialGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.agronaLongList.getLong(i));
        }
    }

    @Benchmark
    public void boxedLong_sequentialGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.boxedLongList.get(i));
        }
    }

    // ==================== Search Benchmarks ====================

    @Benchmark
    public void myLongList_indexOf_hit(PopulatedState state, Blackhole bh) {
        // Search for middle element
        long target = state.myLongList.getLong(state.size / 2);
        bh.consume(state.myLongList.indexOf(target));
    }

    @Benchmark
    public void agronaLong_indexOf_hit(PopulatedState state, Blackhole bh) {
        long target = state.agronaLongList.getLong(state.size / 2);
        bh.consume(state.agronaLongList.indexOf(target));
    }

    @Benchmark
    public void myLongList_indexOf_miss(PopulatedState state, Blackhole bh) {
        // Search for non-existent value
        bh.consume(state.myLongList.indexOf(Long.MAX_VALUE));
    }

    @Benchmark
    public void agronaLong_indexOf_miss(PopulatedState state, Blackhole bh) {
        bh.consume(state.agronaLongList.indexOf(Long.MAX_VALUE));
    }

    @Benchmark
    public void myLongList_contains(PopulatedState state, Blackhole bh) {
        long target = state.myLongList.getLong(state.size / 2);
        bh.consume(state.myLongList.contains(target));
    }

    @Benchmark
    public void agronaInt_contains(PopulatedState state, Blackhole bh) {
        long target = state.agronaLongList.getLong(state.size / 2);
        bh.consume(state.agronaLongList.containsLong(target));
    }

    // ==================== Iterator Benchmarks ====================

    @Benchmark
    public void myLongList_iteration(PopulatedState state, Blackhole bh) {
        try (var it = state.myLongList.borrowIterator()) {
            while (it.hasNext()) {
                bh.consume(it.next());
            }
        }
    }

    @Benchmark
    public void agronaLong_iteration(PopulatedState state, Blackhole bh) {
        for (final long integer : state.agronaLongList) {
            bh.consume(integer);
        }
    }

    // ==================== Memory Footprint Benchmarks ====================

    @State(Scope.Thread)
    public static class MemoryState {
        @Param({"1000", "10000", "100000"})
        private int size;

        @Setup(Level.Trial)
        public void setup() {
            // Force GC before measurements
            System.gc();
            System.gc();
        }
    }

    @Benchmark
    public LongList myLongList_memoryFootprint(MemoryState state) {
        LongList list = LongList.builder().disableNullValue().disableIteratorPool().initialCapacity(state.size).build();
        for (int i = 0; i < state.size; i++) {
            list.add(i);
        }
        return list;
    }

    @Benchmark
    public LongArrayList agronaInt_memoryFootprint(MemoryState state) {
        LongArrayList list = new LongArrayList(state.size, Long.MIN_VALUE);
        for (int i = 0; i < state.size; i++) {
            list.addLong(i);
        }
        return list;
    }

    @Benchmark
    public ArrayList<Long> boxedInt_memoryFootprint(MemoryState state) {
        ArrayList<Long> list = new ArrayList<>(state.size);
        for (int i = 0; i < state.size; i++) {
            list.add((long) i);
        }
        return list;
    }

    // ==================== Mixed Workload Benchmark ====================

    @Benchmark
    public void myLongList_mixedWorkload(PopulatedState state, Blackhole bh) {
        LongList list = LongList.builder().disableNullValue().disableIteratorPool().initialCapacity(100).build();

        // Add
        for (int i = 0; i < 100; i++) {
            list.add(state.accessPattern[i % state.accessPattern.length]);
        }

        // Search
        for (int i = 0; i < 50; i++) {
            bh.consume(list.indexOf(i));
        }

        // Random access
        for (int i = 0; i < 100; i++) {
            bh.consume(list.getLong(i % list.size()));
        }

        // Modify
        for (int i = 0; i < 50; i++) {
            list.set(i, i * 2);
        }
    }

    @Benchmark
    public void agronaLongList_mixedWorkload(PopulatedState state, Blackhole bh) {
        LongArrayList list = new LongArrayList(100, Long.MIN_VALUE);

        // Add
        for (int i = 0; i < 100; i++) {
            list.addLong(state.accessPattern[i % state.accessPattern.length]);
        }

        // Search
        for (int i = 0; i < 50; i++) {
            bh.consume(list.indexOf(i));
        }

        // Random access
        for (int i = 0; i < 100; i++) {
            bh.consume(list.getLong(i % list.size()));
        }

        // Modify
        for (int i = 0; i < 50; i++) {
            list.setLong(i, i * 2);
        }
    }
}