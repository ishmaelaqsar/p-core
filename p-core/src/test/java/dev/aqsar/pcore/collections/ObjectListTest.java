package dev.aqsar.pcore.collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class ObjectListTest {

    // === Shared helpers =======================================================

    private static ObjectList<String> newList() {
        return ObjectList.<String>builder().build();
    }

    @SafeVarargs
    private static <T> void assertListContent(ObjectList<T> list, T... expected) {
        assertEquals(expected.length, list.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], list.get(i));
        }
    }

    @SafeVarargs
    private static <T> void assertIteratesForward(ObjectList<T> list, T... expected) {
        try (ObjectList<T>.ObjectListIterator iter = list.borrowIterator()) {
            for (T e : expected) {
                assertTrue(iter.hasNext());
                assertEquals(e, iter.next());
            }
            assertFalse(iter.hasNext());
        }
    }

    @SafeVarargs
    private static <T> void assertIteratesBackward(ObjectList<T> list, T... expected) {
        try (ObjectList<T>.ObjectListIterator iter = list.borrowIterator(list.size())) {
            for (int i = expected.length - 1; i >= 0; i--) {
                assertTrue(iter.hasPrevious());
                assertEquals(expected[i], iter.previous());
            }
            assertFalse(iter.hasPrevious());
        }
    }

    // === Basic Operations ======================================================

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {
        private ObjectList<String> list;

        @BeforeEach
        void setUp() {
            list = newList();
        }

        @Test
        void createsEmptyList() {
            assertTrue(list.isEmpty());
            assertEquals(0, list.size());
        }

        @Test
        void addsElements() {
            list.add("a");
            list.add("b");
            list.add("c");
            assertListContent(list, "a", "b", "c");
        }

        @Test
        void addsNullElements() {
            list.add(null);
            list.add("x");
            list.add(null);
            assertListContent(list, null, "x", null);
        }

        @Test
        void getsAndSetsElements() {
            list.add("old");
            assertEquals("old", list.get(0));
            assertEquals("old", list.set(0, "new"));
            assertEquals("new", list.get(0));
        }

        @Test
        void throwsOnInvalidIndex() {
            list.add("test");
            assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> list.get(2));
            assertThrows(IndexOutOfBoundsException.class, () -> list.set(5, "x"));
        }

        @Test
        void removesElements() {
            list.add("a");
            list.add("b");
            list.add("c");
            assertEquals("b", list.remove(1));
            assertListContent(list, "a", "c");
        }

        @Test
        void clearsList() {
            list.add("x");
            list.add("y");
            list.clear();
            assertTrue(list.isEmpty());
        }
    }

    // === Search Operations =====================================================

    @Nested
    @DisplayName("Search Operations")
    class SearchOperations {
        private ObjectList<String> list;

        @BeforeEach
        void setUp() {
            list = newList();
        }

        @Test
        void findsByEqualsAndNull() {
            list.add("apple");
            list.add(null);
            list.add("cherry");
            assertEquals(0, list.indexOf("apple"));
            assertEquals(1, list.indexOf(null));
            assertEquals(2, list.indexOf("cherry"));
            assertEquals(-1, list.indexOf("missing"));
        }

        @Test
        void findsByIdentity() {
            String s1 = new String("a"), s2 = new String("a"), s3 = s1;
            list.add(s1);
            list.add(s2);
            assertEquals(0, list.indexOfIdentity(s1));
            assertEquals(0, list.indexOfIdentity(s3));
            assertEquals(1, list.indexOfIdentity(s2));
        }

        @Test
        void containsChecks() {
            list.add("x");
            list.add("y");
            assertTrue(list.contains("x"));
            assertFalse(list.contains("z"));
        }

        @Test
        void handlesLargeListSearch() {
            for (int i = 0; i < 20; i++) {
                list.add("item" + i);
            }
            assertEquals(0, list.indexOf("item0"));
            assertEquals(19, list.indexOf("item19"));
            assertEquals(-1, list.indexOf("item20"));
        }
    }

    // === Bulk Operations =======================================================

    @Nested
    @DisplayName("Bulk Operations")
    class BulkOperations {
        private ObjectList<String> list;

        @BeforeEach
        void setUp() {
            list = newList();
        }

        @Test
        void addsAllFromArray() {
            list.addAll(new String[]{"a", "b", "c"});
            assertListContent(list, "a", "b", "c");
        }

        @Test
        void addsAllFromArrayWithOffset() {
            list.addAll(new String[]{"a", "b", "c", "d"}, 1, 2);
            assertListContent(list, "b", "c");
        }

        @Test
        void convertsToArray() {
            list.add("x");
            list.add("y");
            String[] arr = list.toArray(String[]::new);
            assertArrayEquals(new String[]{"x", "y"}, arr);
        }
    }

    // === Iterator Pool =========================================================

    @Nested
    @DisplayName("Iterator Pool")
    class IteratorPoolTests {
        private ObjectList<String> list;

        @BeforeEach
        void setUp() {
            list = newList();
            list.add("a");
            list.add("b");
            list.add("c");
        }

        @Test
        void borrowsAndReturnsIterators() {
            var it = list.borrowIterator();
            assertNotNull(it);
            list.returnIterator(it);
            assertEquals(8, list.availableIteratorCount());
        }

        @Test
        void iteratesForwardAndBackward() {
            assertIteratesForward(list, "a", "b", "c");
            assertIteratesBackward(list, "a", "b", "c");
        }

        @Test
        void setsDuringIteration() {
            try (var it = list.borrowIterator()) {
                it.next();
                it.set("X");
            }
            assertEquals("X", list.get(0));
        }

        @Test
        void poolExhaustionHandled() {
            var iters = new ObjectList.ObjectListIterator[8];
            for (int i = 0; i < 8; i++) {
                iters[i] = list.borrowIterator();
            }
            assertNull(list.borrowIterator());
            list.returnIterator(iters[0]);
            assertNotNull(list.borrowIterator());
        }

        @Test
        void autoCloseReturnsIterator() {
            try (var it = list.borrowIterator()) {
                assertEquals(7, list.availableIteratorCount());
            }
            assertEquals(8, list.availableIteratorCount());
        }

        @Test
        void throwsWhenOutOfBounds() {
            try (var it = list.borrowIterator()) {
                it.next();
                it.next();
                it.next();
                assertThrows(NoSuchElementException.class, it::next);
            }
            try (var it = list.borrowIterator(0)) {
                assertThrows(NoSuchElementException.class, it::previous);
            }
        }
    }

    // === Pre-allocation mode ===================================================

    @Nested
    @DisplayName("addPreAllocated")
    class AddPreAllocatedTests {

        static class Counter {
            static int instanceCount = 0;
            final int id;
            int value;

            Counter() {
                id = instanceCount++;
            }

            void reset() {
                value = 0;
            }
        }

        private ObjectList<Counter> list;

        @BeforeEach
        void setUp() {
            Counter.instanceCount = 0;
            list = ObjectList.<Counter>builder().preAllocate(Counter::new).initialCapacity(4).build();
        }

        @Test
        @DisplayName("returns pre-allocated objects without creating new ones")
        void usesPreAllocatedObjects() {
            Counter c1 = list.addPreAllocated();
            Counter c2 = list.addPreAllocated();
            Counter c3 = list.addPreAllocated();

            assertNotNull(c1);
            assertNotNull(c2);
            assertNotNull(c3);

            // No extra allocations should have happened beyond initial capacity
            assertEquals(4, Counter.instanceCount);
            assertEquals(3, list.size());
            assertSame(c1, list.get(0));
            assertSame(c2, list.get(1));
            assertSame(c3, list.get(2));
        }

        @Test
        @DisplayName("reflects changes to returned object in the list")
        void reflectsChangesInList() {
            Counter c = list.addPreAllocated();
            c.value = 42;

            assertEquals(1, list.size());
            assertEquals(42, list.get(0).value);
        }

        @Test
        @DisplayName("allocates more objects on capacity growth")
        void allocatesOnGrowth() {
            int initialAllocated = Counter.instanceCount;

            for (int i = 0; i < 10; i++) {
                list.addPreAllocated();
            }

            assertTrue(Counter.instanceCount > initialAllocated);
            assertEquals(10, list.size());
        }

        @Test
        @DisplayName("reuses pre-allocated objects after clear")
        void reusesObjectsAfterClear() {
            Counter c1 = list.addPreAllocated();
            Counter c2 = list.addPreAllocated();
            int beforeClearCount = Counter.instanceCount;

            list.clear();
            Counter c1Again = list.addPreAllocated();
            Counter c2Again = list.addPreAllocated();

            assertEquals(beforeClearCount, Counter.instanceCount);
            // It may reuse same objects but order can be same
            assertSame(c1, c1Again);
            assertSame(c2, c2Again);
        }

        @Test
        @DisplayName("throws if add(E) is called on pre-allocation list (if enforced)")
        void rejectsNormalAddIfEnforced() {
            // Optional: only relevant if you've added that safety check in your class
            assertThrows(UnsupportedOperationException.class, () -> list.add(new Counter()));
        }
    }

    // === Edge Cases ============================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        @Test
        void handlesEmptyList() {
            ObjectList<String> list = newList();
            assertTrue(list.isEmpty());
            assertEquals(-1, list.indexOf("x"));
            assertEquals(0, list.toArray(String[]::new).length);
        }

        @Test
        void handlesNullAndRepeats() {
            ObjectList<String> list = newList();
            list.add(null);
            list.add(null);
            assertEquals(0, list.indexOf(null));
            list.clear();
            list.add("x");
            list.add("x");
            assertEquals(0, list.indexOf("x"));
        }

        @Test
        void worksWithoutIteratorPool() {
            ObjectList<String> list = ObjectList.<String>builder().disableIteratorPool().build();
            assertNull(list.borrowIterator());
        }
    }
}
