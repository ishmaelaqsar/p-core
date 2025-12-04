package dev.aqsar.pcore.concurrent;

import dev.aqsar.pcore.string.AsciiString;
import dev.aqsar.pcore.string.Utf8String;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link UnsafeBuffer}.
 */
@SuppressWarnings("restriction")
class UnsafeBufferTest {

    private static Unsafe UNSAFE;
    private static long BUFFER_ADDRESS_FIELD_OFFSET;

    private static final int TEST_CAPACITY = 1024;
    private UnsafeBuffer unsafeBuffer;
    private ByteBuffer directBuffer; // To manage the lifecycle of the native memory
    private long baseAddress;

    @BeforeAll
    static void setupUnsafe() throws Exception {
        // Get Unsafe and the address field offset, similar to the class under test
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        UNSAFE = (Unsafe) f.get(null);

        Field addrField = Buffer.class.getDeclaredField("address");
        addrField.setAccessible(true);
        BUFFER_ADDRESS_FIELD_OFFSET = UNSAFE.objectFieldOffset(addrField);
    }

    @BeforeEach
    void setup() {
        // Allocate 1KB of off-heap memory for each test
        directBuffer = ByteBuffer.allocateDirect(TEST_CAPACITY).order(ByteOrder.nativeOrder());
        baseAddress = UNSAFE.getLong(directBuffer, BUFFER_ADDRESS_FIELD_OFFSET);

        // Create and wrap the buffer
        unsafeBuffer = new UnsafeBuffer();
        unsafeBuffer.wrap(baseAddress, TEST_CAPACITY);
    }

    @AfterEach
    void teardown() {
        // The garbage collector will reclaim the DirectByteBuffer
        // and its associated native memory.
        directBuffer = null;
        unsafeBuffer = null;
    }

    @Test
    void testWrapAndLength() {
        assertEquals(TEST_CAPACITY, unsafeBuffer.length());

        // Test re-wrapping
        unsafeBuffer.wrap(baseAddress + 100, 200);
        assertEquals(200, unsafeBuffer.length());
    }

    @Test
    void testReadWritePrimitives() {
        // Test Byte
        unsafeBuffer.putByte(0, (byte) 0xAA);
        assertEquals((byte) 0xAA, unsafeBuffer.getByte(0));

        // Test Short
        unsafeBuffer.putShort(2, (short) 0xAABB);
        assertEquals((short) 0xAABB, unsafeBuffer.getShort(2));

        // Test Char
        unsafeBuffer.putChar(4, 'Z');
        assertEquals('Z', unsafeBuffer.getChar(4));

        // Test Int
        unsafeBuffer.putInt(8, 0x12345678);
        assertEquals(0x12345678, unsafeBuffer.getInt(8));

        // Test Float
        unsafeBuffer.putFloat(12, 123.45f);
        assertEquals(123.45f, unsafeBuffer.getFloat(12));

        // Test Long
        unsafeBuffer.putLong(16, 0x1122334455667788L);
        assertEquals(0x1122334455667788L, unsafeBuffer.getLong(16));

        // Test Double
        unsafeBuffer.putDouble(24, 987.654);
        assertEquals(987.654, unsafeBuffer.getDouble(24));

        // Test that writes are correct by reading from the underlying ByteBuffer
        assertEquals((byte) 0xAA, directBuffer.get(0));
        assertEquals((short) 0xAABB, directBuffer.getShort(2));
        assertEquals(0x1122334455667788L, directBuffer.getLong(16));
    }

    @Test
    void testBulkReadWriteBytes() {
        byte[] src = new byte[100];
        for (int i = 0; i < src.length; i++) {
            src[i] = (byte) i;
        }

        // Write the array to the buffer
        unsafeBuffer.putBytes(10, src, 0, 100);

        // Read it back
        byte[] dst = new byte[100];
        unsafeBuffer.getBytes(10, dst, 0, 100);

        assertArrayEquals(src, dst);
    }

