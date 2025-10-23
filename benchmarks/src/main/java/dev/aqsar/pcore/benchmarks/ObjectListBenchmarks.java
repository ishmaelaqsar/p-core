package dev.aqsar.pcore.benchmarks;

import dev.aqsar.pcore.collections.ObjectList;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3, jvmArgsAppend = {"-XX:+UseG1GC", "-Xms2g", "-Xmx2g"})
@State(Scope.Thread)
public class ObjectListBenchmarks {

    @Param({"10", "100", "1000", "10000"})
    private int size;

    private ObjectList<Counter> objectList;
    private ArrayList<Counter> arrayList;
    private Counter[] data;

    public static class Counter {
        int value;
    }

    @Setup(Level.Trial)
    public void setup() {
        Random random = new Random(42);
        data = new Counter[size];
        for (int i = 0; i < size; i++) {
            Counter c = new Counter();
            c.value = random.nextInt();
            data[i] = c;
        }
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        objectList = ObjectList.<Counter>builder().preAllocate(Counter::new).initialCapacity(size).build();
        arrayList = new ArrayList<>(size);
    }

    // ==================== Add Benchmarks ====================

    @Benchmark
    public ObjectList<Counter> objectList_addPreAllocated() {
        for (int i = 0; i < size; i++) {
            Counter c = objectList.addPreAllocated();
            c.value = i;
        }
        return objectList;
    }

    @Benchmark
    public ObjectList<Counter> objectList_add() {
        for (int i = 0; i < size; i++) {
            objectList.add(new Counter());
        }
        return objectList;
    }

    @Benchmark
    public ArrayList<Counter> arrayList_add() {
        for (int i = 0; i < size; i++) {
            arrayList.add(new Counter());
        }
        return arrayList;
    }

    // ==================== Random Access Benchmarks ====================

    @State(Scope.Thread)
    public static class PopulatedState {
        @Param({"10", "100", "1000", "10000"})
        private int size;

        private ObjectList<Counter> objectList;
        private ObjectList<Counter> objectListNoPool;
        private ArrayList<Counter> arrayList;
        private int[] accessPattern;

