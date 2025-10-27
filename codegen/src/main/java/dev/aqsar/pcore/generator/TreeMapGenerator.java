package dev.aqsar.pcore.generator;

import org.stringtemplate.v4.ST;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class TreeMapGenerator {

    private static final List<PrimitiveType> TYPES =
            List.of(new IntegerType("int", "Integer", "Int", "Integer.MIN_VALUE"),
                    new IntegerType("long", "Long", "Long", "Long.MIN_VALUE"),
                    new FloatingType("float", "Float", "Float", "Float.MIN_VALUE"),
                    new FloatingType("double", "Double", "Double", "Double.MIN_VALUE"));

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: TreeMapGenerator <output-dir>");
            System.exit(1);
        }

        final Path outputDir = Paths.get(args[0]);
        final Path templateDir = Paths.get("src/main/templates");
        final Path packageDir = outputDir.resolve("dev/aqsar/pcore/collections");
        Files.createDirectories(packageDir);

        // Generate Primitive2Primitive tree maps
        final String primitive2PrimitiveTemplate =
                Files.readString(templateDir.resolve("Primitive2PrimitiveTreeMap.st"));
        for (final PrimitiveType keyType : TYPES) {
            for (final PrimitiveType valueType : TYPES) {
                generatePrimitive2Primitive(primitive2PrimitiveTemplate, packageDir, keyType, valueType);
            }
        }

        // Generate Primitive2Object tree maps
        final String primitive2ObjectTemplate = Files.readString(templateDir.resolve("Primitive2ObjectTreeMap.st"));
        for (final PrimitiveType keyType : TYPES) {
            generatePrimitive2Object(primitive2ObjectTemplate, packageDir, keyType);
        }

        // Generate Object2Primitive tree maps
        final String object2PrimitiveTemplate = Files.readString(templateDir.resolve("Object2PrimitiveTreeMap.st"));
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

        st.add("key_compare", keyType.getCompareExpression());
        st.add("value_equals", valueType.getEqualsExpression());
        st.add("null_key_check", keyType.getNullKeyCheck());

        final String className = keyType.upper + "2" + valueType.upper + "TreeMap.java";
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

        st.add("key_compare", keyType.getCompareExpression());
        st.add("null_key_check", keyType.getNullKeyCheck());

        final String className = keyType.upper + "2ObjectTreeMap.java";
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

        final String className = "Object2" + valueType.upper + "TreeMap.java";
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

        abstract String getCompareExpression();

        abstract String getEqualsExpression();

        abstract String getNullKeyCheck();
    }

    private static class IntegerType extends PrimitiveType {

        IntegerType(String primitive, String boxed, String upper, String nullValue) {
            super(primitive, boxed, upper, nullValue);
        }

        @Override
        String getCompareExpression() {
            if (primitive.equals("int")) {
                return "return Integer.compare(k1, k2);";
            } else if (primitive.equals("long")) {
                return "return Long.compare(k1, k2);";
            }
            return "return Integer.compare(k1, k2);";
        }

        @Override
        String getEqualsExpression() {
            return "return v1 == v2;";
        }

        @Override
        String getNullKeyCheck() {
            return "// No null check needed for integer types";
        }
    }

    private static class FloatingType extends PrimitiveType {

        FloatingType(String primitive, String boxed, String upper, String nullValue) {
            super(primitive, boxed, upper, nullValue);
        }

        @Override
        String getCompareExpression() {
            if (primitive.equals("double")) {
                return """
                       final double d1 = (k1 == 0.0) ? 0.0 : k1;
                       final double d2 = (k2 == 0.0) ? 0.0 : k2;
                       return Double.compare(d1, d2);""";
            } else {
                return """
                       final float f1 = (k1 == 0.0f) ? 0.0f : k1;
                       final float f2 = (k2 == 0.0f) ? 0.0f : k2;
                       return Float.compare(f1, f2);""";
            }
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
        String getNullKeyCheck() {
            return "// No null check needed for floating types";
        }
    }
}