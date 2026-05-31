package xyz.aimcup.auction.domain.exception;

public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(message);
    }

    @Override
    public String errorType() {
        return "FORBIDDEN";
    }
}
