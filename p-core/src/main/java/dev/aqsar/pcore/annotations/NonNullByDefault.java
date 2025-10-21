package dev.aqsar.pcore.annotations;

import jakarta.annotation.Nonnull;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks all methods, parameters, and fields in a package as Nonnull by default.
 */
@Documented
@Nonnull
@Retention(RUNTIME)
@Target({PACKAGE, TYPE, FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, CONSTRUCTOR, RECORD_COMPONENT})
public @interface NonNullByDefault {
}
