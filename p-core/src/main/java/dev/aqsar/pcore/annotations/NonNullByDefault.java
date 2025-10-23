package dev.aqsar.pcore.annotations;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Marks all method returns, parameters, fields, and locals in a package as Nonnull
 * unless explicitly overridden by @Nullable.
 */
@Documented
@Retention(CLASS)
@Target({PACKAGE, TYPE})
@TypeQualifierDefault({
        FIELD,               // Fields/Instance Variables
        METHOD,              // Method Return Values
        PARAMETER,           // Method/Constructor Parameters
        LOCAL_VARIABLE,      // Local variables inside methods (recommended)
        CONSTRUCTOR,         // Constructor Return Type (The object itself)
        TYPE_PARAMETER,      // Generic Type Parameters (e.g., List<String> -> List<@Nonnull String>)
        RECORD_COMPONENT     // Java 16+ Record Components
})
@Nonnull
public @interface NonNullByDefault {
}
