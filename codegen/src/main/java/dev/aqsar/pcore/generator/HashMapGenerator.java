package dev.aqsar.pcore.generator;

import org.stringtemplate.v4.ST;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class HashMapGenerator {

    private static final List<PrimitiveType> TYPES =
            List.of(new IntegerType("int", "Integer", "Int", "Integer.MIN_VALUE"),
                    new IntegerType("long", "Long", "Long", "Long.MIN_VALUE"),
                    new FloatingType("float", "Float", "Float", "Float.MIN_VALUE"),
                    new FloatingType("double", "Double", "Double", "Double.MIN_VALUE"));

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: HashMapGenerator <output-dir>");
            System.exit(1);
        }

        final Path outputDir = Paths.get(args[0]);
        final Path templateDir = Paths.get("src/main/templates");
        final Path packageDir = outputDir.resolve("dev/aqsar/pcore/collections");
        Files.createDirectories(packageDir);

        // Generate Primitive2Primitive maps
        final String primitive2PrimitiveTemplate =
                Files.readString(templateDir.resolve("Primitive2PrimitiveHashMap.st"));
        for (final PrimitiveType keyType : TYPES) {
            for (final PrimitiveType valueType : TYPES) {
                generatePrimitive2Primitive(primitive2PrimitiveTemplate, packageDir, keyType, valueType);
            }
        }

        // Generate Primitive2Object maps
        final String primitive2ObjectTemplate = Files.readString(templateDir.resolve("Primitive2ObjectHashMap.st"));
        for (final PrimitiveType keyType : TYPES) {
            generatePrimitive2Object(primitive2ObjectTemplate, packageDir, keyType);
        }

        // Generate Object2Primitive maps
        final String object2PrimitiveTemplate = Files.readString(templateDir.resolve("Object2PrimitiveHashMap.st"));
        for (final PrimitiveType valueType : TYPES) {
            generateObject2Primitive(object2PrimitiveTemplate, packageDir, valueType);
        }
    }

    private static void generatePrimitive2Primitive(final String templateContent,
                                                    final Path packageDir,
                                                    final PrimitiveType keyType,
                                                    final PrimitiveType valueType) throws IOException {
        final ST st = new ST(templateContent, '#', '#');

        st.add("primitiveKey", keyType.primitive);
        st.add("boxedKey", keyType.boxed);
        st.add("upperKey", keyType.upper);

        st.add("primitiveValue", valueType.primitive);
        st.add("boxedValue", valueType.boxed);
        st.add("upperValue", valueType.upper);
        st.add("nullValue", valueType.nullValue);

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

        st.add("value_equals", valueType.getEqualsExpression());

        final String className = keyType.upper + "2" + valueType.upper + "HashMap.java";
        final Path classFile = packageDir.resolve(className);
        Files.writeString(classFile, st.render());
        System.out.printf("✅ Generated %s%n", classFile);
    }

    private static void generatePrimitive2Object(final String templateContent,
                                                 final Path packageDir,
                                                 final PrimitiveType keyType) throws IOException {
        final ST st = new ST(templateContent, '#', '#');

        st.add("primitiveKey", keyType.primitive);
        st.add("boxedKey", keyType.boxed);
        st.add("upperKey", keyType.upper);

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

        final String className = keyType.upper + "2ObjectHashMap.java";
        final Path classFile = packageDir.resolve(className);
        Files.writeString(classFile, st.render());
        System.out.printf("✅ Generated %s%n", classFile);
    }

    private static void generateObject2Primitive(final String templateContent,
                                                 final Path packageDir,
                                                 final PrimitiveType valueType) throws IOException {
        final ST st = new ST(templateContent, '#', '#');

        st.add("primitiveValue", valueType.primitive);
        st.add("boxedValue", valueType.boxed);
        st.add("upperValue", valueType.upper);
        st.add("nullValue", valueType.nullValue);

        st.add("value_equals", valueType.getEqualsExpression());

        final String className = "Object2" + valueType.upper + "HashMap.java";
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
            this.sentinelKey = nullValue;
        }

        @Override
        String getSentinelDeclarations() {
            return String.format("""
                                 // Sentinel key used for both empty and tombstone slots
                                 private static final %s NULL_KEY = %s;""", primitive, sentinelKey);
        }

        @Override
        String getStateArrayDeclaration() {
            return "// N/A";
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
            return "return v1 == v2;";
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
                       int h = Long.hashCode(key);
                       h ^= (h >>> 16);
                       return h;""";
            }
            return "return (int) key;";
        }

        @Override
        String getSaveOldState() {
            return "";
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
                return """
                       final double d1 = (v1 == 0.0) ? 0.0 : v1;
                       final double d2 = (v2 == 0.0) ? 0.0 : v2;
                       return Double.doubleToLongBits(d1) == Double.doubleToLongBits(d2);""";
            } else {
                return """
                       final float f1 = (v1 == 0.0f) ? 0.0f : v1;
                       final float f2 = (v2 == 0.0f) ? 0.0f : v2;
                       return Float.floatToIntBits(f1) == Float.floatToIntBits(f2);""";
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
                       final double k = (key == 0.0) ? 0.0 : key;
                       int h = Double.hashCode(k);
                       h ^= (h >>> 16);
                       return h;""";
            } else {
                return """
                       final float k = (key == 0.0f) ? 0.0f : key;
                       int h = Float.floatToIntBits(k);
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