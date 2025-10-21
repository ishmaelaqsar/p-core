package dev.aqsar.pcore.generator;

import org.stringtemplate.v4.ST;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class PrimitiveListGenerator {

    private static final List<PrimitiveType> TYPES =
            List.of(new PrimitiveType("int", "Integer", "Int", "Integer.MIN_VALUE"),
                    new PrimitiveType("long", "Long", "Long", "Long.MIN_VALUE"),
                    new PrimitiveType("float", "Float", "Float", "Float.NaN"),
                    new PrimitiveType("double", "Double", "Double", "Double.NaN"));

    public static void main(final String[] args)
    throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: PrimitiveListGenerator <output-dir> <template-file>");
            System.exit(1);
        }

        final Path outputDir = Paths.get(args[0]);
        final Path templatePath = Paths.get("src/main/templates").resolve(args[1]);
        final String templateContent = Files.readString(templatePath);

        final Path packageDir = outputDir.resolve("dev/aqsar/pcore/collections");
        Files.createDirectories(packageDir);

        for (final PrimitiveType type : TYPES) {
            final ST st = new ST(templateContent, '#', '#');
            st.add("primitive", type.primitive);
            st.add("boxed", type.boxed);
            st.add("upper", type.upper);
            st.add("nullValue", type.nullValue);

            final String className = type.upper + "List.java";
            final Path classFile = packageDir.resolve(className);
            Files.writeString(classFile, st.render());

            System.out.printf("✅ Generated %s%n", classFile);
        }
    }

    private record PrimitiveType(String primitive, String boxed, String upper, String nullValue) {}
}
