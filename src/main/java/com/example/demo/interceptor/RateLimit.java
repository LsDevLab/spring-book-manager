package com.example.demo.interceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Custom annotation to override rate limit on specific controller methods.
// Without this annotation, the interceptor uses its default (100 req / 60s).
//
// @Target(METHOD) — can only be placed on methods, not classes or fields.
// @Retention(RUNTIME) — kept in the bytecode so we can read it at runtime via reflection.
//   Compare: RetentionPolicy.SOURCE (stripped at compile, like @Override)
//            RetentionPolicy.CLASS (in bytecode but not available via reflection)
//            RetentionPolicy.RUNTIME (available via reflection — what we need)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    // Annotation attributes look like methods but act like fields.
    // `default` provides a fallback — if you write just @RateLimit, you get 100 req / 60s.
    // If you write @RateLimit(maxRequests = 10, windowSeconds = 30), you override both.
    int maxRequests() default 100;

    int windowSeconds() default 60;
}
