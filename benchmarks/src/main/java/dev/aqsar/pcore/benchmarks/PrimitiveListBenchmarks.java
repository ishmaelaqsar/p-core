package dev.aqsar.pcore.benchmarks;

import dev.aqsar.pcore.collections.IntList;
import dev.aqsar.pcore.collections.LongList;
import org.agrona.collections.IntArrayList;
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

    private IntList intList;
    private IntArrayList agronaIntList;
    private ArrayList<Integer> boxedIntList;

    private LongList longList;
    private LongArrayList agronaLongList;
    private ArrayList<Long> boxedLongList;

    private int[] intArray;
    private long[] longArray;

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(42);

        // Setup int data
        intArray = new int[size];
        for (int i = 0; i < size; i++) {
            intArray[i] = random.nextInt();
        }

        // Setup long data
        longArray = new long[size];
        for (int i = 0; i < size; i++) {
            longArray[i] = random.nextLong();
        }
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        // IntList variants
        intList = IntList.builder().disableNullValue().disableIteratorPool().initialCapacity(size).build();
        agronaIntList = new IntArrayList(size, Integer.MIN_VALUE);
        boxedIntList = new ArrayList<>(size);

        // LongList variants
        longList = LongList.builder().disableNullValue().disableIteratorPool().initialCapacity(size).build();
        agronaLongList = new LongArrayList(size, Long.MIN_VALUE);
        boxedLongList = new ArrayList<>(size);
    }

    // ==================== Add Benchmarks ====================

    @Benchmark
    public IntList intList_sequentialAdd() {
        for (int i = 0; i < size; i++) {
            intList.addInt(intArray[i]);
        }
        return intList;
    }

    @Benchmark
    public IntArrayList agronaInt_sequentialAdd() {
        for (int i = 0; i < size; i++) {
            agronaIntList.add(intArray[i]);
        }
        return agronaIntList;
    }

    @Benchmark
    public ArrayList<Integer> boxedInt_sequentialAdd() {
        for (int i = 0; i < size; i++) {
            boxedIntList.add(intArray[i]);
        }
        return boxedIntList;
    }

    @Benchmark
    public LongList longList_sequentialAdd() {
        for (int i = 0; i < size; i++) {
            longList.addLong(longArray[i]);
        }
        return longList;
    }

    @Benchmark
    public LongArrayList agronaLong_sequentialAdd() {
        for (int i = 0; i < size; i++) {
            agronaLongList.add(longArray[i]);
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

        private IntList intList;
        private IntArrayList agronaIntList;
        private ArrayList<Integer> boxedIntList;

        private LongList longList;
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

            // Populate IntList
            intList = IntList.builder().disableNullValue().disableIteratorPool().initialCapacity(size).build();
            for (int i = 0; i < size; i++) {
                intList.addInt(random.nextInt());
            }

            // Populate Agrona IntList
            agronaIntList = new IntArrayList(size, Integer.MIN_VALUE);
            for (int i = 0; i < size; i++) {
                agronaIntList.add(random.nextInt());
            }

            // Populate Boxed IntList
            boxedIntList = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                boxedIntList.add(random.nextInt());
            }

            // Populate LongList
            longList = LongList.builder().disableNullValue().disableIteratorPool().initialCapacity(size).build();
            for (int i = 0; i < size; i++) {
                longList.addLong(random.nextLong());
            }

            // Populate Agrona LongList
            agronaLongList = new LongArrayList(size, Long.MIN_VALUE);
            for (int i = 0; i < size; i++) {
                agronaLongList.add(random.nextLong());
            }

            // Populate boxed LongList
            boxedLongList = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                boxedLongList.add(random.nextLong());
            }
        }
    }

    @Benchmark
    public void intList_randomGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.accessPattern.length; i++) {
            bh.consume(state.intList.getInt(state.accessPattern[i]));
        }
    }

    @Benchmark
    public void agronaInt_randomGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.accessPattern.length; i++) {
            bh.consume(state.agronaIntList.getInt(state.accessPattern[i]));
        }
    }

    @Benchmark
    public void boxedInt_randomGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.accessPattern.length; i++) {
            bh.consume(state.boxedIntList.get(state.accessPattern[i]));
        }
    }

    @Benchmark
    public void intList_sequentialGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.intList.getInt(i));
        }
    }

    @Benchmark
    public void agronaInt_sequentialGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.agronaIntList.getInt(i));
        }
    }

    @Benchmark
    public void boxedInt_sequentialGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.boxedIntList.get(i));
        }
    }

    @Benchmark
    public void longList_sequentialGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.longList.getLong(i));
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
    public void intList_indexOf_hit(PopulatedState state, Blackhole bh) {
        // Search for middle element
        int target = state.intList.getInt(state.size / 2);
        bh.consume(state.intList.indexOfInt(target));
    }

    @Benchmark
    public void agronaInt_indexOf_hit(PopulatedState state, Blackhole bh) {
        int target = state.agronaIntList.getInt(state.size / 2);
        bh.consume(state.agronaIntList.indexOf(target));
    }

    @Benchmark
    public void intList_indexOf_miss(PopulatedState state, Blackhole bh) {
        // Search for non-existent value
        bh.consume(state.intList.indexOfInt(Integer.MAX_VALUE));
    }

    @Benchmark
    public void agronaInt_indexOf_miss(PopulatedState state, Blackhole bh) {
        bh.consume(state.agronaIntList.indexOf(Integer.MAX_VALUE));
    }

    @Benchmark
    public void intList_contains(PopulatedState state, Blackhole bh) {
        int target = state.intList.getInt(state.size / 2);
        bh.consume(state.intList.containsInt(target));
    }

    @Benchmark
    public void agronaInt_contains(PopulatedState state, Blackhole bh) {
        int target = state.agronaIntList.getInt(state.size / 2);
        bh.consume(state.agronaIntList.contains(target));
    }

    // ==================== Iterator Benchmarks ====================

    @Benchmark
    public void intList_iteration(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.intList.getInt(i));
        }
    }

    @Benchmark
    public void intList_pooledIterator(PopulatedState state, Blackhole bh) {
        // This requires a list with pooling enabled
        IntList listWithPool = IntList.builder().disableNullValue().initialCapacity(state.size).build();

        // Populate
        for (int i = 0; i < state.size; i++) {
            listWithPool.addInt(state.intList.getInt(i));
        }

        IntList.IntListIterator iter = listWithPool.borrowIterator();
        if (iter != null) {
            try (iter) {
                while (iter.hasNext()) {
                    bh.consume(iter.nextInt());
                }
            }
        }
    }

    @Benchmark
    public void agronaInt_iteration(PopulatedState state, Blackhole bh) {
        for (final Integer integer : state.agronaIntList) {
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
    public IntList intList_memoryFootprint(MemoryState state) {
        IntList list = IntList.builder().disableNullValue().disableIteratorPool().initialCapacity(state.size).build();
        for (int i = 0; i < state.size; i++) {
            list.addInt(i);
        }
        return list;
    }

    @Benchmark
    public IntArrayList agronaInt_memoryFootprint(MemoryState state) {
        IntArrayList list = new IntArrayList(state.size, Integer.MIN_VALUE);
        for (int i = 0; i < state.size; i++) {
            list.add(i);
        }
        return list;
    }

    @Benchmark
    public ArrayList<Integer> boxedInt_memoryFootprint(MemoryState state) {
        ArrayList<Integer> list = new ArrayList<>(state.size);
        for (int i = 0; i < state.size; i++) {
            list.add(i);
        }
        return list;
    }

    // ==================== Mixed Workload Benchmark ====================

    @Benchmark
    public void intList_mixedWorkload(PopulatedState state, Blackhole bh) {
        IntList list = IntList.builder().disableNullValue().disableIteratorPool().initialCapacity(100).build();

        // Add
        for (int i = 0; i < 100; i++) {
            list.addInt(state.accessPattern[i % state.accessPattern.length]);
        }

        // Search
        for (int i = 0; i < 50; i++) {
            bh.consume(list.indexOfInt(i));
        }

        // Random access
        for (int i = 0; i < 100; i++) {
            bh.consume(list.getInt(i % list.size()));
        }

        // Modify
        for (int i = 0; i < 50; i++) {
            list.setInt(i, i * 2);
        }
    }

    @Benchmark
    public void agronaInt_mixedWorkload(PopulatedState state, Blackhole bh) {
        IntArrayList list = new IntArrayList(100, Integer.MIN_VALUE);

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
            bh.consume(list.getInt(i % list.size()));
        }

        // Modify
        for (int i = 0; i < 50; i++) {
            list.set(i, i * 2);
        }
    }
}