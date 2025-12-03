package dev.aqsar.pcore.benchmarks;

import dev.aqsar.pcore.string.AsciiString;
import dev.aqsar.pcore.string.Utf8String;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsAppend = {"-XX:+UseG1GC", "-Xms2g", "-Xmx2g"})
@State(Scope.Thread)
public class StringBenchmarks {
    @Param({"10", "100", "1000", "10000"})
    private int size;
    private String textPayload;
    private int intPayload;

    @Setup(Level.Trial)
    public void setup() {
        textPayload = "a";
        intPayload = 12345;
    }
    // ==================== Baseline Appends (Standard String) ====================

    @Benchmark
    public AsciiString asciiString_append_String() {
        AsciiString sb = new AsciiString(size);
        for (int i = 0; i < size; i++) {
            sb.append(textPayload);
        }
        return sb;
    }

    @Benchmark
    public Utf8String utf8String_append_String() {
        Utf8String sb = new Utf8String(size);
        for (int i = 0; i < size; i++) {
            sb.append(textPayload);
        }
        return sb;
    }

    @Benchmark
    public StringBuilder jdkBuilder_append_String() {
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append(textPayload);
        }
        return sb;
    }
    // ==================== Optimized Appends (MutableString) ====================

    @State(Scope.Thread)
    public static class MutablePayloadState {
        AsciiString asciiPayload;
        Utf8String utf8Payload;
        StringBuilder jdkPayload; // For fair comparison

        @Setup(Level.Trial)
        public void setup() {
            asciiPayload = new AsciiString();
            asciiPayload.append("a");
            utf8Payload = new Utf8String();
            utf8Payload.append("a");
            jdkPayload = new StringBuilder();
            jdkPayload.append("a");
        }
    }

    @Benchmark
    public AsciiString asciiString_append_AsciiString(MutablePayloadState state) {
        AsciiString sb = new AsciiString(size);
        for (int i = 0; i < size; i++) {
            // Should trigger the instanceof AsciiString optimization (memcpy)
            sb.append(state.asciiPayload);
        }
        return sb;
    }

    @Benchmark
    public Utf8String utf8String_append_Utf8String(MutablePayloadState state) {
        Utf8String sb = new Utf8String(size);
        for (int i = 0; i < size; i++) {
            // Should trigger the instanceof Utf8String optimization (memcpy)
            sb.append(state.utf8Payload);
        }
        return sb;
    }

    @Benchmark
    public Utf8String utf8String_append_AsciiString(MutablePayloadState state) {
        Utf8String sb = new Utf8String(size);
        for (int i = 0; i < size; i++) {
            // Should trigger the instanceof AsciiString optimization in Utf8String
            sb.append(state.asciiPayload);
        }
        return sb;
    }

    @Benchmark
    public StringBuilder jdkBuilder_append_StringBuilder(MutablePayloadState state) {
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            // JDK also optimizes appending AbstractStringBuilder to AbstractStringBuilder
            sb.append(state.jdkPayload);
        }
        return sb;
    }
    // ==================== Append (Int) Benchmarks ====================

    @Benchmark
    public AsciiString asciiString_appendInt() {
        AsciiString sb = new AsciiString(size * 5);
        for (int i = 0; i < size; i++) {
            sb.append(intPayload);
        }
        return sb;
    }

    @Benchmark
    public Utf8String utf8String_appendInt() {
        Utf8String sb = new Utf8String(size * 5);
        for (int i = 0; i < size; i++) {
            sb.append(intPayload);
        }
        return sb;
    }

    @Benchmark
    public StringBuilder jdkBuilder_appendInt() {
        StringBuilder sb = new StringBuilder(size * 5);
        for (int i = 0; i < size; i++) {
            sb.append(intPayload);
        }
        return sb;
    }
    // ==================== Unicode Benchmarks (No AsciiString) ====================

    @State(Scope.Thread)
    public static class UnicodeState {
        @Param({"100", "1000"})
        private int size;
        private Utf8String utf8Payload;
        private String stringPayload;
        private StringBuilder jdkBuilderPayload;
        private Utf8String utf8Target;
        private String jdkTarget;
        private int[] accessPattern;

        @Setup(Level.Trial)
        public void setup() {
            // "Hello Ω 世界 👍"
            String sample = "Hello \u03A9 \u4E16\u754C \uD83D\uDC4D ";
            // Payloads for appending
            stringPayload = sample;
            utf8Payload = new Utf8String();
            utf8Payload.append(sample);
            jdkBuilderPayload = new StringBuilder(sample);
            // Setup targets for read benchmarks
            StringBuilder sb = new StringBuilder(size);
            while (sb.length() < size) {
                sb.append(sample);
            }
            sb.setLength(size);
            String content = sb.toString();
            utf8Target = new Utf8String(size);
            utf8Target.append(content);
            jdkTarget = content;
            Random random = new Random(42);
            accessPattern = new int[1000];
            for (int i = 0; i < accessPattern.length; i++) {
                accessPattern[i] = random.nextInt(content.length());
            }
        }
    }

    @Benchmark
    public Utf8String utf8String_appendUnicode_String(UnicodeState state) {
        Utf8String sb = new Utf8String(state.size);
        int currentLen = 0;
        while (currentLen < state.size) {
            sb.append(state.stringPayload);
            currentLen += state.stringPayload.length();
        }
        return sb;
    }

    @Benchmark
    public Utf8String utf8String_appendUnicode_Utf8String(UnicodeState state) {
        Utf8String sb = new Utf8String(state.size);
        int currentLen = 0;
        while (currentLen < state.size) {
            // Optimized path
            sb.append(state.utf8Payload);
            currentLen += state.utf8Payload.length();
        }
        return sb;
    }

    @Benchmark
    public StringBuilder jdkBuilder_appendUnicode_String(UnicodeState state) {
        StringBuilder sb = new StringBuilder(state.size);
        int currentLen = 0;
        while (currentLen < state.size) {
            sb.append(state.stringPayload);
            currentLen += state.stringPayload.length();
        }
        return sb;
    }

    @Benchmark
    public void utf8String_charAt_unicodeRandom(UnicodeState state, Blackhole bh) {
        for (int index : state.accessPattern) {
            bh.consume(state.utf8Target.charAt(index));
        }
    }

    @Benchmark
    public void jdkString_charAt_unicodeRandom(UnicodeState state, Blackhole bh) {
        for (int index : state.accessPattern) {
            bh.consume(state.jdkTarget.charAt(index));
        }
    }
}
