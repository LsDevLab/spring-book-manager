package com.example.demo.exception;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;

// DataFetcherExceptionResolverAdapter — Spring GraphQL's equivalent of @ControllerAdvice.
//
// When a @QueryMapping or @MutationMapping method throws an exception, GraphQL catches it
// and passes it to registered resolvers. If a resolver returns a GraphQLError, that error
// is sent to the client in the "errors" array. If no resolver handles it, the client gets
// a generic "INTERNAL_ERROR".
//
// Key difference from REST error handling:
// - REST: @ControllerAdvice + @ExceptionHandler → ResponseEntity with HTTP status codes
// - GraphQL: DataFetcherExceptionResolverAdapter → GraphQLError with classification
// - GraphQL responses are ALWAYS HTTP 200 — errors live inside the JSON body
@Component
public class GraphqlExceptionHandler extends DataFetcherExceptionResolverAdapter {

    // Custom error types — clients use these to distinguish error categories.
    // Similar to HTTP status codes but for GraphQL.
    enum ErrorType implements ErrorClassification {
        NOT_FOUND,
        BAD_REQUEST,
        FORBIDDEN,
        CONFLICT
    }

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        ErrorType errorType;
        String message = ex.getMessage();

        if (ex instanceof BookNotFoundException
                || ex instanceof UserNotFoundException
                || ex instanceof UserBookNotFoundException) {
            errorType = ErrorType.NOT_FOUND;
        } else if (ex instanceof BookAlreadyInReadingList
                || ex instanceof UserAlreadyExistsException
                || ex instanceof AuthMethodAlreadyRegisteredException) {
            errorType = ErrorType.CONFLICT;
        } else if (ex instanceof AccessDeniedException) {
            // Thrown by @PreAuthorize when the user lacks the required role.
            // In REST this becomes HTTP 403 — in GraphQL it's a FORBIDDEN classification.
            errorType = ErrorType.FORBIDDEN;
            message = "Access denied";
        } else if (ex instanceof BindException bindEx) {
            // BindException happens when GraphQL can't convert an argument
            // (e.g. passing "not-a-uuid" for a UUID parameter).
            // This fires BEFORE the controller method — it's an argument binding failure.
            errorType = ErrorType.BAD_REQUEST;
            message = bindEx.getFieldError() != null
                    ? "Invalid argument: " + bindEx.getFieldError().getDefaultMessage()
                    : "Invalid argument";
        } else if (ex instanceof IllegalArgumentException) {
            // Catches conversion failures that bypass BindException
            // (e.g. empty string "" to UUID).
            errorType = ErrorType.BAD_REQUEST;
        } else {
            // Return null — lets Spring's default handler produce the generic INTERNAL_ERROR.
            // This is intentional: unknown exceptions shouldn't leak details to the client.
            return null;
        }

        return GraphQLError.newError()
                .message(message)
                .errorType(errorType)
                .path(env.getExecutionStepInfo().getPath())
                .location(env.getField().getSourceLocation())
                .build();
    }
}