    @Test
    void testBulkReadWriteBytesWithOffset() {
        byte[] src = new byte[50];
        for (int i = 0; i < src.length; i++) {
            src[i] = (byte) (i + 1); // 1 to 50
        }

        // Write 20 bytes (from index 10 to 29) from src into the buffer
        unsafeBuffer.putBytes(500, src, 10, 20); // Should write bytes 11 through 30

        byte[] dst = new byte[30]; // Dst array filled with 0s
        // Read 20 bytes from buffer into the *middle* of dst (at offset 5)
        unsafeBuffer.getBytes(500, dst, 5, 20);

        // Verify dst
        for (int i = 0; i < 30; i++) {
            if (i < 5) {
                assertEquals(0, dst[i]); // 0-4
            } else if (i < 25) {
                assertEquals((byte) (i - 5 + 11), dst[i]); // 5-24 (bytes 11-30)
            } else {
                assertEquals(0, dst[i]); // 25-29
            }
        }
    }

    @Test
    void testStringAscii() {
        String s = "Hello ASCII 123!";
        int len = unsafeBuffer.putStringAscii(40, s);
        assertEquals(s.length(), len);

        String result = unsafeBuffer.getStringAscii(40, len);
        assertEquals(s, result);
    }

    @Test
    void testStringAsciiTruncation() {
        // 'Ü' (U+00DC) -> charAt() is 0x00DC. (byte) 0x00DC is 0xDC.
        // '€' (U+20AC) -> charAt() is 0x20AC. (byte) 0x20AC is 0xAC.
        String s = "Ü€";
        int len = unsafeBuffer.putStringAscii(50, s);
        assertEquals(2, len);

        // putByte writes 0xDC then 0xAC
        // getByte reads 0xDC, masks to 220, (char) 220 is 'Ü'
        // getByte reads 0xAC, masks to 172, (char) 172 is '¬'
        String result = unsafeBuffer.getStringAscii(50, len);
        assertEquals("Ü¬", result);
    }

    @Test
    void testStringCharset() {
        String s = "Hello UTF-8, this is €uro!";
        int len = unsafeBuffer.putString(100, s, StandardCharsets.UTF_8);

        // "€" is 3 bytes in UTF-8
        assertEquals(s.length() + 2, len);

        String result = unsafeBuffer.getString(100, len, StandardCharsets.UTF_8);
        assertEquals(s, result);

        // Test a different charset (UTF-16BE)
        int len16 = unsafeBuffer.putString(200, s, StandardCharsets.UTF_16BE);
        String result16 = unsafeBuffer.getString(200, len16, StandardCharsets.UTF_16BE);
        assertEquals(s, result16);
    }

    @Test
    void testMutableStringIntegration() {
        // 1. Test Writing Utf8String -> Buffer
        Utf8String utf8Src = new Utf8String();
        utf8Src.append("Hello €uro!"); // '€' is 3 bytes

        int written = unsafeBuffer.putString(0, utf8Src);

        // "Hello " (6) + "€" (3) + "uro!" (4) = 13 bytes
        assertEquals(13, written);
        assertEquals(13, utf8Src.getByteLength());

        // Verify content in buffer manually
        byte[] bufferContent = new byte[13];
        unsafeBuffer.getBytes(0, bufferContent, 0, 13);
        byte[] expectedBytes = Arrays.copyOf(utf8Src.getRawBytes(), utf8Src.getByteLength());
        assertArrayEquals(expectedBytes, bufferContent);

        // 2. Test Reading Buffer -> Utf8String (Overwrite/CopyFrom)
        Utf8String utf8Dst = new Utf8String();
        unsafeBuffer.getString(0, written, utf8Dst);

        assertEquals("Hello €uro!", utf8Dst.toString());
        assertEquals(13, utf8Dst.getByteLength());
        assertEquals(11, utf8Dst.length()); // 11 Chars

        // 3. Test Writing AsciiString -> Buffer
        AsciiString asciiSrc = new AsciiString();
        asciiSrc.append("ASCII Data");

        int writtenAscii = unsafeBuffer.putString(50, asciiSrc);
        assertEquals(10, writtenAscii);

        // 4. Test Reading Buffer -> AsciiString
        AsciiString asciiDst = new AsciiString();
        unsafeBuffer.getString(50, writtenAscii, asciiDst);

        assertEquals("ASCII Data", asciiDst.toString());
        assertEquals(10, asciiDst.length());
    }

