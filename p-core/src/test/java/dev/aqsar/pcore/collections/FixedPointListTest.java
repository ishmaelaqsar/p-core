package dev.aqsar.pcore.collections;

import dev.aqsar.pcore.number.FixedPointNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedPointListTest {

    private static final int TEST_SHIFT = 16;
    private FixedPointList list;

    @BeforeEach
    void setUp() {
        list = new FixedPointList(4, TEST_SHIFT);
    }

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        @Test
        void addsAndGetsRaw() {
            list.addRaw(100);
            list.addRaw(200);
            assertEquals(2, list.size());
            assertEquals(100, list.getRaw(0));
            assertEquals(200, list.getRaw(1));
        }

        @Test
        void addsFixedPointNumber() {
            FixedPointNumber num = FixedPointNumber.wrap(500, TEST_SHIFT);
            list.add(num);
            assertEquals(1, list.size());
            assertEquals(500, list.getRaw(0));
        }

        @Test
        void getsIntoContainer() {
            list.addRaw(12345);
            FixedPointNumber container = FixedPointNumber.valueOf(0); // reuse this
            list.get(0, container);
            assertEquals(12345, container.getRawValue());
            assertEquals(TEST_SHIFT, container.getShift());
        }

        @Test
        void growsDynamically() {
            // Initial cap is 4
            for (int i = 0; i < 10; i++) {
                list.addRaw(i);
            }
            assertEquals(10, list.size());
            assertEquals(9, list.getRaw(9));
        }
    }

    @Nested
    @DisplayName("Validation & Safety")
    class ValidationTests {

        @Test
        void checksShiftOnAdd() {
            FixedPointNumber wrongShift = FixedPointNumber.valueOf(1.0, 8); // Shift 8
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> list.add(wrongShift));
            assertTrue(e.getMessage().contains("Shift mismatch"));
        }

        @Test
        void checksShiftOnGet() {
            list.addRaw(100);
            FixedPointNumber wrongShiftContainer = FixedPointNumber.valueOf(0, 8);
            assertThrows(IllegalArgumentException.class, () -> list.get(0, wrongShiftContainer));
        }

        @Test
        void throwsOutOfBounds() {
            assertThrows(IndexOutOfBoundsException.class, () -> list.getRaw(0));
            list.addRaw(1);
            assertThrows(IndexOutOfBoundsException.class, () -> list.getRaw(1));
        }
    }
}
