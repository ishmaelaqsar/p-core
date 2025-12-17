package dev.aqsar.pcore.collections;

import dev.aqsar.pcore.number.FixedPointNumber;

import java.util.Arrays;
import java.util.Objects;

/**
 * A primitive-backed list for fixed-point values.
 * <p>
 * Unlike {@code ObjectList<FixedPointNumber>}, this stores raw {@code long}s
 * to avoid object overhead and maximize CPU cache locality.
 * </p>
 */
public final class FixedPointList {
    private long[] elements;
    private int size;
    private final int shift; // All numbers in this list share this precision

    public FixedPointList(int capacity, int shift) {
        this.elements = new long[Math.max(1, capacity)];
        this.shift = shift;
        this.size = 0;
    }

    public void add(FixedPointNumber num) {
        if (num.getShift() != this.shift) {
            // Optional: You could auto-rescale here instead of throwing
            throw new IllegalArgumentException("Shift mismatch: List expects " + shift);
        }
        addRaw(num.getRawValue());
    }

    public void addRaw(long raw) {
        if (size == elements.length) {
            grow();
        }
        elements[size++] = raw;
    }

    /**
     * "Zero-Allocation" Getter.
     * Updates the provided mutable container with the value at the index.
     */
    public void get(int index, FixedPointNumber resultContainer) {
        Objects.checkIndex(index, size);
        if (resultContainer.getShift() != this.shift) {
            throw new IllegalArgumentException("Result container has wrong shift");
        }
        resultContainer.setRawValue(elements[index]);
    }

    public long getRaw(int index) {
        Objects.checkIndex(index, size);
        return elements[index];
    }

    public int size() {
        return size;
    }

    private void grow() {
        int newCap = elements.length + (elements.length >> 1);
        elements = Arrays.copyOf(elements, newCap);
    }
}