        @Setup(Level.Trial)
        public void setup() {
            Random random = new Random(42);

            // Create access pattern
            accessPattern = new int[1000];
            for (int i = 0; i < accessPattern.length; i++) {
                accessPattern[i] = random.nextInt(size);
            }

            // Populate ObjectList
            objectList = ObjectList.<Counter>builder()
                                   .preAllocate(Counter::new)
                                   .initialCapacity(size)
                                   .build();
            for (int i = 0; i < size; i++) {
                Counter c = objectList.addPreAllocated();
                c.value = i;
            }

            // Populate ObjectList (no iterator pool)
            objectListNoPool = ObjectList.<Counter>builder()
                                         .preAllocate(Counter::new)
                                         .disableIteratorPool()
                                         .initialCapacity(size)
                                         .build();
            for (int i = 0; i < size; i++) {
                Counter c = objectListNoPool.addPreAllocated();
                c.value = i;
            }

            // Populate ArrayList
            arrayList = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                Counter c = new Counter();
                c.value = i;
                arrayList.add(c);
            }
        }
    }

    @Benchmark
    public void objectList_randomGet(PopulatedState state, Blackhole bh) {
        for (int index : state.accessPattern) {
            bh.consume(state.objectList.get(index));
        }
    }

    @Benchmark
    public void arrayList_randomGet(PopulatedState state, Blackhole bh) {
        for (int index : state.accessPattern) {
            bh.consume(state.arrayList.get(index));
        }
    }

    @Benchmark
    public void objectList_sequentialGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.objectList.get(i));
        }
    }

    @Benchmark
    public void arrayList_sequentialGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.arrayList.get(i));
        }
    }

    // ==================== Search Benchmarks ====================

    @Benchmark
    public void objectList_indexOf_hit(PopulatedState state, Blackhole bh) {
        Counter target = state.objectList.get(state.size / 2);
        bh.consume(state.objectList.indexOf(target));
    }

    @Benchmark
    public void arrayList_indexOf_hit(PopulatedState state, Blackhole bh) {
        Counter target = state.arrayList.get(state.size / 2);
        bh.consume(state.arrayList.indexOf(target));
    }

    @Benchmark
    public void objectList_indexOf_miss(PopulatedState state, Blackhole bh) {
        bh.consume(state.objectList.indexOf(new Counter()));
    }

    @Benchmark
    public void arrayList_indexOf_miss(PopulatedState state, Blackhole bh) {
        bh.consume(state.arrayList.indexOf(new Counter()));
    }

    @Benchmark
    public void objectList_indexOfIdentity_hit(PopulatedState state, Blackhole bh) {
        Counter target = state.objectList.get(state.size / 2);
        bh.consume(state.objectList.indexOfIdentity(target));
    }

    @Benchmark
    public void objectList_indexOfIdentity_miss(PopulatedState state, Blackhole bh) {
        bh.consume(state.objectList.indexOfIdentity(new Counter()));
    }

    // ==================== Iterator Benchmarks ====================

    @Benchmark
    public void objectList_iterator(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.objectList.get(i));
        }
    }

    @Benchmark
    public void objectListNoPool_iterator(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.objectListNoPool.get(i));
        }
    }

    @Benchmark
    public void arrayList_iterator(PopulatedState state, Blackhole bh) {
        for (Counter c : state.arrayList) {
            bh.consume(c);
        }
    }

    // ==================== Memory Footprint Benchmarks ====================

    @State(Scope.Thread)
    public static class MemoryState {
        @Param({"1000", "10000", "100000"})
        private int size;

        @Setup(Level.Trial)
        public void setup() {
            System.gc();
            System.gc();
        }
    }

    @Benchmark
    public ObjectList<Counter> objectList_memoryFootprint(MemoryState state) {
        ObjectList<Counter> list = ObjectList.<Counter>builder()
                                             .preAllocate(Counter::new)
                                             .disableIteratorPool()
                                             .initialCapacity(state.size)
                                             .build();
        for (int i = 0; i < state.size; i++) {
            Counter c = list.addPreAllocated();
            c.value = i;
        }
        return list;
    }

    @Benchmark
    public ArrayList<Counter> arrayList_memoryFootprint(MemoryState state) {
        ArrayList<Counter> list = new ArrayList<>(state.size);
        for (int i = 0; i < state.size; i++) {
            Counter c = new Counter();
            c.value = i;
            list.add(c);
        }
        return list;
    }

    // ==================== Mixed Workload Benchmark ====================

    @Benchmark
    public void objectList_mixedWorkload(PopulatedState state, Blackhole bh) {
        ObjectList<Counter> list = ObjectList.<Counter>builder()
                                             .preAllocate(Counter::new)
                                             .disableIteratorPool()
                                             .initialCapacity(100)
                                             .build();

        // Add
        for (int i = 0; i < 100; i++) {
            Counter c = list.addPreAllocated();
            c.value = state.accessPattern[i % state.accessPattern.length];
        }

        // Search
        for (int i = 0; i < 50; i++) {
            bh.consume(list.indexOfIdentity(list.get(i)));
        }

        // Random access
        for (int i = 0; i < 100; i++) {
            bh.consume(list.get(i % list.size()));
        }

        // Modify
        for (int i = 0; i < 50; i++) {
            list.set(i, new Counter());
        }
    }

    @Benchmark
    public void arrayList_mixedWorkload(PopulatedState state, Blackhole bh) {
        ArrayList<Counter> list = new ArrayList<>(100);

        // Add
        for (int i = 0; i < 100; i++) {
            Counter c = new Counter();
            c.value = state.accessPattern[i % state.accessPattern.length];
            list.add(c);
        }

        // Search
        for (int i = 0; i < 50; i++) {
            bh.consume(list.indexOf(list.get(i)));
        }

        // Random access
        for (int i = 0; i < 100; i++) {
            bh.consume(list.get(i % list.size()));
        }

        // Modify
        for (int i = 0; i < 50; i++) {
            list.set(i, new Counter());
        }
    }
}
