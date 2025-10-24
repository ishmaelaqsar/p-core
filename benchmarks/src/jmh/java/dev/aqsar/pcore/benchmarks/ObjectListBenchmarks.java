package dev.aqsar.pcore.benchmarks;

import dev.aqsar.pcore.collections.ObjectList;
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
public class ObjectListBenchmarks {

    @Param({"10", "100", "1000", "10000"})
    private int size;

    private ObjectList<Counter> objectList;
    private ObjectList<Counter> objectListPreAllocated;
    private ArrayList<Counter> jdkList;
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
        objectList = ObjectList.<Counter>builder().initialCapacity(size).build();
        objectListPreAllocated = ObjectList.<Counter>builder().preAllocate(Counter::new).initialCapacity(size).build();
        jdkList = new ArrayList<>(size);
    }

    // ==================== Add Benchmarks ====================

    @Benchmark
    public ObjectList<Counter> objectList_add() {
        for (int i = 0; i < size; i++) {
            objectList.add(new Counter());
        }
        return objectList;
    }

    @Benchmark
    public ObjectList<Counter> objectListPreAllocated_add() {
        for (int i = 0; i < size; i++) {
            Counter c = objectListPreAllocated.addPreAllocated();
            c.value = i;
        }
        return objectList;
    }

    @Benchmark
    public ArrayList<Counter> jdkList_add() {
        for (int i = 0; i < size; i++) {
            jdkList.add(new Counter());
        }
        return jdkList;
    }

    // ==================== Random Access Benchmarks ====================

    @State(Scope.Thread)
    public static class PopulatedState {
        @Param({"10", "100", "1000", "10000"})
        private int size;

        private ObjectList<Counter> objectList;
        private ObjectList<Counter> objectListPreAllocated;
        private ArrayList<Counter> jdkList;
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
            objectList = ObjectList.<Counter>builder().initialCapacity(size).build();
            for (int i = 0; i < size; i++) {
                Counter c = new Counter();
                c.value = i;
                objectList.add(c);
            }

            // Populate ObjectList PreAllocated
            objectListPreAllocated =
                    ObjectList.<Counter>builder().preAllocate(Counter::new).initialCapacity(size).build();
            for (int i = 0; i < size; i++) {
                Counter c = objectListPreAllocated.addPreAllocated();
                c.value = i;
            }

            // Populate ArrayList
            jdkList = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                Counter c = new Counter();
                c.value = i;
                jdkList.add(c);
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
    public void objectListPreAllocated_randomGet(PopulatedState state, Blackhole bh) {
        for (int index : state.accessPattern) {
            bh.consume(state.objectListPreAllocated.get(index));
        }
    }

    @Benchmark
    public void jdkList_randomGet(PopulatedState state, Blackhole bh) {
        for (int index : state.accessPattern) {
            bh.consume(state.jdkList.get(index));
        }
    }

    @Benchmark
    public void objectList_sequentialGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.objectList.get(i));
        }
    }

    @Benchmark
    public void objectListPreAllocated_sequentialGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.objectListPreAllocated.get(i));
        }
    }

    @Benchmark
    public void jdkList_sequentialGet(PopulatedState state, Blackhole bh) {
        for (int i = 0; i < state.size; i++) {
            bh.consume(state.jdkList.get(i));
        }
    }

    // ==================== Search Benchmarks ====================

    @Benchmark
    public void objectList_indexOf_hit(PopulatedState state, Blackhole bh) {
        Counter target = state.objectList.get(state.size / 2);
        bh.consume(state.objectList.indexOf(target));
    }

    @Benchmark
    public void objectListPreAllocated_indexOf_hit(PopulatedState state, Blackhole bh) {
        Counter target = state.objectListPreAllocated.get(state.size / 2);
        bh.consume(state.objectListPreAllocated.indexOf(target));
    }

    @Benchmark
    public void jdkList_indexOf_hit(PopulatedState state, Blackhole bh) {
        Counter target = state.jdkList.get(state.size / 2);
        bh.consume(state.jdkList.indexOf(target));
    }

    @Benchmark
    public void objectList_indexOf_miss(PopulatedState state, Blackhole bh) {
        bh.consume(state.objectList.indexOf(new Counter()));
    }

    @Benchmark
    public void objectListPreAllocated_indexOf_miss(PopulatedState state, Blackhole bh) {
        bh.consume(state.objectListPreAllocated.indexOf(new Counter()));
    }

    @Benchmark
    public void jdkList_indexOf_miss(PopulatedState state, Blackhole bh) {
        bh.consume(state.jdkList.indexOf(new Counter()));
    }

    @Benchmark
    public void objectList_indexOfIdentity_hit(PopulatedState state, Blackhole bh) {
        Counter target = state.objectList.get(state.size / 2);
        bh.consume(state.objectList.indexOfIdentity(target));
    }

    @Benchmark
    public void objectListPreAllocated_indexOfIdentity_hit(PopulatedState state, Blackhole bh) {
        Counter target = state.objectListPreAllocated.get(state.size / 2);
        bh.consume(state.objectListPreAllocated.indexOfIdentity(target));
    }

    @Benchmark
    public void objectList_indexOfIdentity_miss(PopulatedState state, Blackhole bh) {
        bh.consume(state.objectList.indexOfIdentity(new Counter()));
    }

    @Benchmark
    public void objectListPreAllocated_indexOfIdentity_miss(PopulatedState state, Blackhole bh) {
        bh.consume(state.objectListPreAllocated.indexOfIdentity(new Counter()));
    }

    // ==================== Iterator Benchmarks ====================

    @Benchmark
    public void objectList_iterator(PopulatedState state, Blackhole bh) {
        try (var it = state.objectList.borrowIterator()) {
            while (it.hasNext()) {
                bh.consume(it.next());
            }
        }
    }

    @Benchmark
    public void objectListPreAllocated_iterator(PopulatedState state, Blackhole bh) {
        try (var it = state.objectListPreAllocated.borrowIterator()) {
            while (it.hasNext()) {
                bh.consume(it.next());
            }
        }
    }

    @Benchmark
    public void jdkList_iterator(PopulatedState state, Blackhole bh) {
        for (Counter c : state.jdkList) {
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
                                             .disableIteratorPool()
                                             .initialCapacity(state.size)
                                             .build();
        for (int i = 0; i < state.size; i++) {
            Counter c = new Counter();
            c.value = i;
            list.add(c);
        }
        return list;
    }

    @Benchmark
    public ObjectList<Counter> objectListPreAllocated_memoryFootprint(MemoryState state) {
        ObjectList<Counter> list = ObjectList.<Counter>builder()
                                             .disableIteratorPool()
                                             .preAllocate(Counter::new)
                                             .initialCapacity(state.size)
                                             .build();
        for (int i = 0; i < state.size; i++) {
            Counter c = list.addPreAllocated();
            c.value = i;
        }
        return list;
    }

    @Benchmark
    public ArrayList<Counter> jdkList_memoryFootprint(MemoryState state) {
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
                                             .disableIteratorPool()
                                             .initialCapacity(100)
                                             .build();

        // Add
        for (int i = 0; i < 100; i++) {
            Counter c = new Counter();
            c.value = state.accessPattern[i % state.accessPattern.length];
            list.add(c);
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
    public void objectListPreAllocated_mixedWorkload(PopulatedState state, Blackhole bh) {
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
    public void jdkList_mixedWorkload(PopulatedState state, Blackhole bh) {
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
