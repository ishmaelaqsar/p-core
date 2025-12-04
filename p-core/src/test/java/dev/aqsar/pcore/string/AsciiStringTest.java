package dev.aqsar.pcore.string;

import dev.aqsar.pcore.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class AsciiStringTest {
    private AsciiString s;
    private UnsafeBuffer testBuffer;
    private ByteBuffer directMem;

    @BeforeEach
    void setup() throws NoSuchFieldException, IllegalAccessException {
        s = new AsciiString(16);
        // Setup buffer
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe unsafe = (Unsafe) f.get(null);
        directMem = ByteBuffer.allocateDirect(128).order(ByteOrder.nativeOrder());
        long addr = unsafe.getLong(directMem, unsafe.objectFieldOffset(Buffer.class.getDeclaredField("address")));
        testBuffer = new UnsafeBuffer();
        testBuffer.wrap(addr, 128);
    }

    @Test
    void testConstructors() {
        AsciiString s0 = new AsciiString();
        assertNotNull(s0.getRawBytes());
        AsciiString s1 = new AsciiString(10);
        assertEquals(10, s1.getRawBytes().length);
    }

    @Test
    void testResizing() {
        s = new AsciiString(2);
        s.append("1");
        s.append("23"); // Exceeds capacity
        assertEquals("123", s.toString());
        assertTrue(s.getRawBytes().length > 2);
    }

    @Test
    void testAppendOptimizations() {
        // 1. AsciiString fast path
        AsciiString other = new AsciiString();
        other.append("Fast");
        s.append(other);
        assertEquals("Fast", s.toString());
        // 2. String path
        s.clear();
        s.append("String");
        assertEquals("String", s.toString());
        // 3. Utf8String path (generic loop with truncation)
        s.clear();
        Utf8String utf8 = new Utf8String();
        utf8.append("AB");
        utf8.append('€'); // 3 bytes, 1 char. Char value 0x20AC.
        s.append(utf8);
        // 'A', 'B' copied fine.
        // '€' (0x20AC) cast to byte becomes (byte)0xAC.
        // In ISO-8859-1 extended ASCII, 0xAC is '¬'.
        // But strictly treating as bytes, checking exact values:
        assertEquals(3, s.length());
        assertEquals('A', s.charAt(0));
        assertEquals('B', s.charAt(1));
        assertEquals((char) ((byte) 0x20AC & 0xFF), s.charAt(2));
    }

    @Test
    void testAppendSubSequence() {
        s.append("012345", 2, 4); // "23"
        assertEquals("23", s.toString());
    }

    @Test
    void testAppendInt() {
        s.append(Integer.MIN_VALUE);
        assertEquals("-2147483648", s.toString());
        s.clear();
        s.append(0);
        assertEquals("0", s.toString());
    }

    @Test
    void testAppendLong() {
        s.append(Long.MIN_VALUE);
        assertEquals("-9223372036854775808", s.toString());
    }

    @Test
    void testAppendFloat() {
        s.append(10.5f, 1);
        assertEquals("10.5", s.toString());
        s.clear();
        s.append(Float.NaN);
        assertEquals("NaN", s.toString());
        assertThrows(IllegalArgumentException.class, () -> s.append(1f, 100));
    }

    @Test
    void testAppendDouble() {
        s.append(3.14159, 2);
        assertEquals("3.14", s.toString());
        s.clear();
        s.append(Double.POSITIVE_INFINITY);
        assertEquals("Infinity", s.toString());
    }

    @Test
    void testAppendBoolean() {
        s.append(true).append(false);
        assertEquals("truefalse", s.toString());
    }

    @Test
    void testCharAt() {
        s.append("ABC");
        assertEquals('A', s.charAt(0));
        assertEquals('C', s.charAt(2));
        assertThrows(IndexOutOfBoundsException.class, () -> s.charAt(3));
        assertThrows(IndexOutOfBoundsException.class, () -> s.charAt(-1));
    }

    @Test
    void testSubSequence() {
        s.append("01234");
        assertEquals("12", s.subSequence(1, 3).toString());
        // Optimization check (should return new String)
        assertInstanceOf(String.class, s.subSequence(0, 5));
    }

    @Test
    void testGetBytes() {
        s.append("AB");
        byte[] bytes = s.getBytes(); // Safe copy
        assertEquals(2, bytes.length);
        assertEquals('A', bytes[0]);
        bytes[0] = 'Z';
        assertEquals("AB", s.toString(), "Modifying getBytes() result should not affect AsciiString");
    }

    @Test
    void testSetLength() {
        s.append("123");
        s.setLength(1);
        assertEquals("1", s.toString());
        s.setLength(3);
        // Extended with nulls
        assertEquals("1\0\0", s.toString());
    }

    @Test
    void testCopyOf() {
        s.append("old");
        s.copyOf("new");
        assertEquals("new", s.toString());
        AsciiString other = new AsciiString();
        other.append("other");
        s.copyOf(other);
        assertEquals("other", s.toString());
    }

    @Test
    void testCopyFrom() {
        testBuffer.putStringAscii(0, "Hello");
        s.append("OldData");
        s.copyFrom(testBuffer, 0, 5);
        assertEquals("Hello", s.toString());
        assertEquals(5, s.length());
    }

    @Test
    void testEqualsAndHashCode() {
        s.append("test");
        AsciiString s2 = new AsciiString();
        s2.append("test");
        assertEquals(s, s2);
        assertEquals(s.hashCode(), s2.hashCode());
        s2.setLength(0);
        assertNotEquals(s, s2);
    }
}
