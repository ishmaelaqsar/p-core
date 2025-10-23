package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ListIterator;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class LongListTest {

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        private LongList list;

        @BeforeEach
        void setUp() {
            list = LongList.builder().build();
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
            list.addLong(10);
            list.addLong(20);
            list.addLong(30);

            assertEquals(3, list.size());
            assertEquals(10, list.getLong(0));
            assertEquals(20, list.getLong(1));
            assertEquals(30, list.getLong(2));
        }

        @Test
        @DisplayName("should add boxed elements")
        void testAddBoxed() {
            list.add(10L);
            list.add(20L);

            assertEquals(2, list.size());
            assertEquals(10, list.get(0));
            assertEquals(20, list.get(1));
        }

        @Test
        @DisplayName("should get elements")
        void testGet() {
            list.addLong(100);
            list.addLong(200);

            assertEquals(100, list.getLong(0));
            assertEquals(200, list.getLong(1));
        }

        @Test
        @DisplayName("should throw on invalid index for get")
        void testGetInvalidIndex() {
            list.addLong(1);
            assertThrows(IndexOutOfBoundsException.class, () -> list.getLong(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> list.getLong(1));
            assertThrows(IndexOutOfBoundsException.class, () -> list.getLong(100));
        }

        @Test
        @DisplayName("should set elements")
        void testSet() {
            list.addLong(50);
            long old = list.setLong(0, 75);

            assertEquals(50, old);
            assertEquals(75, list.getLong(0));
        }

        @Test
        @DisplayName("should set boxed elements")
        void testSetBoxed() {
            list.add(50L);
            long old = list.set(0, 75L);

            assertEquals(50, old);
            assertEquals(75, list.get(0));
        }

        @Test
        @DisplayName("should throw on invalid index for set")
        void testSetInvalidIndex() {
            list.addLong(1);
            assertThrows(IndexOutOfBoundsException.class, () -> list.setLong(-1, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> list.setLong(1, 0));
        }

        @Test
        @DisplayName("should remove elements")
        void testRemove() {
            list.addLong(10);
            list.addLong(20);
            list.addLong(30);

            long removed = list.remove(1);

            assertEquals(20, removed);
            assertEquals(2, list.size());
            assertEquals(10, list.getLong(0));
            assertEquals(30, list.getLong(1));
        }

        @Test
        @DisplayName("should remove last element")
        void testRemoveLast() {
            list.addLong(10);
            list.addLong(20);

            list.remove(1);

            assertEquals(1, list.size());
            assertEquals(10, list.getLong(0));
        }

        @Test
        @DisplayName("should remove first element")
        void testRemoveFirst() {
            list.addLong(10);
            list.addLong(20);
            list.addLong(30);

            list.remove(0);

            assertEquals(2, list.size());
            assertEquals(20, list.getLong(0));
            assertEquals(30, list.getLong(1));
        }

        @Test
        @DisplayName("should clear list")
        void testClear() {
            list.addLong(1);
            list.addLong(2);
            list.addLong(3);

            list.clear();

            assertEquals(0, list.size());
            assertTrue(list.isEmpty());
        }
    }

    @Nested
    @DisplayName("Search Operations")
    class SearchOperations {

        private LongList list;

        @BeforeEach
        void setUp() {
            list = LongList.builder().build();
        }

        @Test
        @DisplayName("should find element")
        void testIndexOf() {
            list.addLong(100);
            list.addLong(200);
            list.addLong(300);

            assertEquals(0, list.indexOfLong(100));
            assertEquals(1, list.indexOfLong(200));
            assertEquals(2, list.indexOfLong(300));
            assertEquals(-1, list.indexOfLong(999));
        }

        @Test
        @DisplayName("should find boxed element")
        void testIndexOfBoxed() {
            list.addLong(100);
            list.addLong(200);

            assertEquals(0, list.indexOf(100L));
            assertEquals(1, list.indexOf(200L));
            assertEquals(-1, list.indexOf(999L));
        }

        @Test
        @DisplayName("should return -1 for wrong type in indexOf")
        void testIndexOfWrongType() {
            list.addLong(100);

            assertEquals(-1, list.indexOf("not a number"));
            assertEquals(-1, list.indexOf(new Object()));
        }

        @Test
        @DisplayName("should test contains")
        void testContains() {
            list.addLong(50);
            list.addLong(100);

            assertTrue(list.containsLong(50));
            assertTrue(list.contains(100L));
            assertFalse(list.containsLong(150));
            assertFalse(list.contains(999L));
        }

        @Test
        @DisplayName("should handle large list search efficiently")
        void testLargeListSearch() {
            // Add more than 8 elements to test unrolled loop
            for (int i = 0; i < 20; i++) {
                list.addLong((i * 10));
            }

            assertEquals(0, list.indexOfLong(0));
            assertEquals(10, list.indexOfLong(100L));
            assertEquals(19, list.indexOfLong(190L));
            assertEquals(-1, list.indexOfLong(200L));
        }

        @Test
        @DisplayName("should find first occurrence")
        void testIndexOfFirstOccurrence() {
            list.addLong(10);
            list.addLong(20);
            list.addLong(10);
            list.addLong(30);

            assertEquals(0, list.indexOfLong(10));
        }
    }

    @Nested
    @DisplayName("Bulk Operations")
    class BulkOperations {

        private LongList list;

        @BeforeEach
        void setUp() {
            list = LongList.builder().build();
        }

        @Test
        @DisplayName("should add all from array")
        void testAddAllArray() {
            long[] values = {1, 2, 3, 4};
            list.addAllLong(values);

            assertEquals(4, list.size());
            assertEquals(1, list.getLong(0));
            assertEquals(4, list.getLong(3));
        }

        @Test
        @DisplayName("should add all from array with offset")
        void testAddAllArrayWithOffset() {
            long[] values = {10, 20, 30, 40, 50};
            list.addAllLong(values, 1, 3);

            assertEquals(3, list.size());
            assertEquals(20, list.getLong(0));
            assertEquals(30, list.getLong(1));
            assertEquals(40, list.getLong(2));
        }

        @Test
        @DisplayName("should handle empty addAll")
        void testAddAllEmpty() {
            long[] empty = {};
            list.addAllLong(empty);
            assertEquals(0, list.size());
        }

        @Test
        @DisplayName("should handle zero-length addAll")
        void testAddAllZeroLength() {
            long[] values = {1, 2, 3};
            list.addAllLong(values, 1, 0);
            assertEquals(0, list.size());
        }

        @Test
        @DisplayName("should throw on invalid offset/length")
        void testAddAllInvalidRange() {
            long[] values = {1, 2, 3};

            assertThrows(IndexOutOfBoundsException.class, () -> list.addAllLong(values, -1, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> list.addAllLong(values, 0, 10));
            assertThrows(IndexOutOfBoundsException.class, () -> list.addAllLong(values, 2, 5));
        }

        @Test
        @DisplayName("should convert to array")
        void testToArray() {
            list.addLong(111);
            list.addLong(222);
            list.addLong(333);

            long[] array = list.toLongArray();

            assertEquals(3, array.length);
            assertEquals(111, array[0]);
            assertEquals(222, array[1]);
            assertEquals(333, array[2]);
        }

        @Test
        @DisplayName("should return independent array copy")
        void testToArrayIndependent() {
            list.addLong(10);

            long[] array = list.toLongArray();
            array[0] = 999;

            assertEquals(10, list.getLong(0));
        }
    }

    @Nested
    @DisplayName("Null Value Handling")
    class NullValueHandling {

        @Test
        @DisplayName("should handle null with default null value")
        void testDefaultNullValue() {
            LongList list = LongList.builder().build();

            list.add(null);
            list.add(10L);
            list.add(null);

            assertEquals(3, list.size());
            assertNull(list.get(0));
            assertEquals(10, list.get(1));
            assertNull(list.get(2));

            assertEquals(LongList.DEFAULT_NULL_VALUE, list.getLong(0));
            assertEquals(10, list.getLong(1));
        }

        @Test
        @DisplayName("should handle custom null value")
        void testCustomNullValue() {
            LongList list = LongList.builder().nullValue(-1).build();

            list.add(null);
            list.add(10L);

            assertNull(list.get(0));
            assertEquals(10, list.get(1));
            assertEquals(-1, list.getLong(0));
        }

        @Test
        @DisplayName("should disable null value handling")
        void testDisableNullValue() {
            LongList list = LongList.builder().disableNullValue().build();

            list.add(10L);
            assertEquals(10, list.get(0));

            // With null value disabled, null is not handled specially
            assertThrows(NullPointerException.class, () -> list.add(null));
        }

        @Test
        @DisplayName("should find null value")
        void testIndexOfNull() {
            LongList list = LongList.builder().nullValue(-999).build();

            list.add(10L);
            list.add(null);
            list.add(20L);

            assertEquals(1, list.indexOf(null));
            assertEquals(1, list.indexOfLong(-999));
        }
    }

    @Nested
    @DisplayName("Capacity Management")
    class CapacityManagement {

        @Test
        @DisplayName("should grow capacity automatically")
        void testAutoGrow() {
            LongList list = LongList.builder().initialCapacity(2).build();

            for (int i = 0; i < 100; i++) {
                list.addLong(i);
            }

            assertEquals(100, list.size());
            for (int i = 0; i < 100; i++) {
                assertEquals(i, list.getLong(i));
            }
        }

        @Test
        @DisplayName("should respect initial capacity")
        void testInitialCapacity() {
            LongList list = LongList.builder().initialCapacity(100).build();

            for (int i = 0; i < 50; i++) {
                list.addLong(i);
            }

            assertEquals(50, list.size());
        }

        @Test
        @DisplayName("should handle minimum capacity of 1")
        void testMinimumCapacity() {
            LongList list = LongList.builder().initialCapacity(0).build();

            list.addLong(42);
            assertEquals(1, list.size());
            assertEquals(42, list.getLong(0));
        }

        @Test
        @DisplayName("should ensure capacity")
        void testEnsureCapacity() {
            LongList list = LongList.builder().initialCapacity(8).build();

            list.ensureCapacity(1000);

            for (int i = 0; i < 1000; i++) {
                list.addLong(i);
            }

            assertEquals(1000, list.size());
        }

        @Test
        @DisplayName("should switch to 1.5x growth for large arrays")
        void testLargeArrayGrowth() {
            LongList list = LongList.builder().initialCapacity(1024 * 1024 + 1).build();

            // Should use 1.5x growth instead of 2x
            for (int i = 0; i < 100; i++) {
                list.addLong(i);
            }

            assertEquals(100, list.size());
        }
    }

    @Nested
    @DisplayName("Iterator Pool")
    class IteratorPoolTests {

        private LongList list;

        @BeforeEach
        void setUp() {
            list = LongList.builder().build();
            list.addLong(10);
            list.addLong(20);
            list.addLong(30);
        }

        @Test
        @DisplayName("should borrow and return iterator")
        void testBorrowReturn() {
            assertEquals(8, list.availableIteratorCount());

            LongList.LongListIterator iter = list.borrowIterator();
            assertNotNull(iter);
            assertEquals(7, list.availableIteratorCount());

            list.returnIterator(iter);
            assertEquals(8, list.availableIteratorCount());
        }

        @Test
        @DisplayName("should iterate forward")
        void testIterateForward() {
            try (LongList.LongListIterator iter = list.borrowIterator()) {
                assertTrue(iter.hasNext());
                assertEquals(10, iter.nextLong());
                assertEquals(20, iter.nextLong());
                assertEquals(30, iter.nextLong());
                assertFalse(iter.hasNext());
            }
        }

        @Test
        @DisplayName("should iterate backward")
        void testIterateBackward() {
            try (LongList.LongListIterator iter = list.borrowIterator(3)) {
                assertTrue(iter.hasPrevious());
                assertEquals(30, iter.previousLong());
                assertEquals(20, iter.previousLong());
                assertEquals(10, iter.previousLong());
                assertFalse(iter.hasPrevious());
            }
        }

        @Test
        @DisplayName("should set during iteration")
        void testIteratorSet() {
            try (LongList.LongListIterator iter = list.borrowIterator()) {
                iter.nextLong();
                iter.setLong(99);
            }

            assertEquals(99, list.getLong(0));
        }

        @Test
        @DisplayName("should throw when setting without next/previous")
        void testIteratorSetWithoutMoving() {
            try (LongList.LongListIterator iter = list.borrowIterator()) {
                assertThrows(IllegalStateException.class, () -> iter.setLong(0));
            }
        }

        @Test
        @DisplayName("should return null when pool exhausted")
        void testPoolExhaustion() {
            LongList.LongListIterator[] iters = new LongList.LongListIterator[8];

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

            try (LongList.LongListIterator iter = list.borrowIterator()) {
                assertEquals(7, list.availableIteratorCount());
                iter.nextLong();
            }

            assertEquals(8, list.availableIteratorCount());
        }

        @Test
        @DisplayName("should throw on next when at end")
        void testNextPastEnd() {
            try (LongList.LongListIterator iter = list.borrowIterator()) {
                iter.nextLong();
                iter.nextLong();
                iter.nextLong();
                assertThrows(NoSuchElementException.class, iter::nextLong);
            }
        }

        @Test
        @DisplayName("should throw on previous when at start")
        void testPreviousPastStart() {
            try (LongList.LongListIterator iter = list.borrowIterator(0)) {
                assertThrows(NoSuchElementException.class, iter::previousLong);
            }
        }

        @Test
        @DisplayName("should report correct indices")
        void testIteratorIndices() {
            try (LongList.LongListIterator iter = list.borrowIterator()) {
                assertEquals(0, iter.nextIndex());
                assertEquals(-1, iter.previousIndex());

                iter.nextLong();

                assertEquals(1, iter.nextIndex());
                assertEquals(0, iter.previousIndex());
            }
        }

        @Test
        @DisplayName("should handle iterator starting at middle")
        void testIteratorStartMiddle() {
            try (LongList.LongListIterator iter = list.borrowIterator(1)) {
                assertEquals(1, iter.nextIndex());
                assertEquals(0, iter.previousIndex());

                assertEquals(20, iter.nextLong());
                assertEquals(20, iter.previousLong());
            }
        }

        @Test
        @DisplayName("should not return wrong iterator")
        void testReturnWrongIterator() {
            LongList list2 = LongList.builder().build();
            list2.addLong(1);

            LongList.LongListIterator iter = list.borrowIterator();

            // Returning to wrong list should be ignored
            list2.returnIterator(iter);
            assertEquals(7, list.availableIteratorCount());
            assertEquals(8, list2.availableIteratorCount());
        }
    }

    @Nested
    @DisplayName("Boxed Iterator")
    class BoxedIteratorTests {

        private LongList list;

        @BeforeEach
        void setUp() {
            list = LongList.builder().build();
            list.addLong(100);
            list.addLong(200);
            list.addLong(300);
        }

        @Test
        @DisplayName("should iterate with boxed iterator")
        void testBoxedIterator() {
            ListIterator<Long> iter = list.listIterator();

            assertTrue(iter.hasNext());
            assertEquals(100, iter.next());
            assertEquals(200, iter.next());
            assertEquals(300, iter.next());
            assertFalse(iter.hasNext());
        }

        @Test
        @DisplayName("should support bidirectional iteration")
        void testBidirectional() {
            ListIterator<Long> iter = list.listIterator();

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
            ListIterator<Long> iter = list.listIterator();
            iter.next();
            iter.set(999L);

            assertEquals(999, list.getLong(0));
        }

        @Test
        @DisplayName("should handle null values in boxed iterator")
        void testBoxedIteratorNull() {
            LongList nullList = LongList.builder().nullValue(-1).build();

            nullList.add(null);
            nullList.add(100L);

            ListIterator<Long> iter = nullList.listIterator();
            assertNull(iter.next());
            assertEquals(100, iter.next());
        }

        @Test
        @DisplayName("should throw on unsupported operations")
        void testUnsupportedOperations() {
            ListIterator<Long> iter = list.listIterator();

            assertThrows(UnsupportedOperationException.class, () -> iter.add(0L));
            assertThrows(UnsupportedOperationException.class, iter::remove);
        }

        @Test
        @DisplayName("should report correct indices")
        void testBoxedIteratorIndices() {
            ListIterator<Long> iter = list.listIterator();

            assertEquals(0, iter.nextIndex());
            assertEquals(-1, iter.previousIndex());

            iter.next();

            assertEquals(1, iter.nextIndex());
            assertEquals(0, iter.previousIndex());
        }

        @Test
        @DisplayName("should start at specified index")
        void testBoxedIteratorStartIndex() {
            ListIterator<Long> iter = list.listIterator(1);

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
            LongList list = LongList.builder().build();

            assertEquals(0, list.size());
            assertEquals(8, list.availableIteratorCount());
        }

        @Test
        @DisplayName("should disable iterator pool")
        void testDisableIteratorPool() {
            LongList list = LongList.builder().disableIteratorPool().build();

            assertEquals(0, list.availableIteratorCount());
            assertNull(list.borrowIterator());
        }

        @Test
        @DisplayName("should configure all options")
        void testFullConfiguration() {
            LongList list = LongList.builder().initialCapacity(50).nullValue(-999).disableIteratorPool().build();

            list.add(null);
            assertEquals(-999, list.getLong(0));
            assertEquals(0, list.availableIteratorCount());
        }

        @Test
        @DisplayName("should chain builder methods")
        void testBuilderChaining() {
            LongList list = LongList.builder().initialCapacity(10).nullValue(-1).build();

            assertNotNull(list);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty list operations")
        void testEmptyList() {
            LongList list = LongList.builder().build();

            assertTrue(list.isEmpty());
            assertEquals(0, list.size());
            assertEquals(-1, list.indexOfLong(0));
            assertFalse(list.containsLong(0));

            long[] array = list.toLongArray();
            assertEquals(0, array.length);
        }

        @Test
        @DisplayName("should handle single element")
        void testSingleElement() {
            LongList list = LongList.builder().build();
            list.addLong(42);

            assertEquals(1, list.size());
            assertEquals(42, list.getLong(0));
            assertEquals(0, list.indexOfLong(42));

            list.clear();
            assertTrue(list.isEmpty());
        }

        @Test
        @DisplayName("should handle repeated elements")
        void testRepeatedElements() {
            LongList list = LongList.builder().build();
            list.addLong(7);
            list.addLong(7);
            list.addLong(7);

            assertEquals(3, list.size());
            assertEquals(0, list.indexOfLong(7));
        }

        @Test
        @DisplayName("should handle zero value")
        void testZeroValue() {
            LongList list = LongList.builder().build();
            list.addLong(0);
            list.addLong(1);
            list.addLong(0);

            assertEquals(0, list.indexOfLong(0));
            assertTrue(list.containsLong(0));
        }
    }

    @Nested
    @DisplayName("Longegration Tests")
    class LongegrationTests {

        @Test
        @DisplayName("should handle complex workflow")
        void testComplexWorkflow() {
            LongList list = LongList.builder().initialCapacity(4).build();

            // Add elements
            for (int i = 0; i < 10; i++) {
                list.addLong((i * 10));
            }

            // Search
            assertEquals(5, list.indexOfLong(50));

            // Modify
            list.setLong(5, 999);
            assertEquals(999, list.getLong(5));

            // Remove
            list.remove(0);
            assertEquals(9, list.size());
            assertEquals(10, list.getLong(0));

            // Iterate
            int count = 0;
            try (LongList.LongListIterator iter = list.borrowIterator()) {
                while (iter.hasNext()) {
                    iter.nextLong();
                    count++;
                }
            }
            assertEquals(9, count);

            // Bulk add
            long[] more = {1000, 2000};
            list.addAllLong(more);
            assertEquals(11, list.size());

            // Clear
            list.clear();
            assertTrue(list.isEmpty());
        }

        @Test
        @DisplayName("should handle stress test")
        void testStressTest() {
            LongList list = LongList.builder().build();

            // Add many elements
            for (int i = 0; i < 10000; i++) {
                list.addLong(i);
            }

            assertEquals(10000, list.size());

            // Search in large list
            assertEquals(5000, list.indexOfLong(5000));

            // Remove from middle repeatedly
            for (int i = 0; i < 100; i++) {
                list.remove(5000);
            }

            assertEquals(9900, list.size());
        }
    }
}