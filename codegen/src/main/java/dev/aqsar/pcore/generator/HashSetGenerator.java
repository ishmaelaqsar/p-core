package dev.aqsar.pcore.generator;

import org.stringtemplate.v4.ST;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class HashSetGenerator {

    private static final List<PrimitiveConfig> TYPES = List.of(
            // Integer types - use direct values for sentinel/null checking
            new PrimitiveConfig("int",
                                "Integer",
                                "Int",
                                "",
                                "",
                                "",
                                "this.state = null",
                                "return v1 == v2;",
                                "elements[slot] == 0",
                                "elements[slot] != 0",
                                "elements[slot] == 0",
                                "",
                                "elements[slot] = 0",
                                "Arrays.fill(elements, 0)",
                                """
                                int h = value;
                                h ^= (h >>> 16);
                                return h;""",
                                "",
                                "",
                                "oldElements[i] != 0"),

            new PrimitiveConfig("long",
                                "Long",
                                "Long",
                                "",
                                "",
                                "",
                                "this.state = null",
                                "return v1 == v2;",
                                "elements[slot] == 0L",
                                "elements[slot] != 0L",
                                "elements[slot] == 0L",
                                "",
                                "elements[slot] = 0L",
                                "Arrays.fill(elements, 0L)",
                                """
                                long h = value;
                                h ^= (h >>> 32);
                                return (int) h;""",
                                "",
                                "",
                                "oldElements[i] != 0L"),

            // Float types - need state array for empty/tombstone tracking
            new PrimitiveConfig("float",
                                "Float",
                                "Float",
                                """
                                private static final byte EMPTY = 0;
                                private static final byte OCCUPIED = 1;
                                private static final byte TOMBSTONE = 2;""",
                                "private byte[] state;",
                                "this.state = new byte[capacity]",
                                """
                                this.state = new byte[capacity];
                                Arrays.fill(this.state, EMPTY)""",
                                "return Float.floatToIntBits(v1) == Float.floatToIntBits(v2);",
                                "state[slot] == EMPTY",
                                "state[slot] == OCCUPIED",
                                "state[slot] != OCCUPIED",
                                "state[slot] = OCCUPIED",
                                "state[slot] = TOMBSTONE",
                                """
                                Arrays.fill(state, EMPTY);
                                Arrays.fill(elements, 0.0f)""",
                                """
                                int bits = Float.floatToIntBits(value);
                                bits ^= (bits >>> 16);
                                return bits;""",
                                "final byte[] oldState = state",
                                """
                                this.state = new byte[newCapacity];
                                Arrays.fill(this.state, EMPTY)""",
                                "oldState[i] == OCCUPIED"),

            new PrimitiveConfig("double",
                                "Double",
                                "Double",
                                """
                                private static final byte EMPTY = 0;
                                private static final byte OCCUPIED = 1;
                                private static final byte TOMBSTONE = 2;""",
                                "private byte[] state;",
                                "this.state = new byte[capacity]",
                                """
                                this.state = new byte[capacity];
                                Arrays.fill(this.state, EMPTY)""",
                                "return Double.doubleToLongBits(v1) == Double.doubleToLongBits(v2);",
                                "state[slot] == EMPTY",
                                "state[slot] == OCCUPIED",
                                "state[slot] != OCCUPIED",
                                "state[slot] = OCCUPIED",
                                "state[slot] = TOMBSTONE",
                                """
                                Arrays.fill(state, EMPTY);
                                Arrays.fill(elements, 0.0)""",
                                """
                                long bits = Double.doubleToLongBits(value);
                                bits ^= (bits >>> 32);
                                return (int) bits;""",
                                "final byte[] oldState = state",
                                """
                                this.state = new byte[newCapacity];
                                Arrays.fill(this.state, EMPTY)""",
                                "oldState[i] == OCCUPIED"));

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: HashSetGenerator <output-dir>");
            System.exit(1);
        }

        final Path outputDir = Paths.get(args[0]);
        final Path templatePath = Paths.get("src/main/templates/PrimitiveHashSet.st");
        final String templateContent = Files.readString(templatePath);

        final Path packageDir = outputDir.resolve("dev/aqsar/pcore/collections");
        Files.createDirectories(packageDir);

        for (final PrimitiveConfig config : TYPES) {
            final ST st = new ST(templateContent, '#', '#');
            st.add("primitive", config.primitive);
            st.add("boxed", config.boxed);
            st.add("upper", config.upper);
            st.add("sentinel_declarations", config.sentinelDeclarations);
            st.add("state_array_declaration", config.stateArrayDeclaration);
            st.add("state_array_init", config.stateArrayInit);
            st.add("value_equals", config.valueEquals);
            st.add("is_empty", config.isEmpty);
            st.add("is_occupied", config.isOccupied);
            st.add("is_empty_or_tombstone", config.isEmptyOrTombstone);
            st.add("mark_occupied", config.markOccupied);
            st.add("mark_tombstone", config.markTombstone);
            st.add("clear_state", config.clearState);
            st.add("hash_implementation", config.hashImplementation);
            st.add("save_old_state", config.saveOldState);
            st.add("state_array_init_resize", config.stateArrayInitResize);
            st.add("was_occupied", config.wasOccupied);

            final String className = config.upper + "HashSet.java";
            final Path classFile = packageDir.resolve(className);
            Files.writeString(classFile, st.render());
            System.out.printf("✅ Generated %s%n", classFile);
        }
    }

    private record PrimitiveConfig(String primitive,
                                   String boxed,
                                   String upper,
                                   String sentinelDeclarations,
                                   String stateArrayDeclaration,
                                   String stateArrayInit,
                                   String stateArrayInitFull,
                                   String valueEquals,
                                   String isEmpty,
                                   String isOccupied,
                                   String isEmptyOrTombstone,
                                   String markOccupied,
                                   String markTombstone,
                                   String clearState,
                                   String hashImplementation,
                                   String saveOldState,
                                   String stateArrayInitResize,
                                   String wasOccupied) {}
}