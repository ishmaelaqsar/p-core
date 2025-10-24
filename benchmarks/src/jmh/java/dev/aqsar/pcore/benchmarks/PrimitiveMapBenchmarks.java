package dev.aqsar.pcore.benchmarks;

import dev.aqsar.pcore.collections.Int2IntHashMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsAppend = {"-XX:+UseG1GC", "-Xms2g", "-Xmx2g"})
@State(Scope.Thread)
public class PrimitiveMapBenchmarks {

    @Param({"10", "100", "1000", "10000"})
    private int size;

    private int[] intKeys;
    private int[] intValues;

    private Int2IntHashMap myIntMap;
    private org.agrona.collections.Int2IntHashMap agronaIntMap;
    private Map<Integer, Integer> jdkIntMap;

    @Setup(Level.Trial)
    public void setupData() {
        Random random = new Random(42);

        intKeys = new int[size];
        intValues = new int[size];
        for (int i = 0; i < size; i++) {
            intKeys[i] = random.nextInt();
            intValues[i] = random.nextInt();
        }
    }

    @Setup(Level.Invocation)
    public void setupMaps() {
        myIntMap = Int2IntHashMap.builder().initialCapacity(size).build();
        agronaIntMap = new org.agrona.collections.Int2IntHashMap(Integer.MIN_VALUE);
        jdkIntMap = new HashMap<>(size);
    }

    @Benchmark
    public Int2IntHashMap myIntMap_put() {
        for (int i = 0; i < size; i++) {
            myIntMap.put(intKeys[i], intValues[i]);
        }
        return myIntMap;
    }

    @Benchmark
    public org.agrona.collections.Int2IntHashMap agronaIntMap_put() {
        for (int i = 0; i < size; i++) {
            agronaIntMap.put(intKeys[i], intValues[i]);
        }
        return agronaIntMap;
    }

    @Benchmark
    public Map<Integer, Integer> jdkIntMap_put() {
        for (int i = 0; i < size; i++) {
            jdkIntMap.put(intKeys[i], intValues[i]);
        }
        return jdkIntMap;
    }

    @Benchmark
    public void myIntMap_get(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            bh.consume(myIntMap.get(intKeys[i]));
        }
    }

    @Benchmark
    public void agronaIntMap_get(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            bh.consume(agronaIntMap.get(intKeys[i]));
        }
    }

    @Benchmark
    public void jdkIntMap_get(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            bh.consume(jdkIntMap.get(intKeys[i]));
        }
    }

    @Benchmark
    public void myIntMap_containsKey(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            bh.consume(myIntMap.containsKey(intKeys[i]));
        }
    }

    @Benchmark
    public void agronaIntMap_containsKey(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            bh.consume(agronaIntMap.containsKey(intKeys[i]));
        }
    }

    @Benchmark
    public void jdkIntMap_containsKey(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            bh.consume(jdkIntMap.containsKey(intKeys[i]));
        }
    }

    @Benchmark
    public void myIntMap_iteration(Blackhole bh) {
        try (var it = myIntMap.borrowIterator()) {
            while (it.hasNext()) {
                bh.consume(it.peekNextKey());
                bh.consume(it.nextValue());
            }
        }
    }

    @Benchmark
    public void agronaIntMap_iteration(Blackhole bh) {
        for (var e : agronaIntMap.entrySet()) {
            bh.consume(e.getKey());
            bh.consume(e.getValue());
        }
    }

    @Benchmark
    public void jdkIntMap_iteration(Blackhole bh) {
        for (Map.Entry<Integer, Integer> e : jdkIntMap.entrySet()) {
            bh.consume(e.getKey());
            bh.consume(e.getValue());
        }
    }

    // ==================== Remove Benchmarks ====================

    @Benchmark
    public void myIntMap_removeAll() {
        for (int i = 0; i < size; i++) {
            myIntMap.remove(intKeys[i]);
        }
    }

    @Benchmark
    public void agronaIntMap_removeAll() {
        for (int i = 0; i < size; i++) {
            agronaIntMap.remove(intKeys[i]);
        }
    }

    @Benchmark
    public void jdkIntMap_removeAll() {
        for (int i = 0; i < size; i++) {
            jdkIntMap.remove(intKeys[i]);
        }
    }
}