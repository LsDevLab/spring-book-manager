package com.example.demo.repository.specification.dtoToSpecBuilderUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)   // available at runtime for reflection
@Target(ElementType.FIELD)            // can only annotate fields
public @interface WithSpecification {
    String method();                              // spec method name
    Class<?> specClass() default void.class;      // if void.class → look in the DTO class itself
}
