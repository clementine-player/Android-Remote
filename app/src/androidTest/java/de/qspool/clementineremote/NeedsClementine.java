package de.qspool.clementineremote;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A device test that needs a real Clementine (clementine-it), whose host it's given as the
 * {@code clementineHost} instrumentation argument. .github/workflows/store-screenshots.yml runs
 * every test so marked; without the argument, they're skipped.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NeedsClementine {
}
