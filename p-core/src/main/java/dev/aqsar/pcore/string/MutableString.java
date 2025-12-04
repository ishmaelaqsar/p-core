package dev.aqsar.pcore.string;

import dev.aqsar.pcore.concurrent.ReadableBuffer;

import javax.annotation.Nullable;

public interface MutableString extends CharSequence {
    void clear();

    void setLength(final int newLength);

    MutableString append(final boolean b);

    MutableString append(final char c);

    MutableString append(final int i);

    MutableString append(final long l);

    MutableString append(final float f);

    MutableString append(final float f, final int precision);

    MutableString append(final double d);

    MutableString append(final double d, final int precision);

    MutableString append(final CharSequence s);

    MutableString append(final CharSequence s, final int start, final int end);

    MutableString copyOf(@Nullable final CharSequence cs);

    void copyFrom(final ReadableBuffer src, final int index, final int length);

    int getByteLength();
}
