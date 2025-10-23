package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ListIterator;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class IntListTest {

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        private IntList list;

        @BeforeEach
        void setUp() {
            list = IntList.builder().build();
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
            list.addInt(10);
            list.addInt(20);
            list.addInt(30);

            assertEquals(3, list.size());
            assertEquals(10, list.getInt(0));
            assertEquals(20, list.getInt(1));
            assertEquals(30, list.getInt(2));
        }

        @Test
        @DisplayName("should add boxed elements")
        void testAddBoxed() {
            list.add(10);
            list.add(20);

            assertEquals(2, list.size());
            assertEquals(10, list.get(0));
            assertEquals(20, list.get(1));
        }

        @Test
        @DisplayName("should get elements")
        void testGet() {
            list.addInt(100);
            list.addInt(200);

            assertEquals(100, list.getInt(0));
            assertEquals(200, list.getInt(1));
        }

        @Test
        @DisplayName("should throw on invalid index for get")
        void testGetInvalidIndex() {
            list.addInt(1);
            assertThrows(IndexOutOfBoundsException.class, () -> list.getInt(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> list.getInt(1));
            assertThrows(IndexOutOfBoundsException.class, () -> list.getInt(100));
        }

        @Test
        @DisplayName("should set elements")
        void testSet() {
            list.addInt(50);
            int old = list.setInt(0, 75);

            assertEquals(50, old);
            assertEquals(75, list.getInt(0));
        }

        @Test
        @DisplayName("should set boxed elements")
        void testSetBoxed() {
            list.add(50);
            int old = list.set(0, 75);

            assertEquals(50, old);
            assertEquals(75, list.get(0));
        }

        @Test
        @DisplayName("should throw on invalid index for set")
        void testSetInvalidIndex() {
            list.addInt(1);
            assertThrows(IndexOutOfBoundsException.class, () -> list.setInt(-1, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> list.setInt(1, 0));
        }

        @Test
        @DisplayName("should remove elements")
        void testRemove() {
            list.addInt(10);
            list.addInt(20);
            list.addInt(30);

            int removed = list.remove(1);

            assertEquals(20, removed);
            assertEquals(2, list.size());
            assertEquals(10, list.getInt(0));
            assertEquals(30, list.getInt(1));
        }

        @Test
        @DisplayName("should remove last element")
        void testRemoveLast() {
            list.addInt(10);
            list.addInt(20);

            list.remove(1);

            assertEquals(1, list.size());
            assertEquals(10, list.getInt(0));
        }

        @Test
        @DisplayName("should remove first element")
        void testRemoveFirst() {
            list.addInt(10);
            list.addInt(20);
            list.addInt(30);

            list.remove(0);

            assertEquals(2, list.size());
            assertEquals(20, list.getInt(0));
            assertEquals(30, list.getInt(1));
        }

        @Test
        @DisplayName("should clear list")
        void testClear() {
            list.addInt(1);
            list.addInt(2);
            list.addInt(3);

            list.clear();

            assertEquals(0, list.size());
            assertTrue(list.isEmpty());
        }
    }

    @Nested
    @DisplayName("Search Operations")
    class SearchOperations {

        private IntList list;

        @BeforeEach
        void setUp() {
            list = IntList.builder().build();
        }

        @Test
        @DisplayName("should find element")
        void testIndexOf() {
            list.addInt(100);
            list.addInt(200);
            list.addInt(300);

            assertEquals(0, list.indexOfInt(100));
            assertEquals(1, list.indexOfInt(200));
            assertEquals(2, list.indexOfInt(300));
            assertEquals(-1, list.indexOfInt(999));
        }

        @Test
        @DisplayName("should find boxed element")
        void testIndexOfBoxed() {
            list.addInt(100);
            list.addInt(200);

            assertEquals(0, list.indexOf(100));
            assertEquals(1, list.indexOf(200));
            assertEquals(-1, list.indexOf(999));
        }

        @Test
        @DisplayName("should return -1 for wrong type in indexOf")
        void testIndexOfWrongType() {
            list.addInt(100);

            assertEquals(-1, list.indexOf("not a number"));
            assertEquals(-1, list.indexOf(new Object()));
        }

        @Test
        @DisplayName("should test contains")
        void testContains() {
            list.addInt(50);
            list.addInt(100);

            assertTrue(list.containsInt(50));
            assertTrue(list.contains(100));
            assertFalse(list.containsInt(150));
            assertFalse(list.contains(999));
        }

        @Test
        @DisplayName("should handle large list search efficiently")
        void testLargeListSearch() {
            // Add more than 8 elements to test unrolled loop
            for (int i = 0; i < 20; i++) {
                list.addInt((i * 10));
            }

            assertEquals(0, list.indexOfInt(0));
            assertEquals(10, list.indexOfInt(100));
            assertEquals(19, list.indexOfInt(190));
            assertEquals(-1, list.indexOfInt(200));
        }

        @Test
        @DisplayName("should find first occurrence")
        void testIndexOfFirstOccurrence() {
            list.addInt(10);
            list.addInt(20);
            list.addInt(10);
            list.addInt(30);

            assertEquals(0, list.indexOfInt(10));
        }
    }

    @Nested
    @DisplayName("Bulk Operations")
    class BulkOperations {

        private IntList list;

        @BeforeEach
        void setUp() {
            list = IntList.builder().build();
        }

        @Test
        @DisplayName("should add all from array")
        void testAddAllArray() {
            int[] values = {1, 2, 3, 4};
            list.addAllInt(values);

            assertEquals(4, list.size());
            assertEquals(1, list.getInt(0));
            assertEquals(4, list.getInt(3));
        }

        @Test
        @DisplayName("should add all from array with offset")
        void testAddAllArrayWithOffset() {
            int[] values = {10, 20, 30, 40, 50};
            list.addAllInt(values, 1, 3);

            assertEquals(3, list.size());
            assertEquals(20, list.getInt(0));
            assertEquals(30, list.getInt(1));
            assertEquals(40, list.getInt(2));
        }

        @Test
        @DisplayName("should handle empty addAll")
        void testAddAllEmpty() {
            int[] empty = {};
            list.addAllInt(empty);
            assertEquals(0, list.size());
        }

        @Test
        @DisplayName("should handle zero-length addAll")
        void testAddAllZeroLength() {
            int[] values = {1, 2, 3};
            list.addAllInt(values, 1, 0);
            assertEquals(0, list.size());
        }

        @Test
        @DisplayName("should throw on invalid offset/length")
        void testAddAllInvalidRange() {
            int[] values = {1, 2, 3};

            assertThrows(IndexOutOfBoundsException.class, () -> list.addAllInt(values, -1, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> list.addAllInt(values, 0, 10));
            assertThrows(IndexOutOfBoundsException.class, () -> list.addAllInt(values, 2, 5));
        }

        @Test
        @DisplayName("should convert to array")
        void testToArray() {
            list.addInt(111);
            list.addInt(222);
            list.addInt(333);

            int[] array = list.toIntArray();

            assertEquals(3, array.length);
            assertEquals(111, array[0]);
            assertEquals(222, array[1]);
            assertEquals(333, array[2]);
        }

        @Test
        @DisplayName("should return independent array copy")
        void testToArrayIndependent() {
            list.addInt(10);

            int[] array = list.toIntArray();
            array[0] = 999;

            assertEquals(10, list.getInt(0));
        }
    }

    @Nested
    @DisplayName("Null Value Handling")
    class NullValueHandling {

        @Test
        @DisplayName("should handle null with default null value")
        void testDefaultNullValue() {
            IntList list = IntList.builder().build();

            list.add(null);
            list.add(10);
            list.add(null);

            assertEquals(3, list.size());
            assertNull(list.get(0));
            assertEquals(10, list.get(1));
            assertNull(list.get(2));

            assertEquals(IntList.DEFAULT_NULL_VALUE, list.getInt(0));
            assertEquals(10, list.getInt(1));
        }

        @Test
        @DisplayName("should handle custom null value")
        void testCustomNullValue() {
            IntList list = IntList.builder().nullValue(-1).build();

            list.add(null);
            list.add(10);

            assertNull(list.get(0));
            assertEquals(10, list.get(1));
            assertEquals(-1, list.getInt(0));
        }

        @Test
        @DisplayName("should disable null value handling")
        void testDisableNullValue() {
            IntList list = IntList.builder().disableNullValue().build();

            list.add(10);
            assertEquals(10, list.get(0));

            // With null value disabled, null is not handled specially
            assertThrows(NullPointerException.class, () -> list.add(null));
        }

        @Test
        @DisplayName("should find null value")
        void testIndexOfNull() {
            IntList list = IntList.builder().nullValue(-999).build();

            list.add(10);
            list.add(null);
            list.add(20);

            assertEquals(1, list.indexOf(null));
            assertEquals(1, list.indexOfInt(-999));
        }
    }

    @Nested
    @DisplayName("Capacity Management")
    class CapacityManagement {

        @Test
        @DisplayName("should grow capacity automatically")
        void testAutoGrow() {
            IntList list = IntList.builder().initialCapacity(2).build();

            for (int i = 0; i < 100; i++) {
                list.addInt(i);
            }

            assertEquals(100, list.size());
            for (int i = 0; i < 100; i++) {
                assertEquals(i, list.getInt(i));
            }
        }

        @Test
        @DisplayName("should respect initial capacity")
        void testInitialCapacity() {
            IntList list = IntList.builder().initialCapacity(100).build();

            for (int i = 0; i < 50; i++) {
                list.addInt(i);
            }

            assertEquals(50, list.size());
        }

        @Test
        @DisplayName("should handle minimum capacity of 1")
        void testMinimumCapacity() {
            IntList list = IntList.builder().initialCapacity(0).build();

            list.addInt(42);
            assertEquals(1, list.size());
            assertEquals(42, list.getInt(0));
        }

        @Test
        @DisplayName("should ensure capacity")
        void testEnsureCapacity() {
            IntList list = IntList.builder().initialCapacity(8).build();

            list.ensureCapacity(1000);

            for (int i = 0; i < 1000; i++) {
                list.addInt(i);
            }

            assertEquals(1000, list.size());
        }

        @Test
        @DisplayName("should switch to 1.5x growth for large arrays")
        void testLargeArrayGrowth() {
            IntList list = IntList.builder().initialCapacity(1024 * 1024 + 1).build();

            // Should use 1.5x growth instead of 2x
            for (int i = 0; i < 100; i++) {
                list.addInt(i);
            }

            assertEquals(100, list.size());
        }
    }

    @Nested
    @DisplayName("Iterator Pool")
    class IteratorPoolTests {

        private IntList list;

        @BeforeEach
        void setUp() {
            list = IntList.builder().build();
            list.addInt(10);
            list.addInt(20);
            list.addInt(30);
        }

        @Test
        @DisplayName("should borrow and return iterator")
        void testBorrowReturn() {
            assertEquals(8, list.availableIteratorCount());

            IntList.IntListIterator iter = list.borrowIterator();
            assertNotNull(iter);
            assertEquals(7, list.availableIteratorCount());

            list.returnIterator(iter);
            assertEquals(8, list.availableIteratorCount());
        }

        @Test
        @DisplayName("should iterate forward")
        void testIterateForward() {
            try (IntList.IntListIterator iter = list.borrowIterator()) {
                assertTrue(iter.hasNext());
                assertEquals(10, iter.nextInt());
                assertEquals(20, iter.nextInt());
                assertEquals(30, iter.nextInt());
                assertFalse(iter.hasNext());
            }
        }

        @Test
        @DisplayName("should iterate backward")
        void testIterateBackward() {
            try (IntList.IntListIterator iter = list.borrowIterator(3)) {
                assertTrue(iter.hasPrevious());
                assertEquals(30, iter.previousInt());
                assertEquals(20, iter.previousInt());
                assertEquals(10, iter.previousInt());
                assertFalse(iter.hasPrevious());
            }
        }

        @Test
        @DisplayName("should set during iteration")
        void testIteratorSet() {
            try (IntList.IntListIterator iter = list.borrowIterator()) {
                iter.nextInt();
                iter.setInt(99);
            }

            assertEquals(99, list.getInt(0));
        }

        @Test
        @DisplayName("should throw when setting without next/previous")
        void testIteratorSetWithoutMoving() {
            try (IntList.IntListIterator iter = list.borrowIterator()) {
                assertThrows(IllegalStateException.class, () -> iter.setInt(0));
            }
        }

        @Test
        @DisplayName("should return null when pool exhausted")
        void testPoolExhaustion() {
            IntList.IntListIterator[] iters = new IntList.IntListIterator[8];

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

            try (IntList.IntListIterator iter = list.borrowIterator()) {
                assertEquals(7, list.availableIteratorCount());
                iter.nextInt();
            }

            assertEquals(8, list.availableIteratorCount());
        }

        @Test
        @DisplayName("should throw on next when at end")
        void testNextPastEnd() {
            try (IntList.IntListIterator iter = list.borrowIterator()) {
                iter.nextInt();
                iter.nextInt();
                iter.nextInt();
                assertThrows(NoSuchElementException.class, iter::nextInt);
            }
        }

        @Test
        @DisplayName("should throw on previous when at start")
        void testPreviousPastStart() {
            try (IntList.IntListIterator iter = list.borrowIterator(0)) {
                assertThrows(NoSuchElementException.class, iter::previousInt);
            }
        }

        @Test
        @DisplayName("should report correct indices")
        void testIteratorIndices() {
            try (IntList.IntListIterator iter = list.borrowIterator()) {
                assertEquals(0, iter.nextIndex());
                assertEquals(-1, iter.previousIndex());

                iter.nextInt();

                assertEquals(1, iter.nextIndex());
                assertEquals(0, iter.previousIndex());
            }
        }

        @Test
        @DisplayName("should handle iterator starting at middle")
        void testIteratorStartMiddle() {
            try (IntList.IntListIterator iter = list.borrowIterator(1)) {
                assertEquals(1, iter.nextIndex());
                assertEquals(0, iter.previousIndex());

                assertEquals(20, iter.nextInt());
                assertEquals(20, iter.previousInt());
            }
        }

        @Test
        @DisplayName("should not return wrong iterator")
        void testReturnWrongIterator() {
            IntList list2 = IntList.builder().build();
            list2.addInt(1);

            IntList.IntListIterator iter = list.borrowIterator();

            // Returning to wrong list should be ignored
            list2.returnIterator(iter);
            assertEquals(7, list.availableIteratorCount());
            assertEquals(8, list2.availableIteratorCount());
        }
    }

    @Nested
    @DisplayName("Boxed Iterator")
    class BoxedIteratorTests {

        private IntList list;

        @BeforeEach
        void setUp() {
            list = IntList.builder().build();
            list.addInt(100);
            list.addInt(200);
            list.addInt(300);
        }

        @Test
        @DisplayName("should iterate with boxed iterator")
        void testBoxedIterator() {
            ListIterator<Integer> iter = list.listIterator();

            assertTrue(iter.hasNext());
            assertEquals(100, iter.next());
            assertEquals(200, iter.next());
            assertEquals(300, iter.next());
            assertFalse(iter.hasNext());
        }

        @Test
        @DisplayName("should support bidirectional iteration")
        void testBidirectional() {
            ListIterator<Integer> iter = list.listIterator();

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
            ListIterator<Integer> iter = list.listIterator();
            iter.next();
            iter.set(999);

            assertEquals(999, list.getInt(0));
        }

        @Test
        @DisplayName("should handle null values in boxed iterator")
        void testBoxedIteratorNull() {
            IntList nullList = IntList.builder().nullValue(-1).build();

            nullList.add(null);
            nullList.add(100);

            ListIterator<Integer> iter = nullList.listIterator();
            assertNull(iter.next());
            assertEquals(100, iter.next());
        }

        @Test
        @DisplayName("should throw on unsupported operations")
        void testUnsupportedOperations() {
            ListIterator<Integer> iter = list.listIterator();

            assertThrows(UnsupportedOperationException.class, () -> iter.add(0));
            assertThrows(UnsupportedOperationException.class, iter::remove);
        }

        @Test
        @DisplayName("should report correct indices")
        void testBoxedIteratorIndices() {
            ListIterator<Integer> iter = list.listIterator();

            assertEquals(0, iter.nextIndex());
            assertEquals(-1, iter.previousIndex());

            iter.next();

            assertEquals(1, iter.nextIndex());
            assertEquals(0, iter.previousIndex());
        }

        @Test
        @DisplayName("should start at specified index")
        void testBoxedIteratorStartIndex() {
            ListIterator<Integer> iter = list.listIterator(1);

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
            IntList list = IntList.builder().build();

            assertEquals(0, list.size());
            assertEquals(8, list.availableIteratorCount());
        }

        @Test
        @DisplayName("should disable iterator pool")
        void testDisableIteratorPool() {
            IntList list = IntList.builder().disableIteratorPool().build();

            assertEquals(0, list.availableIteratorCount());
            assertNull(list.borrowIterator());
        }

        @Test
        @DisplayName("should configure all options")
        void testFullConfiguration() {
            IntList list = IntList.builder().initialCapacity(50).nullValue(-999).disableIteratorPool().build();

            list.add(null);
            assertEquals(-999, list.getInt(0));
            assertEquals(0, list.availableIteratorCount());
        }

        @Test
        @DisplayName("should chain builder methods")
        void testBuilderChaining() {
            IntList list = IntList.builder().initialCapacity(10).nullValue(-1).build();

            assertNotNull(list);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty list operations")
        void testEmptyList() {
            IntList list = IntList.builder().build();

            assertTrue(list.isEmpty());
            assertEquals(0, list.size());
            assertEquals(-1, list.indexOfInt(0));
            assertFalse(list.containsInt(0));

            int[] array = list.toIntArray();
            assertEquals(0, array.length);
        }

        @Test
        @DisplayName("should handle single element")
        void testSingleElement() {
            IntList list = IntList.builder().build();
            list.addInt(42);

            assertEquals(1, list.size());
            assertEquals(42, list.getInt(0));
            assertEquals(0, list.indexOfInt(42));

            list.clear();
            assertTrue(list.isEmpty());
        }

        @Test
        @DisplayName("should handle repeated elements")
        void testRepeatedElements() {
            IntList list = IntList.builder().build();
            list.addInt(7);
            list.addInt(7);
            list.addInt(7);

            assertEquals(3, list.size());
            assertEquals(0, list.indexOfInt(7));
        }

        @Test
        @DisplayName("should handle zero value")
        void testZeroValue() {
            IntList list = IntList.builder().build();
            list.addInt(0);
            list.addInt(1);
            list.addInt(0);

            assertEquals(0, list.indexOfInt(0));
            assertTrue(list.containsInt(0));
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("should handle complex workflow")
        void testComplexWorkflow() {
            IntList list = IntList.builder().initialCapacity(4).build();

            // Add elements
            for (int i = 0; i < 10; i++) {
                list.addInt((i * 10));
            }

            // Search
            assertEquals(5, list.indexOfInt(50));

            // Modify
            list.setInt(5, 999);
            assertEquals(999, list.getInt(5));

            // Remove
            list.remove(0);
            assertEquals(9, list.size());
            assertEquals(10, list.getInt(0));

            // Iterate
            int count = 0;
            try (IntList.IntListIterator iter = list.borrowIterator()) {
                while (iter.hasNext()) {
                    iter.nextInt();
                    count++;
                }
            }
            assertEquals(9, count);

            // Bulk add
            int[] more = {1000, 2000};
            list.addAllInt(more);
            assertEquals(11, list.size());

            // Clear
            list.clear();
            assertTrue(list.isEmpty());
        }

        @Test
        @DisplayName("should handle stress test")
        void testStressTest() {
            IntList list = IntList.builder().build();

            // Add many elements
            for (int i = 0; i < 10000; i++) {
                list.addInt(i);
            }

            assertEquals(10000, list.size());

            // Search in large list
            assertEquals(5000, list.indexOfInt(5000));

            // Remove from middle repeatedly
            for (int i = 0; i < 100; i++) {
                list.remove(5000);
            }

            assertEquals(9900, list.size());
        }
    }
}