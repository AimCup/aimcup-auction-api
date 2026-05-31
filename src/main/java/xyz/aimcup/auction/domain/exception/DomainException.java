package xyz.aimcup.auction.domain.exception;

/**
 * Base type for expected, client-facing domain errors. Carries a stable {@link #errorType} that the
 * GraphQL exception resolver maps to a {@code graphql.ErrorType}.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    public abstract String errorType();
}
