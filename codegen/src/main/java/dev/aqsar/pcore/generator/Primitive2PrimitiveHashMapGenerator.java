package dev.aqsar.pcore.generator;

import org.stringtemplate.v4.ST;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Primitive2PrimitiveHashMapGenerator {

    private static final List<PrimitiveType> TYPES = List.of(
            // Integer types - use sentinel values
            new IntegerType("int", "Integer", "Int", "Integer.MIN_VALUE"),
            new IntegerType("long", "Long", "Long", "Long.MIN_VALUE"),

            // Floating point types - use state array
            new FloatingType("float", "Float", "Float", "Float.NaN"),
            new FloatingType("double", "Double", "Double", "Double.NaN"));

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: Primitive2PrimitiveHashMapGenerator <output-dir>");
            System.exit(1);
        }

        final Path outputDir = Paths.get(args[0]);
        final Path templatePath = Paths.get("src/main/templates/Primitive2PrimitiveHashMap.st");
        final String templateContent = Files.readString(templatePath);

        final Path packageDir = outputDir.resolve("dev/aqsar/pcore/collections");
        Files.createDirectories(packageDir);

        // Generate all key-value combinations
        for (final PrimitiveType keyType : TYPES) {
            for (final PrimitiveType valueType : TYPES) {
                generateHashMap(templateContent, packageDir, keyType, valueType);
            }
        }
    }

    private static void generateHashMap(final String templateContent,
                                        final Path packageDir,
                                        final PrimitiveType keyType,
                                        final PrimitiveType valueType) throws IOException {
        final ST st = new ST(templateContent, '#', '#');

        // Key type parameters
        st.add("primitiveKey", keyType.primitive);
        st.add("boxedKey", keyType.boxed);
        st.add("upperKey", keyType.upper);

        // Value type parameters
        st.add("primitiveValue", valueType.primitive);
        st.add("boxedValue", valueType.boxed);
        st.add("upperValue", valueType.upper);
        st.add("nullValue", valueType.nullValue);

        // Key-specific implementations
        st.add("sentinel_declarations", keyType.getSentinelDeclarations());
        st.add("state_array_declaration", keyType.getStateArrayDeclaration());
        st.add("state_array_init", keyType.getStateArrayInit());
        st.add("clear_state", keyType.getClearState());
        st.add("key_equals", keyType.getEqualsExpression());
        st.add("key_null", keyType.getNullKeyExpression());
        st.add("is_empty", keyType.isEmpty());
        st.add("is_occupied", keyType.isOccupied());
        st.add("is_empty_or_tombstone", keyType.isEmptyOrTombstone());
        st.add("mark_occupied", keyType.markOccupied());
        st.add("mark_tombstone", keyType.markTombstone());
        st.add("hash_implementation", keyType.getHashImplementation());
        st.add("save_old_state", keyType.getSaveOldState());
        st.add("state_array_init_resize", keyType.getStateArrayInitResize());
        st.add("was_occupied", keyType.wasOccupied());

        // Value-specific implementations
        st.add("value_equals", valueType.getEqualsExpression());

        // Add suffix to avoid name collision when key and value types are the same
        String valueSuffix = keyType.upper.equals(valueType.upper) ? "Value" : "";
        st.add("value_suffix", valueSuffix);

        final String className = keyType.upper + "2" + valueType.upper + "HashMap.java";
        final Path classFile = packageDir.resolve(className);
        Files.writeString(classFile, st.render());
        System.out.printf("✅ Generated %s%n", classFile);
    }

    // ==================== Type Hierarchy ====================

    private abstract static class PrimitiveType {
        final String primitive;
        final String boxed;
        final String upper;
        final String nullValue;

        PrimitiveType(String primitive, String boxed, String upper, String nullValue) {
            this.primitive = primitive;
            this.boxed = boxed;
            this.upper = upper;
            this.nullValue = nullValue;
        }

        abstract String getSentinelDeclarations();

        abstract String getStateArrayDeclaration();

        abstract String getStateArrayInit();

        abstract String getClearState();

        abstract String getEqualsExpression();

        abstract String isEmpty();

        abstract String isOccupied();

        abstract String isEmptyOrTombstone();

        abstract String markOccupied();

        abstract String markTombstone();

        abstract String getHashImplementation();

        abstract String getSaveOldState();

        abstract String getStateArrayInitResize();

        abstract String wasOccupied();

        abstract String getNullKeyExpression();
    }

    private static class IntegerType extends PrimitiveType {
        private final String sentinelKey;

        IntegerType(String primitive, String boxed, String upper, String nullValue) {
            super(primitive, boxed, upper, nullValue);
            this.sentinelKey = nullValue; // reuse nullValue for empty/tombstone
        }

        @Override
        String getSentinelDeclarations() {
            return String.format("""
                                 // Sentinel key used for both empty and tombstone slots
                                 private static final %s NULL_KEY = %s;""", primitive, sentinelKey);
        }

        @Override
        String getStateArrayDeclaration() {
            return "// N/A"; // No state array needed for integers
        }

        @Override
        String getStateArrayInit() {
            return "Arrays.fill(keys, NULL_KEY)";
        }

        @Override
        String getClearState() {
            return "Arrays.fill(keys, NULL_KEY)";
        }

        @Override
        String getEqualsExpression() {
            return "v1 == v2";
        }

        @Override
        String isEmpty() {
            return "keys[slot] == NULL_KEY";
        }

        @Override
        String isOccupied() {
            return "keys[slot] != NULL_KEY";
        }

        @Override
        String isEmptyOrTombstone() {
            return "keys[slot] == NULL_KEY";
        }

        @Override
        String markOccupied() {
            return "// No-op for integer types";
        }

        @Override
        String markTombstone() {
            return "keys[slot] = NULL_KEY";
        }

        @Override
        String getHashImplementation() {
            if (primitive.equals("int")) {
                return """
                       int h = key;
                       h ^= (h >>> 16);
                       return h;""";
            } else if (primitive.equals("long")) {
                return """
                       int h = (int)(key ^ (key >>> 32));
                       h ^= (h >>> 16);
                       return h;""";
            }
            return "return (int) key;";
        }

        @Override
        String getSaveOldState() {
            return ""; // No state array to save
        }

        @Override
        String getStateArrayInitResize() {
            return "Arrays.fill(keys, NULL_KEY)";
        }

        @Override
        String wasOccupied() {
            return "oldKeys[i] != NULL_KEY";
        }

        @Override
        String getNullKeyExpression() {
            return "key == NULL_KEY";
        }
    }

    private static class FloatingType extends PrimitiveType {

        FloatingType(String primitive, String boxed, String upper, String nullValue) {
            super(primitive, boxed, upper, nullValue);
        }

        @Override
        String getSentinelDeclarations() {
            return """
                   // State tracking for floating point types (can't use sentinel values)
                   private static final byte STATE_EMPTY = 0;
                   private static final byte STATE_OCCUPIED = 1;
                   private static final byte STATE_TOMBSTONE = 2;""";
        }

        @Override
        String getStateArrayDeclaration() {
            return "private byte[] state;";
        }

        @Override
        String getStateArrayInit() {
            return """
                   this.state = new byte[capacity];
                   Arrays.fill(state, STATE_EMPTY)""";
        }

        @Override
        String getClearState() {
            return "Arrays.fill(state, STATE_EMPTY)";
        }

        @Override
        String getEqualsExpression() {
            if (primitive.equals("double")) {
                return "Double.doubleToLongBits(v1) == Double.doubleToLongBits(v2)";
            } else { // float
                return "Float.floatToIntBits(v1) == Float.floatToIntBits(v2)";
            }
        }

        @Override
        String isEmpty() {
            return "state[slot] == STATE_EMPTY";
        }

        @Override
        String isOccupied() {
            return "state[slot] == STATE_OCCUPIED";
        }

        @Override
        String isEmptyOrTombstone() {
            return "state[slot] != STATE_OCCUPIED";
        }

        @Override
        String markOccupied() {
            return "state[slot] = STATE_OCCUPIED";
        }

        @Override
        String markTombstone() {
            return "state[slot] = STATE_TOMBSTONE";
        }

        @Override
        String getHashImplementation() {
            if (primitive.equals("double")) {
                return """
                       // Normalize NaN to single representation for consistent hashing
                       long bits = Double.doubleToLongBits(key);
                       int h = (int)(bits ^ (bits >>> 32));
                       h ^= (h >>> 16);
                       return h;""";
            } else { // float
                return """
                       // Normalize NaN to single representation for consistent hashing
                       int bits = Float.floatToIntBits(key);
                       int h = bits;
                       h ^= (h >>> 16);
                       return h;""";
            }
        }

        @Override
        String getSaveOldState() {
            return "final byte[] oldState = state;";
        }

        @Override
        String getStateArrayInitResize() {
            return """
                   this.state = new byte[newCapacity];
                   Arrays.fill(state, STATE_EMPTY)""";
        }

        @Override
        String wasOccupied() {
            return "oldState[i] == STATE_OCCUPIED";
        }

        @Override
        String getNullKeyExpression() {
            return "false";
        }
    }
}