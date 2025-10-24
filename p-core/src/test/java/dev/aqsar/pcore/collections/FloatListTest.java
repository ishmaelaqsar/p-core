package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ListIterator;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class FloatListTest {

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        private FloatList list;

        @BeforeEach
        void setUp() {
            list = FloatList.builder().build();
        }

        @Test
        @DisplayName("should create empty list")
        void testEmptyList() {
            assertEquals(0, list.size());
            assertTrue(list.isEmpty());
        }

        @Test
        @DisplayName("should add elements")
        void testAdd() {
            list.add(10);
            list.add(20);
            list.add(30);

            assertEquals(3, list.size());
            assertEquals(10, list.getFloat(0));
            assertEquals(20, list.getFloat(1));
            assertEquals(30, list.getFloat(2));
        }

        @Test
        @DisplayName("should add boxed elements")
        void testAddBoxed() {
            list.add(10f);
            list.add(20f);

            assertEquals(2, list.size());
            assertEquals(10, list.get(0));
            assertEquals(20, list.get(1));
        }

        @Test
        @DisplayName("should get elements")
        void testGet() {
            list.add(100);
            list.add(200);

            assertEquals(100, list.getFloat(0));
            assertEquals(200, list.getFloat(1));
        }

        @Test
        @DisplayName("should throw on invalid index for get")
        void testGetInvalidIndex() {
            list.add(1);
            assertThrows(IndexOutOfBoundsException.class, () -> list.getFloat(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> list.getFloat(1));
            assertThrows(IndexOutOfBoundsException.class, () -> list.getFloat(100));
        }

        @Test
        @DisplayName("should set elements")
        void testSet() {
            list.add(50);
            float old = list.set(0, 75f);

            assertEquals(50, old);
            assertEquals(75, list.getFloat(0));
        }

        @Test
        @DisplayName("should set boxed elements")
        void testSetBoxed() {
            list.add(50f);
            float old = list.set(0, 75f);

            assertEquals(50, old);
            assertEquals(75, list.get(0));
        }

        @Test
        @DisplayName("should throw on invalid index for set")
        void testSetInvalidIndex() {
            list.add(1);
            assertThrows(IndexOutOfBoundsException.class, () -> list.set(-1, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> list.set(1, 0));
        }

        @Test
        @DisplayName("should remove elements")
        void testRemove() {
            list.add(10);
            list.add(20);
            list.add(30);

            float removed = list.remove(1);

            assertEquals(20, removed);
            assertEquals(2, list.size());
            assertEquals(10, list.getFloat(0));
            assertEquals(30, list.getFloat(1));
        }

        @Test
        @DisplayName("should remove last element")
        void testRemoveLast() {
            list.add(10);
            list.add(20);

            list.remove(1);

            assertEquals(1, list.size());
            assertEquals(10, list.getFloat(0));
        }

        @Test
        @DisplayName("should remove first element")
        void testRemoveFirst() {
            list.add(10);
            list.add(20);
            list.add(30);

            list.remove(0);

            assertEquals(2, list.size());
            assertEquals(20, list.getFloat(0));
            assertEquals(30, list.getFloat(1));
        }

        @Test
        @DisplayName("should clear list")
        void testClear() {
            list.add(1);
            list.add(2);
            list.add(3);

            list.clear();

            assertEquals(0, list.size());
            assertTrue(list.isEmpty());
        }
    }

    @Nested
    @DisplayName("Search Operations")
    class SearchOperations {

        private FloatList list;

        @BeforeEach
        void setUp() {
            list = FloatList.builder().build();
        }

        @Test
        @DisplayName("should find element")
        void testIndexOf() {
            list.add(100);
            list.add(200);
            list.add(300);

            assertEquals(0, list.indexOf(100));
            assertEquals(1, list.indexOf(200));
            assertEquals(2, list.indexOf(300));
            assertEquals(-1, list.indexOf(999));
        }

        @Test
        @DisplayName("should find boxed element")
        void testIndexOfBoxed() {
            list.add(100);
            list.add(200);

            assertEquals(0, list.indexOf(100f));
            assertEquals(1, list.indexOf(200f));
            assertEquals(-1, list.indexOf(999f));
        }

        @Test
        @DisplayName("should return -1 for wrong type in indexOf")
        void testIndexOfWrongType() {
            list.add(100);

            assertEquals(-1, list.indexOf("not a number"));
            assertEquals(-1, list.indexOf(new Object()));
        }

        @Test
        @DisplayName("should test contains")
        void testContains() {
            list.add(50);
            list.add(100);

            assertTrue(list.contains(50));
            assertTrue(list.contains(100f));
            assertFalse(list.contains(150));
            assertFalse(list.contains(999f));
        }

        @Test
        @DisplayName("should handle large list search efficiently")
        void testLargeListSearch() {
            // Add more than 8 elements to test unrolled loop
            for (int i = 0; i < 20; i++) {
                list.add((i * 10));
            }

            assertEquals(0, list.indexOf(0));
            assertEquals(10, list.indexOf(100));
            assertEquals(19, list.indexOf(190));
            assertEquals(-1, list.indexOf(200));
        }

        @Test
        @DisplayName("should find first occurrence")
        void testIndexOfFirstOccurrence() {
            list.add(10);
            list.add(20);
            list.add(10);
            list.add(30);

            assertEquals(0, list.indexOf(10));
        }
    }

    @Nested
    @DisplayName("Bulk Operations")
    class BulkOperations {

        private FloatList list;

        @BeforeEach
        void setUp() {
            list = FloatList.builder().build();
        }

        @Test
        @DisplayName("should add all from array")
        void testAddAllArray() {
            float[] values = {1, 2, 3, 4};
            list.addAll(values);

            assertEquals(4, list.size());
            assertEquals(1, list.getFloat(0));
            assertEquals(4, list.getFloat(3));
        }

        @Test
        @DisplayName("should add all from array with offset")
        void testAddAllArrayWithOffset() {
            float[] values = {10, 20, 30, 40, 50};
            list.addAll(values, 1, 3);

            assertEquals(3, list.size());
            assertEquals(20, list.getFloat(0));
            assertEquals(30, list.getFloat(1));
            assertEquals(40, list.getFloat(2));
        }

        @Test
        @DisplayName("should handle empty addAll")
        void testAddAllEmpty() {
            float[] empty = {};
            list.addAll(empty);
            assertEquals(0, list.size());
        }

        @Test
        @DisplayName("should handle zero-length addAll")
        void testAddAllZeroLength() {
            float[] values = {1, 2, 3};
            list.addAll(values, 1, 0);
            assertEquals(0, list.size());
        }

        @Test
        @DisplayName("should throw on invalid offset/length")
        void testAddAllInvalidRange() {
            float[] values = {1, 2, 3};

            assertThrows(IndexOutOfBoundsException.class, () -> list.addAll(values, -1, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> list.addAll(values, 0, 10));
            assertThrows(IndexOutOfBoundsException.class, () -> list.addAll(values, 2, 5));
        }

        @Test
        @DisplayName("should convert to array")
        void testToArray() {
            list.add(111);
            list.add(222);
            list.add(333);

            float[] array = list.toFloatArray();

            assertEquals(3, array.length);
            assertEquals(111, array[0]);
            assertEquals(222, array[1]);
            assertEquals(333, array[2]);
        }

        @Test
        @DisplayName("should return independent array copy")
        void testToArrayIndependent() {
            list.add(10);

            float[] array = list.toFloatArray();
            array[0] = 999;

            assertEquals(10, list.getFloat(0));
        }
    }

    @Nested
    @DisplayName("Null Value Handling")
    class NullValueHandling {

        @Test
        @DisplayName("should handle null with default null value")
        void testDefaultNullValue() {
            FloatList list = FloatList.builder().build();

            list.add(null);
            list.add(10f);
            list.add(null);

            assertEquals(3, list.size());
            assertTrue(Float.isNaN(list.get(0)));
            assertEquals(10, list.get(1));
            assertTrue(Float.isNaN(list.get(2)));

            assertEquals(FloatList.DEFAULT_NULL_VALUE, list.getFloat(0));
            assertEquals(10, list.getFloat(1));
        }

        @Test
        @DisplayName("should handle custom null value")
        void testCustomNullValue() {
            FloatList list = FloatList.builder().nullValue(-1).build();

            list.add(null);
            list.add(10f);

            assertNull(list.get(0));
            assertEquals(10, list.get(1));
            assertEquals(-1, list.getFloat(0));
        }

        @Test
        @DisplayName("should disable null value handling")
        void testDisableNullValue() {
            FloatList list = FloatList.builder().disableNullValue().build();

            list.add(10f);
            assertEquals(10, list.get(0));

            // With null value disabled, null is not handled specially
            assertThrows(NullPointerException.class, () -> list.add(null));
        }

        @Test
        @DisplayName("should find null value")
        void testIndexOfNull() {
            FloatList list = FloatList.builder().nullValue(-999).build();

            list.add(10f);
            list.add(null);
            list.add(20f);

            assertEquals(1, list.indexOf(null));
            assertEquals(1, list.indexOf(-999));
        }
    }

    @Nested
    @DisplayName("Capacity Management")
    class CapacityManagement {

        @Test
        @DisplayName("should grow capacity automatically")
        void testAutoGrow() {
            FloatList list = FloatList.builder().initialCapacity(2).build();

            for (int i = 0; i < 100; i++) {
                list.add(i);
            }

            assertEquals(100, list.size());
            for (int i = 0; i < 100; i++) {
                assertEquals(i, list.getFloat(i));
            }
        }

        @Test
        @DisplayName("should respect initial capacity")
        void testInitialCapacity() {
            FloatList list = FloatList.builder().initialCapacity(100).build();

            for (int i = 0; i < 50; i++) {
                list.add(i);
            }

            assertEquals(50, list.size());
        }

        @Test
        @DisplayName("should handle minimum capacity of 1")
        void testMinimumCapacity() {
            FloatList list = FloatList.builder().initialCapacity(0).build();

            list.add(42);
            assertEquals(1, list.size());
            assertEquals(42, list.getFloat(0));
        }

        @Test
        @DisplayName("should ensure capacity")
        void testEnsureCapacity() {
            FloatList list = FloatList.builder().initialCapacity(8).build();

            list.ensureCapacity(1000);

            for (int i = 0; i < 1000; i++) {
                list.add(i);
            }

            assertEquals(1000, list.size());
        }

        @Test
        @DisplayName("should switch to 1.5x growth for large arrays")
        void testLargeArrayGrowth() {
            FloatList list = FloatList.builder().initialCapacity(1024 * 1024 + 1).build();

            // Should use 1.5x growth instead of 2x
            for (int i = 0; i < 100; i++) {
                list.add(i);
            }

            assertEquals(100, list.size());
        }
    }

    @Nested
    @DisplayName("Iterator Pool")
    class IteratorPoolTests {

        private FloatList list;

        @BeforeEach
        void setUp() {
            list = FloatList.builder().build();
            list.add(10);
            list.add(20);
            list.add(30);
        }

        @Test
        @DisplayName("should borrow and return iterator")
        void testBorrowReturn() {
            assertEquals(8, list.availableIteratorCount());

            FloatList.FloatListIterator iter = list.borrowIterator();
            assertNotNull(iter);
            assertEquals(7, list.availableIteratorCount());

            list.returnIterator(iter);
            assertEquals(8, list.availableIteratorCount());
        }

        @Test
        @DisplayName("should iterate forward")
        void testIterateForward() {
            try (FloatList.FloatListIterator iter = list.borrowIterator()) {
                assertTrue(iter.hasNext());
                assertEquals(10, iter.next());
                assertEquals(20, iter.next());
                assertEquals(30, iter.next());
                assertFalse(iter.hasNext());
            }
        }

        @Test
        @DisplayName("should iterate backward")
        void testIterateBackward() {
            try (FloatList.FloatListIterator iter = list.borrowIterator(3)) {
                assertTrue(iter.hasPrevious());
                assertEquals(30, iter.previous());
                assertEquals(20, iter.previous());
                assertEquals(10, iter.previous());
                assertFalse(iter.hasPrevious());
            }
        }

        @Test
        @DisplayName("should set during iteration")
        void testIteratorSet() {
            try (FloatList.FloatListIterator iter = list.borrowIterator()) {
                iter.next();
                iter.set(99);
            }

            assertEquals(99, list.getFloat(0));
        }

        @Test
        @DisplayName("should throw when setting without next/previous")
        void testIteratorSetWithoutMoving() {
            try (FloatList.FloatListIterator iter = list.borrowIterator()) {
                assertThrows(IllegalStateException.class, () -> iter.set(0));
            }
        }

        @Test
        @DisplayName("should return null when pool exhausted")
        void testPoolExhaustion() {
            FloatList.FloatListIterator[] iters = new FloatList.FloatListIterator[8];

            for (int i = 0; i < 8; i++) {
                iters[i] = list.borrowIterator();
                assertNotNull(iters[i]);
            }

            assertNull(list.borrowIterator());

            list.returnIterator(iters[0]);
            assertNotNull(list.borrowIterator());
        }

        @Test
        @DisplayName("should auto-return with try-with-resources")
        void testAutoClose() {
            assertEquals(8, list.availableIteratorCount());

            try (FloatList.FloatListIterator iter = list.borrowIterator()) {
                assertEquals(7, list.availableIteratorCount());
                iter.next();
            }

            assertEquals(8, list.availableIteratorCount());
        }

        @Test
        @DisplayName("should throw on next when at end")
        void testNextPastEnd() {
            try (FloatList.FloatListIterator iter = list.borrowIterator()) {
                iter.next();
                iter.next();
                iter.next();
                assertThrows(NoSuchElementException.class, iter::next);
            }
        }

        @Test
        @DisplayName("should throw on previous when at start")
        void testPreviousPastStart() {
            try (FloatList.FloatListIterator iter = list.borrowIterator(0)) {
                assertThrows(NoSuchElementException.class, iter::previous);
            }
        }

        @Test
        @DisplayName("should report correct indices")
        void testIteratorIndices() {
            try (FloatList.FloatListIterator iter = list.borrowIterator()) {
                assertEquals(0, iter.nextIndex());
                assertEquals(-1, iter.previousIndex());

                iter.next();

                assertEquals(1, iter.nextIndex());
                assertEquals(0, iter.previousIndex());
            }
        }

        @Test
        @DisplayName("should handle iterator starting at middle")
        void testIteratorStartMiddle() {
            try (FloatList.FloatListIterator iter = list.borrowIterator(1)) {
                assertEquals(1, iter.nextIndex());
                assertEquals(0, iter.previousIndex());

                assertEquals(20, iter.next());
                assertEquals(20, iter.previous());
            }
        }

        @Test
        @DisplayName("should not return wrong iterator")
        void testReturnWrongIterator() {
            FloatList list2 = FloatList.builder().build();
            list2.add(1);

            FloatList.FloatListIterator iter = list.borrowIterator();

            // Returning to wrong list should be ignored
            list2.returnIterator(iter);
            assertEquals(7, list.availableIteratorCount());
            assertEquals(8, list2.availableIteratorCount());
        }
    }

    @Nested
    @DisplayName("Boxed Iterator")
    class BoxedIteratorTests {

        private FloatList list;

        @BeforeEach
        void setUp() {
            list = FloatList.builder().build();
            list.add(100);
            list.add(200);
            list.add(300);
        }

        @Test
        @DisplayName("should iterate with boxed iterator")
        void testBoxedIterator() {
            ListIterator<Float> iter = list.listIterator();

            assertTrue(iter.hasNext());
            assertEquals(100, iter.next());
            assertEquals(200, iter.next());
            assertEquals(300, iter.next());
            assertFalse(iter.hasNext());
        }

        @Test
        @DisplayName("should support bidirectional iteration")
        void testBidirectional() {
            ListIterator<Float> iter = list.listIterator();

            iter.next();
            iter.next();

            assertTrue(iter.hasPrevious());
            assertEquals(200, iter.previous());
            assertEquals(100, iter.previous());
            assertFalse(iter.hasPrevious());
        }

        @Test
        @DisplayName("should set via boxed iterator")
        void testBoxedIteratorSet() {
            ListIterator<Float> iter = list.listIterator();
            iter.next();
            iter.set(999f);

            assertEquals(999, list.getFloat(0));
        }

        @Test
        @DisplayName("should handle null values in boxed iterator")
        void testBoxedIteratorNull() {
            FloatList nullList = FloatList.builder().nullValue(-1).build();

            nullList.add(null);
            nullList.add(100f);

            ListIterator<Float> iter = nullList.listIterator();
            assertNull(iter.next());
            assertEquals(100, iter.next());
        }

        @Test
        @DisplayName("should throw on unsupported operations")
        void testUnsupportedOperations() {
            ListIterator<Float> iter = list.listIterator();

            assertThrows(UnsupportedOperationException.class, () -> iter.add(0f));
            assertThrows(UnsupportedOperationException.class, iter::remove);
        }

        @Test
        @DisplayName("should report correct indices")
        void testBoxedIteratorIndices() {
            ListIterator<Float> iter = list.listIterator();

            assertEquals(0, iter.nextIndex());
            assertEquals(-1, iter.previousIndex());

            iter.next();

            assertEquals(1, iter.nextIndex());
            assertEquals(0, iter.previousIndex());
        }

        @Test
        @DisplayName("should start at specified index")
        void testBoxedIteratorStartIndex() {
            ListIterator<Float> iter = list.listIterator(1);

            assertEquals(1, iter.nextIndex());
            assertEquals(200, iter.next());
        }
    }

    @Nested
    @DisplayName("Builder Configuration")
    class BuilderConfiguration {

        @Test
        @DisplayName("should use default values")
        void testDefaults() {
            FloatList list = FloatList.builder().build();

            assertEquals(0, list.size());
            assertEquals(8, list.availableIteratorCount());
        }

        @Test
        @DisplayName("should disable iterator pool")
        void testDisableIteratorPool() {
            FloatList list = FloatList.builder().disableIteratorPool().build();

            assertEquals(0, list.availableIteratorCount());
            assertNull(list.borrowIterator());
        }

        @Test
        @DisplayName("should configure all options")
        void testFullConfiguration() {
            FloatList list = FloatList.builder().initialCapacity(50).nullValue(-999).disableIteratorPool().build();

            list.add(null);
            assertEquals(-999, list.getFloat(0));
            assertEquals(0, list.availableIteratorCount());
        }

        @Test
        @DisplayName("should chain builder methods")
        void testBuilderChaining() {
            FloatList list = FloatList.builder().initialCapacity(10).nullValue(-1).build();

            assertNotNull(list);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty list operations")
        void testEmptyList() {
            FloatList list = FloatList.builder().build();

            assertTrue(list.isEmpty());
            assertEquals(0, list.size());
            assertEquals(-1, list.indexOf(0));
            assertFalse(list.contains(0));

            float[] array = list.toFloatArray();
            assertEquals(0, array.length);
        }

        @Test
        @DisplayName("should handle single element")
        void testSingleElement() {
            FloatList list = FloatList.builder().build();
            list.add(42);

            assertEquals(1, list.size());
            assertEquals(42, list.getFloat(0));
            assertEquals(0, list.indexOf(42));

            list.clear();
            assertTrue(list.isEmpty());
        }

        @Test
        @DisplayName("should handle repeated elements")
        void testRepeatedElements() {
            FloatList list = FloatList.builder().build();
            list.add(7);
            list.add(7);
            list.add(7);

            assertEquals(3, list.size());
            assertEquals(0, list.indexOf(7));
        }

        @Test
        @DisplayName("should handle zero value")
        void testZeroValue() {
            FloatList list = FloatList.builder().build();
            list.add(0);
            list.add(1);
            list.add(0);

            assertEquals(0, list.indexOf(0));
            assertTrue(list.contains(0));
        }
    }

    @Nested
    @DisplayName("Floategration Tests")
    class FloategrationTests {

        @Test
        @DisplayName("should handle complex workflow")
        void testComplexWorkflow() {
            FloatList list = FloatList.builder().initialCapacity(4).build();

            // Add elements
            for (int i = 0; i < 10; i++) {
                list.add((i * 10));
            }

            // Search
            assertEquals(5, list.indexOf(50));

            // Modify
            list.set(5, 999);
            assertEquals(999, list.getFloat(5));

            // Remove
            list.remove(0);
            assertEquals(9, list.size());
            assertEquals(10, list.getFloat(0));

            // Iterate
            int count = 0;
            try (FloatList.FloatListIterator iter = list.borrowIterator()) {
                while (iter.hasNext()) {
                    iter.next();
                    count++;
                }
            }
            assertEquals(9, count);

            // Bulk add
            float[] more = {1000, 2000};
            list.addAll(more);
            assertEquals(11, list.size());

            // Clear
            list.clear();
            assertTrue(list.isEmpty());
        }

        @Test
        @DisplayName("should handle stress test")
        void testStressTest() {
            FloatList list = FloatList.builder().build();

            // Add many elements
            for (int i = 0; i < 10000; i++) {
                list.add(i);
            }

            assertEquals(10000, list.size());

            // Search in large list
            assertEquals(5000, list.indexOf(5000));

            // Remove from middle repeatedly
            for (int i = 0; i < 100; i++) {
                list.remove(5000);
            }

            assertEquals(9900, list.size());
        }
    }
}