    @Test
    void testMutableStringStateRecalculation() {
        // This explicitly tests that Utf8String recalculates its char count
        // when read from the buffer via copyFrom.

        // Manually write UTF-8 bytes for "A€" (A = 0x41, € = 0xE2 0x82 0xAC)
        unsafeBuffer.putByte(0, (byte) 0x41);
        unsafeBuffer.putByte(1, (byte) 0xE2);
        unsafeBuffer.putByte(2, (byte) 0x82);
        unsafeBuffer.putByte(3, (byte) 0xAC);

        Utf8String dst = new Utf8String();
        unsafeBuffer.getString(0, 4, dst);

        assertEquals("A€", dst.toString());
        assertEquals(4, dst.getByteLength());
        assertEquals(2, dst.length()); // Recalculated correctly as 2 chars
    }

    @Test
    void testBoundsChecking() {
        // This test will only pass if assertions are enabled (-ea)
        try {
            assert false;
            System.err.println(
                "Warning: UnsafeBufferTest.testBoundsChecking is " + "not running with assertions enabled (-ea). " +
                "Boundary check validation is being skipped.");
            return;
        } catch (AssertionError e) {
            // Assertions are enabled, proceed with the test
        }

        // Wrap a small buffer for precise checking
        unsafeBuffer.wrap(baseAddress, 10); // Valid indices 0-9

        // --- Test Negative Indices ---
        assertThrows(AssertionError.class, () -> unsafeBuffer.getByte(-1));
        assertThrows(AssertionError.class, () -> unsafeBuffer.putByte(-1, (byte) 0));

        // --- Test Getters (index == length) ---
        assertThrows(AssertionError.class, () -> unsafeBuffer.getByte(10));

        // --- Test Getters (index + size > length) ---
        assertThrows(AssertionError.class, () -> unsafeBuffer.getShort(9)); // 9+2 > 10
        assertThrows(AssertionError.class, () -> unsafeBuffer.getChar(9));  // 9+2 > 10
        assertThrows(AssertionError.class, () -> unsafeBuffer.getInt(7));   // 7+4 > 10
        assertThrows(AssertionError.class, () -> unsafeBuffer.getFloat(8)); // 8+4 > 10
        assertThrows(AssertionError.class, () -> unsafeBuffer.getLong(3));  // 3+8 > 10
        assertThrows(AssertionError.class, () -> unsafeBuffer.getDouble(4)); // 4+8 > 10

        // --- Test Bulk Getters ---
        assertThrows(AssertionError.class, () -> unsafeBuffer.getBytes(5, new byte[10], 0, 6)); // 5+6 > 10
        assertThrows(AssertionError.class, () -> unsafeBuffer.getStringAscii(1, 10)); // 1+10 > 10
        assertThrows(AssertionError.class, () -> unsafeBuffer.getString(0, 11, StandardCharsets.US_ASCII));

        // --- Test Putters (index == length) ---
        assertThrows(AssertionError.class, () -> unsafeBuffer.putByte(10, (byte) 0));

        // --- Test Putters (index + size > length) ---
        assertThrows(AssertionError.class, () -> unsafeBuffer.putShort(9, (short) 0));
        assertThrows(AssertionError.class, () -> unsafeBuffer.putChar(9, 'a'));
        assertThrows(AssertionError.class, () -> unsafeBuffer.putInt(7, 0));
        assertThrows(AssertionError.class, () -> unsafeBuffer.putFloat(8, 0f));
        assertThrows(AssertionError.class, () -> unsafeBuffer.putLong(3, 0L));
        assertThrows(AssertionError.class, () -> unsafeBuffer.putDouble(4, 0.0));

        // --- Test Bulk Putters ---
        assertThrows(AssertionError.class, () -> unsafeBuffer.putBytes(5, new byte[10], 0, 6));
        assertThrows(AssertionError.class, () -> unsafeBuffer.putStringAscii(1, "0123456789")); // 1+10 > 10
        assertThrows(AssertionError.class, () -> unsafeBuffer.putString(0, "12345678901", StandardCharsets.US_ASCII));
    }
}
