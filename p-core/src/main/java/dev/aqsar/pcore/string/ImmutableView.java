package dev.aqsar.pcore.string;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.Objects;

/**
 * A read-only view of a MutableString.
 * <p>
 * Note: This view is "live". If the underlying MutableString is modified,
 * this view will reflect those changes.
 * </p>
 */
@NotThreadSafe
public final class ImmutableView implements CharSequence, Comparable<ImmutableView> {

    private final AbstractMutableString mutableString;

    ImmutableView(final AbstractMutableString mutableString) {
        this.mutableString = mutableString;
    }

    @Override
    public int length() {
        return mutableString.length();
    }

    @Override
    public char charAt(final int index) {
        return mutableString.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return mutableString.subSequence(start, end);
    }

    @Override
    public String toString() {
        return mutableString.toString();
    }

    @Override
    public int compareTo(final ImmutableView o) {
        return mutableString.compareTo(o.mutableString);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ImmutableView that = (ImmutableView) o;
        return Objects.equals(mutableString, that.mutableString);
    }

    @Override
    public int hashCode() {
        return mutableString.hashCode();
    }
}
