package xyz.aimcup.auction.adapters.in.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import xyz.aimcup.auction.domain.exception.DomainException;

import java.util.Map;

/**
 * Maps domain exceptions to clean GraphQL errors (stable classification + human message) instead of
 * leaking stack traces to the client.
 */
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof DomainException domain) {
            return GraphqlErrorBuilder.newError(env)
                    .message(domain.getMessage())
                    .errorType(toErrorType(domain.errorType()))
                    .extensions(Map.of("classification", domain.errorType()))
                    .build();
        }
        return null;
    }

    private ErrorType toErrorType(String type) {
        return switch (type) {
            case "NOT_FOUND" -> ErrorType.NOT_FOUND;
            case "FORBIDDEN" -> ErrorType.FORBIDDEN;
            default -> ErrorType.BAD_REQUEST;
        };
    }
}
