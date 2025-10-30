package dev.aqsar.pcore.generator;

import org.stringtemplate.v4.ST;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class TreeSetGenerator {

    private static final List<PrimitiveConfig> TYPES =
        List.of(new PrimitiveConfig("int", "Integer", "Int", "", // No null check for int
                                    "return Integer.compare(k1, k2);"),

                new PrimitiveConfig("long", "Long", "Long", "", // No null check for long
                                    "return Long.compare(k1, k2);"),

                new PrimitiveConfig("float",
                                    "Float",
                                    "Float",
                                    "if (Float.isNaN(value)) throw new IllegalArgumentException(\"NaN values are not supported\");",
                                    "return Float.compare(k1, k2);"),

                new PrimitiveConfig("double",
                                    "Double",
                                    "Double",
                                    "if (Double.isNaN(value)) throw new IllegalArgumentException(\"NaN values are not supported\");",
                                    "return Double.compare(k1, k2);"));

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: TreeSetGenerator <output-dir>");
            System.exit(1);
        }

        final Path outputDir = Paths.get(args[0]);
        // Point to the correct template file
        final Path templatePath = Paths.get("src/main/templates/PrimitiveTreeSet.st");
        final String templateContent = Files.readString(templatePath);

        final Path packageDir = outputDir.resolve("dev/aqsar/pcore/collections");
        Files.createDirectories(packageDir);

        for (final PrimitiveConfig config : TYPES) {
            final ST st = new ST(templateContent, '#', '#');

            // Add template attributes based on the new PrimitiveConfig
            st.add("primitive", config.primitive);
            st.add("boxed", config.boxed);
            st.add("upper", config.upper);
            st.add("null_check", config.nullCheck);
            st.add("key_compare", config.keyCompare);

            // Change the output class name
            final String className = config.upper + "TreeSet.java";
            final Path classFile = packageDir.resolve(className);
            Files.writeString(classFile, st.render());
            System.out.printf("✅ Generated %s%n", classFile);
        }
    }

    /**
     * Configuration record for PrimitiveTreeSet generation.
     * (Simpler than HashSet's, as it only needs comparison logic)
     */
    private record PrimitiveConfig(String primitive, String boxed, String upper, String nullCheck, String keyCompare) {}
}
