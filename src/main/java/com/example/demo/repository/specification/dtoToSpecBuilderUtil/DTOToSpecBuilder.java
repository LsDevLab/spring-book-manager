package com.example.demo.repository.specification.dtoToSpecBuilderUtil;

import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class that converts any DTO with {@link WithSpecification}-annotated fields
 * into a JPA Specification.
 *
 * <p>The DTO stays a plain POJO — no inheritance required. This avoids Spring MVC
 * model attribute binding issues that occurred when the DTO extended a generic base class.</p>
 *
 * <p>Reflection results are cached per class so the cost is only paid once.</p>
 */
public class DTOToSpecBuilder {

    private DTOToSpecBuilder() {}

    // ── Resolved mappings ────────────────────────────────────────
    // Stores resolved field+method pairs per DTO class, so reflection only runs once.
    private static final Map<Class<?>, List<ResolvedFilter>> RESOLVED = new ConcurrentHashMap<>();

    private record ResolvedFilter(Field field, Method specMethod) {}

    /**
     * Builds a Specification from a DTO's @WithSpecification-annotated fields, chained with AND.
     * Null fields are skipped — only non-null values become active filters.
     *
     * @param dto any object with @WithSpecification-annotated fields
     * @return composed Specification, or unrestricted if no filters are active
     */
    @SuppressWarnings("unchecked")
    public static <T> Specification<T> buildAndSpecification(Object dto) {
        List<ResolvedFilter> filters = resolve(dto);

        Specification<T> spec = Specification.unrestricted();
        for (ResolvedFilter filter : filters) {
            try {
                Object value = filter.field().get(dto);
                if (value != null) {
                    Specification<T> fieldSpec = (Specification<T>) filter.specMethod().invoke(null, value);
                    spec = spec.and(fieldSpec);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Failed to apply @WithSpecification for field '%s'".formatted(filter.field().getName()), e);
            }
        }
        return spec;
    }

    /**
     * Same as buildAndSpecification, but chains filters with OR.
     * Matches entities that satisfy ANY of the non-null filters.
     */
    @SuppressWarnings("unchecked")
    public static <T> Specification<T> buildOrSpecification(Object dto) {
        List<ResolvedFilter> filters = resolve(dto);

        Specification<T> spec = null;
        for (ResolvedFilter filter : filters) {
            try {
                Object value = filter.field().get(dto);
                if (value != null) {
                    Specification<T> fieldSpec = (Specification<T>) filter.specMethod().invoke(null, value);
                    spec = spec == null ? Specification.where(fieldSpec) : spec.or(fieldSpec);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Failed to apply @WithSpecification for field '%s'".formatted(filter.field().getName()), e);
            }
        }
        // If no filters provided, return unrestricted (match everything)
        return spec != null ? spec : Specification.unrestricted();
    }

    /**
     * Resolves @WithSpecification annotations for the DTO class (cached after first call).
     */
    private static List<ResolvedFilter> resolve(Object dto) {
        return RESOLVED.computeIfAbsent(dto.getClass(), DTOToSpecBuilder::resolveFilters);
    }

    /**
     * Scans a class for @WithSpecification annotations and resolves each to a Field + Method pair.
     */
    private static List<ResolvedFilter> resolveFilters(Class<?> dtoClass) {
        List<ResolvedFilter> resolved = new ArrayList<>();

        for (Field field : dtoClass.getDeclaredFields()) {
            WithSpecification annotation = field.getAnnotation(WithSpecification.class);
            if (annotation == null) continue;

            field.setAccessible(true);

            // void.class (default) means "look in the DTO class itself"
            Class<?> targetClass = annotation.specClass() == void.class
                    ? dtoClass
                    : annotation.specClass();

            try {
                Method specMethod = targetClass.getMethod(annotation.method(), field.getType());
                resolved.add(new ResolvedFilter(field, specMethod));
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(
                        "@WithSpecification on field '%s': method '%s(%s)' not found in %s".formatted(
                                field.getName(),
                                annotation.method(),
                                field.getType().getSimpleName(),
                                targetClass.getSimpleName()),
                        e);
            }
        }

        return resolved;
    }
}
