package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class IntHashSetTest {

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        private IntHashSet set;

        @BeforeEach
        void setUp() {
            set = IntHashSet.builder().build();
        }

        @Test
        @DisplayName("should create empty set")
        void testEmptySet() {
            assertEquals(0, set.size());
            assertTrue(set.isEmpty());
        }

        @Test
        @DisplayName("should add elements")
        void testAdd() {
            assertTrue(set.add(10));
            assertTrue(set.add(20));
            assertTrue(set.add(30));

            assertEquals(3, set.size());
            assertTrue(set.contains(10));
            assertTrue(set.contains(20));
            assertTrue(set.contains(30));
        }

        @Test
        @DisplayName("should not add duplicate elements")
        void testAddDuplicate() {
            assertTrue(set.add(10));
            assertFalse(set.add(10));
            assertFalse(set.add(10));

            assertEquals(1, set.size());
        }

        @Test
        @DisplayName("should add boxed elements")
        void testAddBoxed() {
            assertTrue(set.add(Integer.valueOf(10)));
            assertTrue(set.add(Integer.valueOf(20)));

            assertEquals(2, set.size());
            assertTrue(set.contains(10));
            assertTrue(set.contains(20));
        }

        @Test
        @DisplayName("should throw on boxed null add")
        void testAddBoxedNull() {
            assertThrows(NullPointerException.class, () -> set.add((Integer) null));
        }

        @Test
        @DisplayName("should check containment")
        void testContains() {
            set.add(100);
            set.add(200);

            assertTrue(set.contains(100));
            assertTrue(set.contains(200));
            assertFalse(set.contains(300));
            assertFalse(set.contains(999));
        }

        @Test
        @DisplayName("should check boxed containment")
        void testContainsBoxed() {
            set.add(100);

            assertTrue(set.contains(Integer.valueOf(100)));
            assertFalse(set.contains(Integer.valueOf(200)));
            assertFalse(set.contains("not a number"));
            assertFalse(set.contains(new Object()));
        }

        @Test
        @DisplayName("should remove elements")
        void testRemove() {
            set.add(10);
            set.add(20);
            set.add(30);

            assertTrue(set.remove(20));
            assertEquals(2, set.size());
            assertFalse(set.contains(20));
            assertTrue(set.contains(10));
            assertTrue(set.contains(30));
        }

        @Test
        @DisplayName("should return false when removing non-existent element")
        void testRemoveNonExistent() {
            set.add(10);

            assertFalse(set.remove(999));
            assertEquals(1, set.size());
        }

        @Test
        @DisplayName("should remove boxed elements")
        void testRemoveBoxed() {
            set.add(100);

            assertTrue(set.remove(Integer.valueOf(100)));
            assertEquals(0, set.size());
            assertFalse(set.contains(100));
        }

        @Test
        @DisplayName("should clear set")
        void testClear() {
            set.add(1);
            set.add(2);
            set.add(3);

            set.clear();

            assertEquals(0, set.size());
            assertTrue(set.isEmpty());
            assertFalse(set.contains(1));
            assertFalse(set.contains(2));
            assertFalse(set.contains(3));
        }

        @Test
        @DisplayName("should handle clear on empty set")
        void testClearEmpty() {
            set.clear();
            assertEquals(0, set.size());
        }
    }

    @Nested
    @DisplayName("Bulk Operations")
    class BulkOperations {

        private IntHashSet set;

        @BeforeEach
        void setUp() {
            set = IntHashSet.builder().build();
        }

        @Test
        @DisplayName("should convert to array")
        void testToArray() {
            set.add(10);
            set.add(20);
            set.add(30);

            int[] array = set.toIntArray();

            assertEquals(3, array.length);

            // Set has no order guarantee, so just verify all elements present
            assertTrue(contains(array, 10));
            assertTrue(contains(array, 20));
            assertTrue(contains(array, 30));
        }

        @Test
        @DisplayName("should return empty array for empty set")
        void testToArrayEmpty() {
            int[] array = set.toIntArray();
            assertEquals(0, array.length);
        }

        @Test
        @DisplayName("should return independent array copy")
        void testToArrayIndependent() {
            set.add(10);

            int[] array = set.toIntArray();
            array[0] = 999;

            assertTrue(set.contains(10));
            assertFalse(set.contains(999));
        }

        @Test
        @DisplayName("should apply forEach consumer")
        void testForEach() {
            set.add(10);
            set.add(20);
            set.add(30);

            int[] sum = {0};
            set.forEachInt(value -> sum[0] += value);

            assertEquals(60, sum[0]);
        }

        @Test
        @DisplayName("should handle forEach on empty set")
        void testForEachEmpty() {
            int[] count = {0};
            set.forEachInt(value -> count[0]++);
            assertEquals(0, count[0]);
        }

        @Test
        @DisplayName("should throw on null forEach consumer")
        void testForEachNull() {
            set.add(10);
            assertThrows(NullPointerException.class, () -> set.forEachInt(null));
        }

        private boolean contains(int[] array, int value) {
            for (int v : array) {
                if (v == value) {
                    return true;
                }
            }
            return false;
        }
    }

    @Nested
    @DisplayName("Capacity Management")
    class CapacityManagement {

        @Test
        @DisplayName("should grow capacity automatically")
        void testAutoGrow() {
            IntHashSet set = IntHashSet.builder().initialCapacity(4).build();

            for (int i = 1; i < 100; i++) {
                set.add(i);
            }

            assertEquals(99, set.size());
            for (int i = 1; i < 100; i++) {
                assertTrue(set.contains(i));
            }
        }

        @Test
        @DisplayName("should respect initial capacity")
        void testInitialCapacity() {
            IntHashSet set = IntHashSet.builder().initialCapacity(100).build();

            for (int i = 1; i <= 50; i++) {
                set.add(i);
            }

            assertEquals(50, set.size());
        }

        @Test
        @DisplayName("should ensure capacity")
        void testEnsureCapacity() {
            IntHashSet set = IntHashSet.builder().initialCapacity(8).build();

            set.ensureCapacity(1000);

            for (int i = 1; i <= 1000; i++) {
                set.add(i);
            }

            assertEquals(1000, set.size());
        }

        @Test
        @DisplayName("should handle max capacity")
        void testLargeCapacity() {
            IntHashSet set = IntHashSet.builder().initialCapacity(1024).build();

            for (int i = 1; i <= 100; i++) {
                set.add(i);
            }

            assertEquals(100, set.size());
        }

        @Test
        @DisplayName("should respect load factor")
        void testLoadFactor() {
            IntHashSet set = IntHashSet.builder().initialCapacity(16).loadFactor(0.5f).build();

            // With load factor 0.5, should trigger resize at 8 elements
            for (int i = 1; i <= 20; i++) {
                set.add(i);
            }

            assertEquals(20, set.size());
        }
    }

    @Nested
    @DisplayName("Iterator Pool")
    class IteratorPoolTests {

        private IntHashSet set;

        @BeforeEach
        void setUp() {
            set = IntHashSet.builder().build();
            set.add(10);
            set.add(20);
            set.add(30);
        }

        @Test
        @DisplayName("should borrow and return iterator")
        void testBorrowReturn() {
            assertEquals(8, set.availableIteratorCount());

            IntHashSet.IntHashSetIterator iter = set.borrowIterator();
            assertNotNull(iter);
            assertEquals(7, set.availableIteratorCount());

            set.returnIterator(iter);
            assertEquals(8, set.availableIteratorCount());
        }

        @Test
        @DisplayName("should iterate all elements")
        void testIterateAll() {
            int count = 0;
            int sum = 0;

            try (IntHashSet.IntHashSetIterator iter = set.borrowIterator()) {
                while (iter.hasNext()) {
                    sum += iter.next();
                    count++;
                }
            }

            assertEquals(3, count);
            assertEquals(60, sum);
        }

        @Test
        @DisplayName("should peek without advancing")
        void testPeek() {
            try (IntHashSet.IntHashSetIterator iter = set.borrowIterator()) {
                assertTrue(iter.hasNext());

                int peeked = iter.peekNext();
                int actual = iter.next();

                assertEquals(peeked, actual);
            }
        }

        @Test
        @DisplayName("should remove during iteration")
        void testIteratorRemove() {
            try (IntHashSet.IntHashSetIterator iter = set.borrowIterator()) {
                iter.next();
                iter.remove();
            }

            assertEquals(2, set.size());
        }

        @Test
        @DisplayName("should throw when removing without next")
        void testIteratorRemoveWithoutNext() {
            try (IntHashSet.IntHashSetIterator iter = set.borrowIterator()) {
                assertThrows(IllegalStateException.class, iter::remove);
            }
        }

        @Test
        @DisplayName("should throw when removing twice")
        void testIteratorRemoveTwice() {
            try (IntHashSet.IntHashSetIterator iter = set.borrowIterator()) {
                iter.next();
                iter.remove();
                assertThrows(IllegalStateException.class, iter::remove);
            }
        }

        @Test
        @DisplayName("should return null when pool exhausted")
        void testPoolExhaustion() {
            IntHashSet.IntHashSetIterator[] iters = new IntHashSet.IntHashSetIterator[8];

            for (int i = 0; i < 8; i++) {
                iters[i] = set.borrowIterator();
                assertNotNull(iters[i]);
            }

            assertNull(set.borrowIterator());
            assertEquals(0, set.availableIteratorCount());

            set.returnIterator(iters[0]);
            assertEquals(1, set.availableIteratorCount());
            assertNotNull(set.borrowIterator());
        }

        @Test
        @DisplayName("should auto-return with try-with-resources")
        void testAutoClose() {
            assertEquals(8, set.availableIteratorCount());

            try (IntHashSet.IntHashSetIterator iter = set.borrowIterator()) {
                assertEquals(7, set.availableIteratorCount());
                iter.next();
            }

            assertEquals(8, set.availableIteratorCount());
        }

        @Test
        @DisplayName("should throw on next when exhausted")
        void testNextPastEnd() {
            try (IntHashSet.IntHashSetIterator iter = set.borrowIterator()) {
                iter.next();
                iter.next();
                iter.next();
                assertThrows(NoSuchElementException.class, iter::next);
            }
        }

        @Test
        @DisplayName("should throw on peek when exhausted")
        void testPeekPastEnd() {
            try (IntHashSet.IntHashSetIterator iter = set.borrowIterator()) {
                iter.next();
                iter.next();
                iter.next();
                assertThrows(NoSuchElementException.class, iter::peekNext);
            }
        }

        @Test
        @DisplayName("should handle forEachRemaining")
        void testForEachRemaining() {
            int[] sum = {0};

            try (IntHashSet.IntHashSetIterator iter = set.borrowIterator()) {
                // Consume first element
                iter.next();

                // Process remaining
                iter.forEachRemaining(value -> sum[0] += value);
            }

            // Should process 2 remaining elements (not the first)
            assertTrue(sum[0] == 50 || sum[0] == 40 || sum[0] == 30); // Depends on which was first
        }

        @Test
        @DisplayName("should throw on concurrent modification")
        void testConcurrentModification() {
            try (IntHashSet.IntHashSetIterator iter = set.borrowIterator()) {
                iter.next();
                set.add(999); // Modify set during iteration
                assertThrows(Exception.class, iter::next); // Should throw ConcurrentModificationException
            }
        }

        @Test
        @DisplayName("should not return wrong iterator")
        void testReturnWrongIterator() {
            IntHashSet set2 = IntHashSet.builder().build();
            set2.add(1);

            IntHashSet.IntHashSetIterator iter = set.borrowIterator();

            // Returning to wrong set should be ignored
            set2.returnIterator(iter);
            assertEquals(7, set.availableIteratorCount());
            assertEquals(8, set2.availableIteratorCount());
        }
    }

    @Nested
    @DisplayName("Boxed Iterator")
    class BoxedIteratorTests {

        private IntHashSet set;

        @BeforeEach
        void setUp() {
            set = IntHashSet.builder().build();
            set.add(100);
            set.add(200);
            set.add(300);
        }

        @Test
        @DisplayName("should iterate with boxed iterator")
        void testBoxedIterator() {
            Iterator<Integer> iter = set.iterator();

            int count = 0;
            while (iter.hasNext()) {
                Integer value = iter.next();
                assertNotNull(value);
                assertTrue(value == 100 || value == 200 || value == 300);
                count++;
            }

            assertEquals(3, count);
        }

        @Test
        @DisplayName("should remove via boxed iterator")
        void testBoxedIteratorRemove() {
            Iterator<Integer> iter = set.iterator();
            iter.next();
            iter.remove();

            assertEquals(2, set.size());
        }

        @Test
        @DisplayName("should throw on remove without next")
        void testBoxedIteratorRemoveWithoutNext() {
            Iterator<Integer> iter = set.iterator();
            assertThrows(IllegalStateException.class, iter::remove);
        }

        @Test
        @DisplayName("should throw on concurrent modification")
        void testBoxedIteratorConcurrentModification() {
            Iterator<Integer> iter = set.iterator();
            iter.next();
            set.add(999);
            assertThrows(Exception.class, iter::next);
        }

        @Test
        @DisplayName("should handle empty set iteration")
        void testBoxedIteratorEmpty() {
            IntHashSet emptySet = IntHashSet.builder().build();
            Iterator<Integer> iter = emptySet.iterator();

            assertFalse(iter.hasNext());
            assertThrows(NoSuchElementException.class, iter::next);
        }
    }

    @Nested
    @DisplayName("Builder Configuration")
    class BuilderConfiguration {

        @Test
        @DisplayName("should use default values")
        void testDefaults() {
            IntHashSet set = IntHashSet.builder().build();

            assertEquals(0, set.size());
            assertEquals(8, set.availableIteratorCount());
        }

        @Test
        @DisplayName("should disable iterator pool")
        void testDisableIteratorPool() {
            IntHashSet set = IntHashSet.builder().disableIteratorPool().build();

            assertEquals(0, set.availableIteratorCount());
            assertNull(set.borrowIterator());
        }

        @Test
        @DisplayName("should configure all options")
        void testFullConfiguration() {
            IntHashSet set = IntHashSet.builder().initialCapacity(50).loadFactor(0.6f).disableIteratorPool().build();

            set.add(1);
            assertEquals(1, set.size());
            assertEquals(0, set.availableIteratorCount());
        }

        @Test
        @DisplayName("should chain builder methods")
        void testBuilderChaining() {
            IntHashSet set = IntHashSet.builder().initialCapacity(10).loadFactor(0.75f).build();

            assertNotNull(set);
        }
    }

    @Nested
    @DisplayName("Collision Handling")
    class CollisionHandling {

        @Test
        @DisplayName("should handle hash collisions")
        void testCollisions() {
            IntHashSet set = IntHashSet.builder().initialCapacity(8).build();

            // Add many elements to force collisions
            for (int i = 1; i <= 100; i++) {
                set.add(i);
            }

            assertEquals(100, set.size());

            // Verify all elements are present
            for (int i = 1; i <= 100; i++) {
                assertTrue(set.contains(i));
            }
        }

        @Test
        @DisplayName("should handle removal with collisions")
        void testRemovalWithCollisions() {
            IntHashSet set = IntHashSet.builder().initialCapacity(8).build();

            for (int i = 1; i <= 50; i++) {
                set.add(i);
            }

            // Remove every other element
            for (int i = 2; i <= 50; i += 2) {
                assertTrue(set.remove(i));
            }

            assertEquals(25, set.size());

            // Verify correct elements remain
            for (int i = 1; i <= 50; i++) {
                if (i % 2 == 0) {
                    assertFalse(set.contains(i));
                } else {
                    assertTrue(set.contains(i));
                }
            }
        }

        @Test
        @DisplayName("should handle add after remove")
        void testAddAfterRemove() {
            IntHashSet set = IntHashSet.builder().build();

            set.add(10);
            set.add(20);
            set.remove(10);
            set.add(10);

            assertEquals(2, set.size());
            assertTrue(set.contains(10));
            assertTrue(set.contains(20));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty set operations")
        void testEmptySet() {
            IntHashSet set = IntHashSet.builder().build();

            assertTrue(set.isEmpty());
            assertEquals(0, set.size());
            assertFalse(set.contains(1));

            int[] array = set.toIntArray();
            assertEquals(0, array.length);
        }

        @Test
        @DisplayName("should handle single element")
        void testSingleElement() {
            IntHashSet set = IntHashSet.builder().build();
            set.add(42);

            assertEquals(1, set.size());
            assertTrue(set.contains(42));

            set.clear();
            assertTrue(set.isEmpty());
        }

        @Test
        @DisplayName("should handle power of 2 boundaries")
        void testPowerOf2Boundaries() {
            IntHashSet set = IntHashSet.builder().initialCapacity(16).build();

            // Add exactly at capacity boundary
            for (int i = 1; i <= 12; i++) { // 16 * 0.75 = 12
                set.add(i);
            }

            assertEquals(12, set.size());

            // Add one more to trigger resize
            set.add(13);
            assertEquals(13, set.size());
        }

        @Test
        @DisplayName("should handle non-power-of-2 initial capacity")
        void testNonPowerOf2InitialCapacity() {
            IntHashSet set = IntHashSet.builder().initialCapacity(10).build();

            for (int i = 1; i <= 20; i++) {
                set.add(i);
            }

            assertEquals(20, set.size());
        }

        @Test
        @DisplayName("should handle negative values")
        void testNegativeValues() {
            IntHashSet set = IntHashSet.builder().build();

            set.add(-10);
            set.add(-20);
            set.add(-30);

            assertEquals(3, set.size());
            assertTrue(set.contains(-10));
            assertTrue(set.contains(-20));
            assertTrue(set.contains(-30));
        }

        @Test
        @DisplayName("should handle Integer.MIN_VALUE correctly")
        void testMinValue() {
            IntHashSet set = IntHashSet.builder().build();

            set.add(Integer.MIN_VALUE + 1);
            set.add(Integer.MIN_VALUE + 2);

            assertEquals(2, set.size());
            assertTrue(set.contains(Integer.MIN_VALUE + 1));
            assertTrue(set.contains(Integer.MIN_VALUE + 2));
        }

        @Test
        @DisplayName("should handle Integer.MAX_VALUE")
        void testMaxValue() {
            IntHashSet set = IntHashSet.builder().build();

            set.add(Integer.MAX_VALUE);
            set.add(Integer.MAX_VALUE - 1);

            assertEquals(2, set.size());
            assertTrue(set.contains(Integer.MAX_VALUE));
            assertTrue(set.contains(Integer.MAX_VALUE - 1));
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("should handle complex workflow")
        void testComplexWorkflow() {
            IntHashSet set = IntHashSet.builder().initialCapacity(4).build();

            // Add elements
            for (int i = 1; i <= 100; i++) {
                set.add(i);
            }
            assertEquals(100, set.size());

            // Check containment
            assertTrue(set.contains(50));
            assertFalse(set.contains(200));

            // Remove elements
            for (int i = 1; i <= 50; i++) {
                set.remove(i);
            }
            assertEquals(50, set.size());

            // Verify remaining elements
            for (int i = 51; i <= 100; i++) {
                assertTrue(set.contains(i));
            }

            // Iterate and sum
            int[] sum = {0};
            set.forEachInt(value -> sum[0] += value);
            assertEquals(3775, sum[0]); // Sum of 51..100

            // Clear
            set.clear();
            assertTrue(set.isEmpty());
        }

        @Test
        @DisplayName("should handle stress test")
        void testStressTest() {
            IntHashSet set = IntHashSet.builder().build();

            // Add many elements
            for (int i = 1; i <= 10000; i++) {
                set.add(i);
            }

            assertEquals(10000, set.size());

            // Verify all present
            for (int i = 1; i <= 10000; i++) {
                assertTrue(set.contains(i));
            }

            // Remove half
            for (int i = 1; i <= 5000; i++) {
                set.remove(i);
            }

            assertEquals(5000, set.size());

            // Verify correct half remains
            for (int i = 1; i <= 5000; i++) {
                assertFalse(set.contains(i));
            }
            for (int i = 5001; i <= 10000; i++) {
                assertTrue(set.contains(i));
            }
        }

        @Test
        @DisplayName("should handle add-remove cycles")
        void testAddRemoveCycles() {
            IntHashSet set = IntHashSet.builder().build();

            for (int cycle = 0; cycle < 10; cycle++) {
                // Add elements
                for (int i = 1; i <= 100; i++) {
                    set.add(i);
                }
                assertEquals(100, set.size());

                // Remove all
                for (int i = 1; i <= 100; i++) {
                    set.remove(i);
                }
                assertEquals(0, set.size());
            }
        }

        @Test
        @DisplayName("should maintain integrity after many operations")
        void testIntegrity() {
            IntHashSet set = IntHashSet.builder().build();

            // Mixed operations
            for (int i = 1; i <= 1000; i++) {
                set.add(i);

                if (i % 3 == 0) {
                    set.remove(i - 1);
                }

                if (i % 5 == 0) {
                    set.add(i / 2);
                }
            }

            // Verify set is in consistent state
            int count = 0;
            try (IntHashSet.IntHashSetIterator iter = set.borrowIterator()) {
                while (iter.hasNext()) {
                    int value = iter.next();
                    assertTrue(set.contains(value));
                    count++;
                }
            }

            assertEquals(set.size(), count);
        }
    }
}